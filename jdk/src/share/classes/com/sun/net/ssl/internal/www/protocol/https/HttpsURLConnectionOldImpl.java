/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * NOTE: This class lives in the package sun.net.www.protocol.https.
 * There is a copy in com.sun.net.ssl.internal.www.protocol.https for JSSE
 * 1.0.2 compatibility. It is 100% identical except the package and extends
 * lines. Any changes should be made to be class in sun.net.* and then copied
 * to com.sun.net.*.
 */

// For both copies of the file, uncomment one line and comment the other
// package sun.net.www.protocol.https;
package com.sun.net.ssl.internal.www.protocol.https;

import java.net.URL;
import java.net.Proxy;
import java.net.ProtocolException;
import java.net.MalformedURLException;
import java.io.*;
import javax.net.ssl.*;
import java.security.Permission;
import java.util.Map;
import java.util.List;
import sun.net.www.http.HttpClient;

/**
 * A class to represent an HTTP connection to a remote object.
 *
 * Ideally, this class should subclass and inherit the http handler
 * implementation, but it can't do so because that class have the
 * wrong Java Type.  Thus it uses the delegate (aka, the
 * Adapter/Wrapper design pattern) to reuse code from the http
 * handler.
 *
 * Since it would use a delegate to access
 * sun.net.www.protocol.http.HttpURLConnection functionalities, it
 * needs to implement all public methods in it's super class and all
 * the way to Object.
 *
 */

// For both copies of the file, uncomment one line and comment the other
// public class HttpsURLConnectionImpl
//      extends javax.net.ssl.HttpsURLConnection {
public class HttpsURLConnectionOldImpl
        extends com.sun.net.ssl.HttpsURLConnection {

    private DelegateHttpsURLConnection delegate;

// For both copies of the file, uncomment one line and comment the other
//    HttpsURLConnectionImpl(URL u, Handler handler) throws IOException {
    HttpsURLConnectionOldImpl(URL u, Handler handler) throws IOException {
        this(u, null, handler);
    }

    static URL checkURL(URL u) throws IOException {
        if (u != null) {
            if (u.toExternalForm().indexOf('\n') > -1) {
                throw new MalformedURLException("Illegal character in URL");
            }
        }
        return u;
    }
// For both copies of the file, uncomment one line and comment the other
//    HttpsURLConnectionImpl(URL u, Handler handler) throws IOException {
    HttpsURLConnectionOldImpl(URL u, Proxy p, Handler handler) throws IOException {
        super(checkURL(u));
        delegate = new DelegateHttpsURLConnection(url, p, handler, this);
    }

    /**
     * Create a new HttpClient object, bypassing the cache of
     * HTTP client objects/connections.
     *
     * @param url       the URL being accessed
     */
    protected void setNewClient(URL url) throws IOException {
        delegate.setNewClient(url, false);
    }

    /**
     * Obtain a HttpClient object. Use the cached copy if specified.
     *
     * @param url       the URL being accessed
     * @param useCache  whether the cached connection should be used
     *                  if present
     */
    protected void setNewClient(URL url, boolean useCache)
            throws IOException {
        delegate.setNewClient(url, useCache);
    }

    /**
     * Create a new HttpClient object, set up so that it uses
     * per-instance proxying to the given HTTP proxy.  This
     * bypasses the cache of HTTP client objects/connections.
     *
     * @param url       the URL being accessed
     * @param proxyHost the proxy host to use
     * @param proxyPort the proxy port to use
     */
    protected void setProxiedClient(URL url, String proxyHost, int proxyPort)
            throws IOException {
        delegate.setProxiedClient(url, proxyHost, proxyPort);
    }

    /**
     * Obtain a HttpClient object, set up so that it uses per-instance
     * proxying to the given HTTP proxy. Use the cached copy of HTTP
     * client objects/connections if specified.
     *
     * @param url       the URL being accessed
     * @param proxyHost the proxy host to use
     * @param proxyPort the proxy port to use
     * @param useCache  whether the cached connection should be used
     *                  if present
     */
    protected void setProxiedClient(URL url, String proxyHost, int proxyPort,
            boolean useCache) throws IOException {
        delegate.setProxiedClient(url, proxyHost, proxyPort, useCache);
    }

    /**
     * Implements the HTTP protocol handler's "connect" method,
     * establishing an SSL connection to the server as necessary.
     */
    public void connect() throws IOException {
        delegate.connect();
    }

    /**
     * Used by subclass to access "connected" variable.  Since we are
     * delegating the actual implementation to "delegate", we need to
     * delegate the access of "connected" as well.
     */
    protected boolean isConnected() {
        return delegate.isConnected();
    }

    /**
     * Used by subclass to access "connected" variable.  Since we are
     * delegating the actual implementation to "delegate", we need to
     * delegate the access of "connected" as well.
     */
    protected void setConnected(boolean conn) {
        delegate.setConnected(conn);
    }

    /**
     * Returns the cipher suite in use on this connection.
     */
    public String getCipherSuite() {
        return delegate.getCipherSuite();
    }

    /**
     * Returns the certificate chain the client sent to the
     * server, or null if the client did not authenticate.
     */
    public java.security.cert.Certificate []
        getLocalCertificates() {
        return delegate.getLocalCertificates();
    }

    /**
     * Returns the server's certificate chain, or throws
     * SSLPeerUnverified Exception if
     * the server did not authenticate.
     */
    public java.security.cert.Certificate []
        getServerCertificates() throws SSLPeerUnverifiedException {
        return delegate.getServerCertificates();
    }

    /**
     * Returns the server's X.509 certificate chain, or null if
     * the server did not authenticate.
     *
     * NOTE: This method is not necessary for the version of this class
     * implementing javax.net.ssl.HttpsURLConnection, but provided for
     * compatibility with the com.sun.net.ssl.HttpsURLConnection version.
     */
    public javax.security.cert.X509Certificate[] getServerCertificateChain() {
        try {
            return delegate.getServerCertificateChain();
        } catch (SSLPeerUnverifiedException e) {
            // this method does not throw an exception as declared in
            // com.sun.net.ssl.HttpsURLConnection.
            // Return null for compatibility.
            return null;
        }
    }

    /*
     * Allowable input/output sequences:
     * [interpreted as POST/PUT]
     * - get output, [write output,] get input, [read input]
     * - get output, [write output]
     * [interpreted as GET]
     * - get input, [read input]
     * Disallowed:
     * - get input, [read input,] get output, [write output]
     */

    public synchronized OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    public synchronized InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    public InputStream getErrorStream() {
        return delegate.getErrorStream();
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        delegate.disconnect();
    }

    public boolean usingProxy() {
        return delegate.usingProxy();
    }

    /**
     * Returns an unmodifiable Map of the header fields.
     * The Map keys are Strings that represent the
     * response-header field names. Each Map value is an
     * unmodifiable List of Strings that represents
     * the corresponding field values.
     *
     * @return a Map of header fields
     * @since 1.4
     */
    public Map<String,List<String>> getHeaderFields() {
        return delegate.getHeaderFields();
    }

    /**
     * Gets a header field by name. Returns null if not known.
     * @param name the name of the header field
     */
    public String getHeaderField(String name) {
        return delegate.getHeaderField(name);
    }

    /**
     * Gets a header field by index. Returns null if not known.
     * @param n the index of the header field
     */
    public String getHeaderField(int n) {
        return delegate.getHeaderField(n);
    }

    /**
     * Gets a header field by index. Returns null if not known.
     * @param n the index of the header field
     */
    public String getHeaderFieldKey(int n) {
        return delegate.getHeaderFieldKey(n);
    }

    /**
     * Sets request property. If a property with the key already
     * exists, overwrite its value with the new value.
     * @param value the value to be set
     */
    public void setRequestProperty(String key, String value) {
        delegate.setRequestProperty(key, value);
    }

    /**
     * Adds a general request property specified by a
     * key-value pair.  This method will not overwrite
     * existing values associated with the same key.
     *
     * @param   key     the keyword by which the request is known
     *                  (e.g., "<code>accept</code>").
     * @param   value  the value associated with it.
     * @see #getRequestProperties(java.lang.String)
     * @since 1.4
     */
    public void addRequestProperty(String key, String value) {
        delegate.addRequestProperty(key, value);
    }

    /**
     * Overwrite super class method
     */
    public int getResponseCode() throws IOException {
        return delegate.getResponseCode();
    }

    public String getRequestProperty(String key) {
        return delegate.getRequestProperty(key);
    }

    /**
     * Returns an unmodifiable Map of general request
     * properties for this connection. The Map keys
     * are Strings that represent the request-header
     * field names. Each Map value is a unmodifiable List
     * of Strings that represents the corresponding
     * field values.
     *
     * @return  a Map of the general request properties for this connection.
     * @throws IllegalStateException if already connected
     * @since 1.4
     */
    public Map<String,List<String>> getRequestProperties() {
        return delegate.getRequestProperties();
    }

    /*
     * We support JDK 1.2.x so we can't count on these from JDK 1.3.
     * We override and supply our own version.
     */
    public void setInstanceFollowRedirects(boolean shouldFollow) {
        delegate.setInstanceFollowRedirects(shouldFollow);
    }

    public boolean getInstanceFollowRedirects() {
        return delegate.getInstanceFollowRedirects();
    }

    public void setRequestMethod(String method) throws ProtocolException {
        delegate.setRequestMethod(method);
    }

    public String getRequestMethod() {
        return delegate.getRequestMethod();
    }

    public String getResponseMessage() throws IOException {
        return delegate.getResponseMessage();
    }

    public long getHeaderFieldDate(String name, long Default) {
        return delegate.getHeaderFieldDate(name, Default);
    }

    public Permission getPermission() throws IOException {
        return delegate.getPermission();
    }

    public URL getURL() {
        return delegate.getURL();
    }

    public int getContentLength() {
        return delegate.getContentLength();
    }

    public long getContentLengthLong() {
        return delegate.getContentLengthLong();
    }

    public String getContentType() {
        return delegate.getContentType();
    }

    public String getContentEncoding() {
        return delegate.getContentEncoding();
    }

    public long getExpiration() {
        return delegate.getExpiration();
    }

    public long getDate() {
        return delegate.getDate();
    }

    public long getLastModified() {
        return delegate.getLastModified();
    }

    public int getHeaderFieldInt(String name, int Default) {
        return delegate.getHeaderFieldInt(name, Default);
    }

    public long getHeaderFieldLong(String name, long Default) {
        return delegate.getHeaderFieldLong(name, Default);
    }

    public Object getContent() throws IOException {
        return delegate.getContent();
    }

    @SuppressWarnings("rawtypes")
    public Object getContent(Class[] classes) throws IOException {
        return delegate.getContent(classes);
    }

    public String toString() {
        return delegate.toString();
    }

    public void setDoInput(boolean doinput) {
        delegate.setDoInput(doinput);
    }

    public boolean getDoInput() {
        return delegate.getDoInput();
    }

    public void setDoOutput(boolean dooutput) {
        delegate.setDoOutput(dooutput);
    }

    public boolean getDoOutput() {
        return delegate.getDoOutput();
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
        delegate.setAllowUserInteraction(allowuserinteraction);
    }

    public boolean getAllowUserInteraction() {
        return delegate.getAllowUserInteraction();
    }

    public void setUseCaches(boolean usecaches) {
        delegate.setUseCaches(usecaches);
    }

    public boolean getUseCaches() {
        return delegate.getUseCaches();
    }

    public void setIfModifiedSince(long ifmodifiedsince) {
        delegate.setIfModifiedSince(ifmodifiedsince);
    }

    public long getIfModifiedSince() {
        return delegate.getIfModifiedSince();
    }

    public boolean getDefaultUseCaches() {
        return delegate.getDefaultUseCaches();
    }

    public void setDefaultUseCaches(boolean defaultusecaches) {
        delegate.setDefaultUseCaches(defaultusecaches);
    }

    /*
     * finalize (dispose) the delegated object.  Otherwise
     * sun.net.www.protocol.http.HttpURLConnection's finalize()
     * would have to be made public.
     */
    protected void finalize() throws Throwable {
        delegate.dispose();
    }

    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public void setConnectTimeout(int timeout) {
        delegate.setConnectTimeout(timeout);
    }

    public int getConnectTimeout() {
        return delegate.getConnectTimeout();
    }

    public void setReadTimeout(int timeout) {
        delegate.setReadTimeout(timeout);
    }

    public int getReadTimeout() {
        return delegate.getReadTimeout();
    }

    public void setFixedLengthStreamingMode (int contentLength) {
        delegate.setFixedLengthStreamingMode(contentLength);
    }

    public void setFixedLengthStreamingMode(long contentLength) {
        delegate.setFixedLengthStreamingMode(contentLength);
    }

    public void setChunkedStreamingMode (int chunklen) {
        delegate.setChunkedStreamingMode(chunklen);
    }
}
