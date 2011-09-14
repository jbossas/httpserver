/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 */

/**
 * @test
 * @bug 6886436
 * @summary
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;

public class B6886436 {

    public static void main (String[] args) throws Exception {
        Logger logger = Logger.getLogger ("com.sun.net.httpserver");
        ConsoleHandler c = new ConsoleHandler();
        c.setLevel (Level.WARNING);
        logger.addHandler (c);
        logger.setLevel (Level.WARNING);
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        URL url = new URL ("http://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection ();
        try {
            InputStream is = urlc.getInputStream();
            while (is.read()!= -1) ;
            is.close ();
            urlc = (HttpURLConnection)url.openConnection ();
            urlc.setReadTimeout (3000);
            is = urlc.getInputStream();
            while (is.read()!= -1);
            is.close ();

        } catch (IOException e) {
            server.stop(2);
            executor.shutdown();
            throw new RuntimeException ("Test failed");
        }
        server.stop(2);
        executor.shutdown();
        System.out.println ("OK");
    }

    public static boolean error = false;

    static class Handler implements HttpHandler {
        int invocation = 1;
        public void handle (HttpExchange t)
            throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            while (is.read () != -1) ;
            is.close();
            // send a 204 response with an empty chunked body
            t.sendResponseHeaders (204, 0);
            t.close();
        }
    }
}
