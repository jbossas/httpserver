/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

/**
 * @test
 * @bug 6270015
 * @summary  Light weight HTTP server
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;

public class Test11 extends Test {
    static class Handler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            read (t.getRequestBody());
            String response = "response";
            t.sendResponseHeaders (200, response.length());
            OutputStream os = t.getResponseBody();
            os.write (response.getBytes ("ISO8859_1"));
            t.close();
        }

        void read (InputStream is ) throws IOException {
            byte[] b = new byte [8096];
            while (is.read (b) != -1) {}
        }
    }

    public static void main (String[] args) throws Exception {
        System.out.print ("Test 11: ");
        HttpServer server = HttpServer.create (new InetSocketAddress(0), 0);
        HttpContext ctx = server.createContext (
            "/foo/bar/", new Handler ()
        );
        ExecutorService s =  Executors.newCachedThreadPool();
        server.setExecutor (s);
        server.start ();
        URL url = new URL ("http://localhost:" + server.getAddress().getPort()+
                "/Foo/bar/test.html");
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
        int r = urlc.getResponseCode();
        System.out.println ("OK");
        s.shutdown();
        server.stop(5);
        if (r == 200) {
            throw new RuntimeException ("wrong response received");
        }
    }
}
