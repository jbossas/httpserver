/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package org.jboss.com.sun.net.httpserver;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

import org.jboss.com.sun.net.httpserver.spi.HttpServerProvider;

/**
 * This class implements a simple HTTP server. A HttpServer is bound to an IP address
 * and port number and listens for incoming TCP connections from clients on this address.
 * The sub-class {@link HttpsServer} implements a server which handles HTTPS requests.
 * <p>
 * One or more {@link HttpHandler} objects must be associated with a server
 * in order to process requests. Each such HttpHandler is registered
 * with a root URI path which represents the
 * location of the application or service on this server. The mapping of a handler
 * to a HttpServer is encapsulated by a {@link HttpContext} object. HttpContexts
 * are created by calling {@link #createContext(String,HttpHandler)}.
 * Any request for which no handler can be found is rejected with a 404 response.
 * Management of threads can be done external to this object by providing a
 * {@link java.util.concurrent.Executor} object. If none is provided a default
 * implementation is used.
 * <p>
 * <a name="mapping_description"></a>
 * <b>Mapping request URIs to HttpContext paths</b><p>
 * When a HTTP request is received,
 * the appropriate HttpContext (and handler) is located by finding the context
 * whose path is the longest matching prefix of the request URI's path.
 * Paths are matched literally, which means that the strings are compared
 * case sensitively, and with no conversion to or from any encoded forms.
 * For example. Given a HttpServer with the following HttpContexts configured.<p>
 * <table >
 * <tr><td><i>Context</i></td><td><i>Context path</i></td></tr>
 * <tr><td>ctx1</td><td>"/"</td></tr>
 * <tr><td>ctx2</td><td>"/apps/"</td></tr>
 * <tr><td>ctx3</td><td>"/apps/foo/"</td></tr>
 * </table>
 * <p>
 * the following table shows some request URIs and which, if any context they would
 * match with.<p>
 * <table>
 * <tr><td><i>Request URI</i></td><td><i>Matches context</i></td></tr>
 * <tr><td>"http://foo.com/apps/foo/bar"</td><td>ctx3</td></tr>
 * <tr><td>"http://foo.com/apps/Foo/bar"</td><td>no match, wrong case</td></tr>
 * <tr><td>"http://foo.com/apps/app1"</td><td>ctx2</td></tr>
 * <tr><td>"http://foo.com/foo"</td><td>ctx1</td></tr>
 * </table>
 * <p>
 * <b>Note about socket backlogs</b><p>
 * When binding to an address and port number, the application can also specify an integer
 * <i>backlog</i> parameter. This represents the maximum number of incoming TCP connections
 * which the system will queue internally. Connections are queued while they are waiting to
 * be accepted by the HttpServer. When the limit is reached, further connections may be
 * rejected (or possibly ignored) by the underlying TCP implementation. Setting the right
 * backlog value is a compromise between efficient resource usage in the TCP layer (not setting
 * it too high) and allowing adequate throughput of incoming requests (not setting it too low).
 * @since 1.6
 */

public abstract class HttpServer extends HttpHost {

    /**
     */
    protected HttpServer () {
    }

    /**
     * creates a HttpServer instance which is initially not bound to any local address/port.
     * The HttpServer is acquired from the currently installed {@link HttpServerProvider}
     * The server must be bound using {@link #bind(InetSocketAddress,int)} before it can be used.
     * @throws IOException
     */
    public static HttpServer create () throws IOException {
        return create (null, 0);
    }

    /**
     * Create a <code>HttpServer</code> instance which will bind to the
     * specified {@link java.net.InetSocketAddress} (IP address and port number)
     *
     * A maximum backlog can also be specified. This is the maximum number of
     * queued incoming connections to allow on the listening socket.
     * Queued TCP connections exceeding this limit may be rejected by the TCP implementation.
     * The HttpServer is acquired from the currently installed {@link HttpServerProvider}
     *
     * @param addr the address to listen on, if <code>null</code> then bind() must be called
     *  to set the address
     * @param backlog the socket backlog. If this value is less than or equal to zero,
     *          then a system default value is used.
     * @throws BindException if the server cannot bind to the requested address,
     *          or if the server is already bound.
     * @throws IOException
     */

    public static HttpServer create (
        InetSocketAddress addr, int backlog
    ) throws IOException {
        HttpServerProvider provider = HttpServerProvider.provider();
        return provider.createHttpServer (addr, backlog);
    }

    /**
     * Binds a currently unbound HttpServer to the given address and port number.
     * A maximum backlog can also be specified. This is the maximum number of
     * queued incoming connections to allow on the listening socket.
     * Queued TCP connections exceeding this limit may be rejected by the TCP implementation.
     * @param addr the address to listen on
     * @param backlog the socket backlog. If this value is less than or equal to zero,
     *          then a system default value is used.
     * @throws BindException if the server cannot bind to the requested address or if the server
     *          is already bound.
     * @throws NullPointerException if addr is <code>null</code>
     */
    public abstract void bind (InetSocketAddress addr, int backlog) throws IOException;

    /**
     * Starts this server in a new background thread. The background thread
     * inherits the priority, thread group and context class loader
     * of the caller.
     */
    public abstract void start () ;

    /**
     * sets this server's {@link java.util.concurrent.Executor} object. An
     * Executor must be established before {@link #start()} is called.
     * All HTTP requests are handled in tasks given to the executor.
     * If this method is not called (before start()) or if it is
     * called with a <code>null</code> Executor, then
     * a default implementation is used, which uses the thread
     * which was created by the {@link #start()} method.
     * @param executor the Executor to set, or <code>null</code> for  default
     *          implementation
     * @throws IllegalStateException if the server is already started
     */
    public abstract void setExecutor (Executor executor);


    /**
     * returns this server's Executor object if one was specified with
     * {@link #setExecutor(Executor)}, or <code>null</code> if none was
     * specified.
     * @return the Executor established for this server or <code>null</code> if not set.
     */
    public abstract Executor getExecutor () ;

    /**
     * stops this server by closing the listening socket and disallowing
     * any new exchanges from being processed. The method will then block
     * until all current exchange handlers have completed or else when
     * approximately <i>delay</i> seconds have elapsed (whichever happens
     * sooner). Then, all open TCP connections are closed, the background
     * thread created by start() exits, and the method returns.
     * Once stopped, a HttpServer cannot be re-used. <p>
     *
     * @param delay the maximum time in seconds to wait until exchanges have finished.
     * @throws IllegalArgumentException if delay is less than zero.
     */
    public abstract void stop (int delay);

    /** {@inheritDoc} */
    public abstract HttpContext createContext (final String path, final HttpHandler handler);

    /** {@inheritDoc} */
    public abstract HttpContext createContext (final String path);

    /** {@inheritDoc} */
    public abstract void removeContext (final String path) throws IllegalArgumentException;

    /** {@inheritDoc} */
    public abstract void removeContext (final HttpContext context) throws IllegalArgumentException;

    /**
     * Creates a virtual host with the given pattern.  A pattern is a sequence of
     * host name segments seprated by <code>"."</code>, optionally preceded by a
     * <code>"*."</code> wildcard designator which means "any host or sequence of
     * hosts".
     * <p>
     * The base implementation throws {@code java.lang.UnsupportedOperationException}
     * to avoid breaking backwards compatibility with existing implementations.
     *
     * @param pattern the virtual host pattern to match
     * @return the virtual host
     *
     * @since 1.7
     */
    public HttpHost createVirtualHost(final String pattern) {
        throw new UnsupportedOperationException("Virutal hosts not supported by this implementation");
    }

    /**
     * Removes the virtual host identified by the given pattern from the server.
     * Removing a virtual host does not affect exchanges currently being
     * processed but prevents new ones from being accepted.
     *
     * @param pattern the virtual host pattern previously registered
     * @throws IllegalArgumentException if no such pattern is registered
     */
    public void removeVirtualHost(final String pattern) throws IllegalArgumentException {
        throw new IllegalArgumentException("No virtual host with the pattern " + pattern + " exists in this server");
    }

    /**
     * Remove the given virtual host from the server.  Removing a virtual host
     * does not affect exchanges currently being
     * processed but prevents new ones from being accepted.
     *
     * @param host the host to remove
     * @throws IllegalArgumentException if no such host is registered
     */
    public void removeVirtualHost(final HttpHost host) throws IllegalArgumentException {
        throw new IllegalArgumentException("No virtual host " + host + " exists in this server");
    }

    /**
     * returns the address this server is listening on
     * @return the address/port number the server is listening on
     */
    public abstract InetSocketAddress getAddress() ;
}
