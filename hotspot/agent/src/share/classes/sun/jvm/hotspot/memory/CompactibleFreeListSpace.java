/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.memory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.Debugger;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.utilities.Assert;

public class CompactibleFreeListSpace extends CompactibleSpace {
   private static AddressField collectorField;
   private static AddressField indexedFreeListField;
   private static AddressField dictionaryField;
   private static long         smallLinearAllocBlockFieldOffset;

   private int    heapWordSize;     // 4 for 32bit, 8 for 64 bits
   private int    IndexSetStart;    // for small indexed list
   private int    IndexSetSize;
   private int    IndexSetStride;

   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize(VM.getVM().getTypeDataBase());
         }
      });
   }

   private static synchronized void initialize(TypeDataBase db) {
      long sizeofFreeChunk = db.lookupType("FreeChunk").getSize();
      VM vm = VM.getVM();
      MinChunkSizeInBytes = numQuanta(sizeofFreeChunk, vm.getMinObjAlignmentInBytes()) *
                     vm.getMinObjAlignmentInBytes();

     Type type = db.lookupType("CompactibleFreeListSpace");
     collectorField = type.getAddressField("_collector");
     collectorField       = type.getAddressField("_collector");
     dictionaryField      = type.getAddressField("_dictionary");
     indexedFreeListField = type.getAddressField("_indexedFreeList[0]");
     smallLinearAllocBlockFieldOffset = type.getField("_smallLinearAllocBlock").getOffset();
   }

   public CompactibleFreeListSpace(Address addr) {
      super(addr);
      VM vm = VM.getVM();
      heapWordSize   = vm.getHeapWordSize();
      IndexSetStart  = vm.getMinObjAlignmentInBytes() / heapWordSize;
      IndexSetStride = IndexSetStart;
      IndexSetSize   = 257;
   }

   // Accessing block offset table
   public CMSCollector collector() {
    return (CMSCollector) VMObjectFactory.newObject(
                                 CMSCollector.class,
                                 collectorField.getValue(addr));
   }

   public long free0() {
     return capacity() - used0();
   }

   public long used() {
     return capacity() - free();
   }

   public long used0() {
      List regions = getLiveRegions();
      long usedSize = 0L;
      for (Iterator itr = regions.iterator(); itr.hasNext();) {
         MemRegion mr = (MemRegion) itr.next();
         usedSize += mr.byteSize();
      }
      return usedSize;
   }

   public long free() {
      // small chunks
      long size = 0;
      Address cur = addr.addOffsetTo( indexedFreeListField.getOffset() );
      cur = cur.addOffsetTo(IndexSetStart*AdaptiveFreeList.sizeOf());
      for (int i=IndexSetStart; i<IndexSetSize; i += IndexSetStride) {
         AdaptiveFreeList freeList = (AdaptiveFreeList) VMObjectFactory.newObject(AdaptiveFreeList.class, cur);
         size += i*freeList.count();
         cur= cur.addOffsetTo(IndexSetStride*AdaptiveFreeList.sizeOf());
      }

      // large block
      AFLBinaryTreeDictionary aflbd = (AFLBinaryTreeDictionary) VMObjectFactory.newObject(AFLBinaryTreeDictionary.class,
                                                                                   dictionaryField.getValue(addr));
      size += aflbd.size();


      // linear block in TLAB
      LinearAllocBlock lab = (LinearAllocBlock) VMObjectFactory.newObject(LinearAllocBlock.class,
                                                                          addr.addOffsetTo(smallLinearAllocBlockFieldOffset));
      size += lab.word_size();

      return size*heapWordSize;
  }

   public void printOn(PrintStream tty) {
      tty.print("free-list-space");
      tty.print("[ " + bottom() + " , " + end() + " ) ");
      long cap = capacity();
      long used_size = used();
      long free_size = free();
      int  used_perc = (int)((double)used_size/cap*100);
      tty.print("space capacity = " + cap + " used(" + used_perc + "%)= " + used_size + " ");
      tty.print("free= " + free_size );
      tty.print("\n");

   }

   public Address skipBlockSizeUsingPrintezisBits(Address pos) {
       CMSCollector collector = collector();
       long size = 0;
       Address addr = null;

       if (collector != null) {
         size = collector.blockSizeUsingPrintezisBits(pos);
         if (size >= 3) {
           addr = pos.addOffsetTo(adjustObjectSizeInBytes(size));
         }
       }
       return addr;
   }

   public List/*<MemRegion>*/ getLiveRegions() {
      List res = new ArrayList(); // List<MemRegion>
      VM vm = VM.getVM();
      Debugger dbg = vm.getDebugger();
      ObjectHeap heap = vm.getObjectHeap();
      Address cur = bottom();
      Address regionStart = cur;
      Address limit = end();
      final long addressSize = vm.getAddressSize();

      for (; cur.lessThan(limit);) {
         Address k = cur.getAddressAt(addressSize);
         if (FreeChunk.indicatesFreeChunk(cur)) {
            if (! cur.equals(regionStart)) {
               res.add(new MemRegion(regionStart, cur));
            }
            FreeChunk fc = (FreeChunk) VMObjectFactory.newObject(FreeChunk.class, cur);
            long chunkSize = fc.size();
            if (Assert.ASSERTS_ENABLED) {
               Assert.that(chunkSize > 0, "invalid FreeChunk size");
            }
            // note that fc.size() gives chunk size in heap words
            cur = cur.addOffsetTo(chunkSize * addressSize);
            regionStart = cur;
         } else if (k != null) {
            Oop obj = heap.newOop(cur.addOffsetToAsOopHandle(0));
            long objectSize = obj.getObjectSize();
            cur = cur.addOffsetTo(adjustObjectSizeInBytes(objectSize));
         } else {
            // FIXME: need to do a better job here.
            // can I use bitMap here?
            //Find the object size using Printezis bits and skip over
            long size = collector().blockSizeUsingPrintezisBits(cur);
            if (size == -1) {
              break;
            }
            cur = cur.addOffsetTo(adjustObjectSizeInBytes(size));
         }
      }
      return res;
   }

   //-- Internals only below this point

   // Unlike corresponding VM code, we operate on byte size rather than
   // HeapWord size for convenience.

   private static long numQuanta(long x, long y) {
      return  ((x+y-1)/y);
   }

   public static long adjustObjectSizeInBytes(long sizeInBytes) {
      return Oop.alignObjectSize(Math.max(sizeInBytes, MinChunkSizeInBytes));
   }

   // FIXME: should I read this directly from VM?
   private static long MinChunkSizeInBytes;
}
