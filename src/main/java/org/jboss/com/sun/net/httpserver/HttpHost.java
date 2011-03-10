/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * This class implements a simple HTTP host. A HttpHost may be "real" or "virtual".
 * A "real" host is one which is bound to an IP address and extends the {@link HttpServer}
 * subclass.  A "virtual" host allows for more than one set of registered contexts
 * per physical server, differentiated by host name, IP address, or other criteria.
 * <p>
 * One or more {@link HttpHandler} objects must be associated with a host
 * in order to process requests. Each such HttpHandler is registered
 * with a root URI path which represents the
 * location of the application or service on this server. The mapping of a handler
 * to a {@code HttpHost} is essentially equivalent to the mapping of a handler to a
 * {@link HttpServer}.
 *
 * @since 1.7
 */
public abstract class HttpHost {
    /**
     * Creates a {@code HttpContext}. A {@code HttpContext} represents a mapping from a
     * URI path to a exchange handler on this {@code HttpHost}. Once created, all requests
     * received by the server for the path will be handled by calling
     * the given handler object. The context is identified by the path, and
     * can later be removed from the server using this with the {@link #removeContext(String)} method.
     * <p>
     * The path specifies the root URI path for this context. The first character of path must be
     * {@code '/'}. <p>
     * The class overview describes how incoming request URIs are <a href="#mapping_description">mapped</a>
     * to HttpContext instances.
     * @param path the root URI path to associate the context with
     * @param handler the handler to invoke for incoming requests.
     * @throws IllegalArgumentException if path is invalid, or if a context
     *          already exists for this path
     * @throws NullPointerException if either path, or handler are <code>null</code>
     */
    public abstract HttpContext createContext (String path, HttpHandler handler) ;

    /**
     * Creates a {@code HttpContext} without initially specifying a handler. The handler must later be specified using
     * {@link com.sun.net.httpserver.HttpContext#setHandler(com.sun.net.httpserver.HttpHandler)}.  A {@code HttpContext} represents a mapping from a
     * URI path to an exchange handler on this {@code HttpHost}. Once created, and when
     * the handler has been set, all requests
     * received by the server for the path will be handled by calling
     * the handler object. The context is identified by the path, and
     * can later be removed from the server using this with the {@link #removeContext(String)} method.
     * <p>
     * The path specifies the root URI path for this context. The first character of path must be
     * {@code '/'}. <p>
     * The class overview describes how incoming request URIs are <a href="#mapping_description">mapped</a>
     * to HttpContext instances.
     * @param path the root URI path to associate the context with
     * @throws IllegalArgumentException if path is invalid, or if a context
     *          already exists for this path
     * @throws NullPointerException if path is <code>null</code>
     */
    public abstract HttpContext createContext (String path) ;

    /**
     * Removes the context identified by the given path from the server.
     * Removing a context does not affect exchanges currently being processed
     * but prevents new ones from being accepted.
     * @param path the path of the handler to remove
     * @throws IllegalArgumentException if no handler corresponding to this
     *          path exists.
     * @throws NullPointerException if path is <code>null</code>
     */
    public abstract void removeContext (String path) throws IllegalArgumentException ;

    /**
     * Removes the given context from the server.
     * Removing a context does not affect exchanges currently being processed
     * but prevents new ones from being accepted.
     * @param context the context to remove
     * @throws IllegalArgumentException if the given context is not registered on this host
     * @throws NullPointerException if context is <code>null</code>
     */
    public abstract void removeContext (HttpContext context)  throws IllegalArgumentException ;
}
