/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 6270015
 * @summary  Light weight HTTP server
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.BasicAuthenticator;
import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpPrincipal;
import org.jboss.com.sun.net.httpserver.HttpServer;

/**
 * Test authentication
 */

public class Test2 extends Test {

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);
        BasicAuthenticator a = new BasicAuthenticator ("foobar@test.realm") {
            public boolean checkCredentials (String username, String pw) {
                return "fred".equals(username) && pw.charAt(0) == 'x';
            }
        };

        ctx.setAuthenticator (a);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();
        java.net.Authenticator.setDefault (new MyAuthenticator());

        URL url = new URL ("http://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        System.out.print ("Test2: " );
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection ();
        InputStream is = urlc.getInputStream();
        int c = 0;
        while (is.read()!= -1) {
            c ++;
        }
        server.stop(2);
        executor.shutdown();
        if (error ) {
            throw new RuntimeException ("test failed error");
        }
        if (c != 0) {
            throw new RuntimeException ("test failed c");
        }
        if (count != 2) {
            throw new RuntimeException ("test failed count = " + count);
        }
        System.out.println ("OK");

    }

    public static boolean error = false;
    public static int count = 0;

    static class MyAuthenticator extends java.net.Authenticator {
        public PasswordAuthentication getPasswordAuthentication () {
            PasswordAuthentication pw;
            if (!getRequestingPrompt().equals ("foobar@test.realm")) {
                Test2.error = true;
            }
            if (count == 0) {
                pw = new PasswordAuthentication ("bad", "wrong".toCharArray());
            } else {
                pw = new PasswordAuthentication ("fred", "xyz".toCharArray());
            }
            count ++;
            return pw;
        }
    }

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
            t.sendResponseHeaders (200, -1);
            HttpPrincipal p = t.getPrincipal ();
            if (!p.getUsername().equals("fred")) {
                error = true;
            }
            if (!p.getRealm().equals("foobar@test.realm")) {
                error = true;
            }
            t.close();
        }
    }
}
