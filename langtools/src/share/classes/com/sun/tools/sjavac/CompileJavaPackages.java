/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.sjavac;

import java.net.URI;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.Map;

import com.sun.tools.sjavac.server.JavacServer;
import com.sun.tools.sjavac.server.SysInfo;
import java.io.PrintStream;

/**
 * This transform compiles a set of packages containing Java sources.
 * The compile request is divided into separate sets of source files.
 * For each set a separate request thread is dispatched to a javac server
 * and the meta data is accumulated. The number of sets correspond more or
 * less to the number of cores. Less so now, than it will in the future.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class CompileJavaPackages implements Transformer {

    // The current limited sharing of data between concurrent JavaCompilers
    // in the server will not give speedups above 3 cores. Thus this limit.
    // We hope to improve this in the future.
    final static int limitOnConcurrency = 3;

    String serverSettings;
    public void setExtra(String e) {
        serverSettings = e;
    }

    String[] args;
    public void setExtra(String[] a) {
        args = a;
    }

    public boolean transform(Map<String,Set<URI>> pkgSrcs,
                             Set<URI>             visibleSources,
                             Map<URI,Set<String>> visibleClasses,
                             Map<String,Set<String>> oldPackageDependents,
                             URI destRoot,
                             final Map<String,Set<URI>>    packageArtifacts,
                             final Map<String,Set<String>> packageDependencies,
                             final Map<String,String>      packagePubapis,
                             int debugLevel,
                             boolean incremental,
                             int numCores,
                             PrintStream out,
                             PrintStream err)
    {
        boolean rc = true;
        boolean concurrentCompiles = true;

        // Fetch the id.
        String id = Util.extractStringOption("id", serverSettings);
        if (id == null || id.equals("")) {
            // No explicit id set. Create a random id so that the requests can be
            // grouped properly in the server.
            id = "id"+(((new Random()).nextLong())&Long.MAX_VALUE);
        }
        // Only keep portfile and sjavac settings..
        String psServerSettings = Util.cleanSubOptions("--server:", Util.set("portfile","sjavac","background","keepalive"), serverSettings);

        // Get maximum heap size from the server!
        SysInfo sysinfo = JavacServer.connectGetSysInfo(psServerSettings, out, err);
        if (sysinfo.numCores == -1) {
            Log.error("Could not query server for sysinfo!");
            return false;
        }
        int numMBytes = (int)(sysinfo.maxMemory / ((long)(1024*1024)));
        Log.debug("Server reports "+numMBytes+"MiB of memory and "+sysinfo.numCores+" cores");

        if (numCores <= 0) {
            // Set the requested number of cores to the number of cores on the server.
            numCores = sysinfo.numCores;
            Log.debug("Number of jobs not explicitly set, defaulting to "+sysinfo.numCores);
        } else if (sysinfo.numCores < numCores) {
            // Set the requested number of cores to the number of cores on the server.
            Log.debug("Limiting jobs from explicitly set "+numCores+" to cores available on server: "+sysinfo.numCores);
            numCores = sysinfo.numCores;
        } else {
            Log.debug("Number of jobs explicitly set to "+numCores);
        }
        // More than three concurrent cores does not currently give a speedup, at least for compiling the jdk
        // in the OpenJDK. This will change in the future.
        int numCompiles = numCores;
        if (numCores > limitOnConcurrency) numCompiles = limitOnConcurrency;
        // Split the work up in chunks to compiled.

        int numSources = 0;
        for (String s : pkgSrcs.keySet()) {
            Set<URI> ss = pkgSrcs.get(s);
            numSources += ss.size();
        }

        int sourcesPerCompile = numSources / numCompiles;

        // For 64 bit Java, it seems we can compile the OpenJDK 8800 files with a 1500M of heap
        // in a single chunk, with reasonable performance.
        // For 32 bit java, it seems we need 1G of heap.
        // Number experimentally determined when compiling the OpenJDK.
        // Includes space for reasonably efficient garbage collection etc,
        // Calculating backwards gives us a requirement of
        // 1500M/8800 = 175 KiB for 64 bit platforms
        // and 1G/8800 = 119 KiB for 32 bit platform
        // for each compile.....
        int kbPerFile = 175;
        String osarch = System.getProperty("os.arch");
        String dataModel = System.getProperty("sun.arch.data.model");
        if ("32".equals(dataModel)) {
            // For 32 bit platforms, assume it is slightly smaller
            // because of smaller object headers and pointers.
            kbPerFile = 119;
        }
        int numRequiredMBytes = (kbPerFile*numSources)/1024;
        Log.debug("For os.arch "+osarch+" the empirically determined heap required per file is "+kbPerFile+"KiB");
        Log.debug("Server has "+numMBytes+"MiB of heap.");
        Log.debug("Heuristics say that we need "+numRequiredMBytes+"MiB of heap for all source files.");
        // Perform heuristics to see how many cores we can use,
        // or if we have to the work serially in smaller chunks.
        if (numMBytes < numRequiredMBytes) {
            // Ouch, cannot fit even a single compile into the heap.
            // Split it up into several serial chunks.
            concurrentCompiles = false;
            // Limit the number of sources for each compile to 500.
            if (numSources < 500) {
                numCompiles = 1;
                sourcesPerCompile = numSources;
                Log.debug("Compiling as a single source code chunk to stay within heap size limitations!");
            } else if (sourcesPerCompile > 500) {
                // This number is very low, and tuned to dealing with the OpenJDK
                // where the source is >very< circular! In normal application,
                // with less circularity the number could perhaps be increased.
                numCompiles = numSources / 500;
                sourcesPerCompile = numSources/numCompiles;
                Log.debug("Compiling source as "+numCompiles+" code chunks serially to stay within heap size limitations!");
            }
        } else {
            if (numCompiles > 1) {
                // Ok, we can fit at least one full compilation on the heap.
                float usagePerCompile = (float)numRequiredMBytes / ((float)numCompiles * (float)0.7);
                int usage = (int)(usagePerCompile * (float)numCompiles);
                Log.debug("Heuristics say that for "+numCompiles+" concurrent compiles we need "+usage+"MiB");
                if (usage > numMBytes) {
                    // Ouch it does not fit. Reduce to a single chunk.
                    numCompiles = 1;
                    sourcesPerCompile = numSources;
                    // What if the relationship betweem number of compile_chunks and num_required_mbytes
                    // is not linear? Then perhaps 2 chunks would fit where 3 does not. Well, this is
                    // something to experiment upon in the future.
                    Log.debug("Limiting compile to a single thread to stay within heap size limitations!");
                }
            }
        }

        Log.debug("Compiling sources in "+numCompiles+" chunk(s)");

        // Create the chunks to be compiled.
        final CompileChunk[] compileChunks = createCompileChunks(pkgSrcs, oldPackageDependents,
                numCompiles, sourcesPerCompile);

        if (Log.isDebugging()) {
            int cn = 1;
            for (CompileChunk cc : compileChunks) {
                Log.debug("Chunk "+cn+" for "+id+" ---------------");
                cn++;
                for (URI u : cc.srcs) {
                    Log.debug(""+u);
                }
            }
        }

        // The return values for each chunked compile.
        final int[] rn = new int[numCompiles];
        // The requets, might or might not run as a background thread.
        final Thread[] requests  = new Thread[numCompiles];

        final Set<URI>             fvisible_sources = visibleSources;
        final Map<URI,Set<String>> fvisible_classes = visibleClasses;

        long start = System.currentTimeMillis();

        for (int i=0; i<numCompiles; ++i) {
            final int ii = i;
            final CompileChunk cc = compileChunks[i];

            // Pass the num_cores and the id (appended with the chunk number) to the server.
            final String cleanedServerSettings = psServerSettings+",poolsize="+numCores+",id="+id+"-"+ii;
            final PrintStream fout = out;
            final PrintStream ferr = err;

            requests[ii] = new Thread() {
                @Override
                public void run() {
                                        rn[ii] = JavacServer.useServer(cleanedServerSettings,
                                                           Main.removeWrapperArgs(args),
                                                               cc.srcs,
                                                           fvisible_sources,
                                                           fvisible_classes,
                                                           packageArtifacts,
                                                           packageDependencies,
                                                           packagePubapis,
                                                           null,
                                                           fout, ferr);
                }
            };

            if (cc.srcs.size() > 0) {
                String numdeps = "";
                if (cc.numDependents > 0) numdeps = "(with "+cc.numDependents+" dependents) ";
                if (!incremental || cc.numPackages > 16) {
                    String info = "("+cc.pkgFromTos+")";
                    if (info.equals("( to )")) {
                        info = "";
                    }
                    Log.info("Compiling "+cc.srcs.size()+" files "+numdeps+"in "+cc.numPackages+" packages "+info);
                } else {
                    Log.info("Compiling "+cc.pkgNames+numdeps);
                }
                if (concurrentCompiles) {
                    requests[ii].start();
                }
                else {
                    requests[ii].run();
                    // If there was an error, then stop early when running single threaded.
                    if (rn[i] != 0) {
                        return false;
                    }
                }
            }
        }
        if (concurrentCompiles) {
            // If there are background threads for the concurrent compiles, then join them.
            for (int i=0; i<numCompiles; ++i) {
                try { requests[i].join(); } catch (InterruptedException e) { }
            }
        }

        // Check the return values.
        for (int i=0; i<numCompiles; ++i) {
            if (compileChunks[i].srcs.size() > 0) {
                if (rn[i] != 0) {
                    rc = false;
                }
            }
        }
        long duration = System.currentTimeMillis() - start;
        long minutes = duration/60000;
        long seconds = (duration-minutes*60000)/1000;
        Log.debug("Compilation of "+numSources+" source files took "+minutes+"m "+seconds+"s");

        return rc;
    }


    /**
     * Split up the sources into compile chunks. If old package dependents information
     * is available, sort the order of the chunks into the most dependent first!
     * (Typically that chunk contains the java.lang package.) In the future
     * we could perhaps improve the heuristics to put the sources into even more sensible chunks.
     * Now the package are simple sorted in alphabetical order and chunked, then the chunks
     * are sorted on how dependent they are.
     *
     * @param pkgSrcs The sources to compile.
     * @param oldPackageDependents Old package dependents, if non-empty, used to sort the chunks.
     * @param numCompiles The number of chunks.
     * @param sourcesPerCompile The number of sources per chunk.
     * @return
     */
    CompileChunk[] createCompileChunks(Map<String,Set<URI>> pkgSrcs,
                                 Map<String,Set<String>> oldPackageDependents,
                                 int numCompiles,
                                 int sourcesPerCompile) {

        CompileChunk[] compileChunks = new CompileChunk[numCompiles];
        for (int i=0; i<compileChunks.length; ++i) {
            compileChunks[i] = new CompileChunk();
        }

        // Now go through the packages and spread out the source on the different chunks.
        int ci = 0;
        // Sort the packages
        String[] packageNames = pkgSrcs.keySet().toArray(new String[0]);
        Arrays.sort(packageNames);
        String from = null;
        for (String pkgName : packageNames) {
            CompileChunk cc = compileChunks[ci];
            Set<URI> s = pkgSrcs.get(pkgName);
            if (cc.srcs.size()+s.size() > sourcesPerCompile && ci < numCompiles-1) {
                from = null;
                ci++;
                cc = compileChunks[ci];
            }
            cc.numPackages++;
            cc.srcs.addAll(s);

            // Calculate nice package names to use as information when compiling.
            String justPkgName = Util.justPackageName(pkgName);
            // Fetch how many packages depend on this package from the old build state.
            Set<String> ss = oldPackageDependents.get(pkgName);
            if (ss != null) {
                // Accumulate this information onto this chunk.
                cc.numDependents += ss.size();
            }
            if (from == null || from.trim().equals("")) from = justPkgName;
            cc.pkgNames.append(justPkgName+"("+s.size()+") ");
            cc.pkgFromTos = from+" to "+justPkgName;
        }
        // If we are compiling serially, sort the chunks, so that the chunk (with the most dependents) (usually the chunk
        // containing java.lang.Object, is to be compiled first!
        // For concurrent compilation, this does not matter.
        Arrays.sort(compileChunks);
        return compileChunks;
    }
}
