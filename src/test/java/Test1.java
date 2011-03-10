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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;
import org.jboss.com.sun.net.httpserver.HttpsConfigurator;
import org.jboss.com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

/* basic http/s connectivity test
 * Tests:
 *      - client/server
 *      - send/receive large/small file
 *      - chunked encoding
 *      - via http and https
 */

public class Test1 extends Test {

    static SSLContext ctx;

    public static void main (String[] args) throws Exception {
        HttpServer s1 = null;
        HttpsServer s2 = null;
        ExecutorService executor=null;
        try {
            String root = System.getProperty ("test.src")+ "/docs";
            System.out.print ("Test1: ");
            InetSocketAddress addr = new InetSocketAddress (0);
            s1 = HttpServer.create (addr, 0);
            if (s1 instanceof HttpsServer) {
                throw new RuntimeException ("should not be httpsserver");
            }
            s2 = HttpsServer.create (addr, 0);
            HttpHandler h = new FileServerHandler (root);
            HttpContext c1 = s1.createContext ("/test1", h);
            HttpContext c2 = s2.createContext ("/test1", h);
            executor = Executors.newCachedThreadPool();
            s1.setExecutor (executor);
            s2.setExecutor (executor);
            ctx = new SimpleSSLContext(System.getProperty("test.src")).get();
            s2.setHttpsConfigurator(new HttpsConfigurator (ctx));
            s1.start();
            s2.start();

            int port = s1.getAddress().getPort();
            int httpsport = s2.getAddress().getPort();
            test (true, "http", root+"/test1", port, "smallfile.txt", 23);
            test (true, "http", root+"/test1", port, "largefile.txt", 2730088);
            test (true, "https", root+"/test1", httpsport, "smallfile.txt", 23);
            test (true, "https", root+"/test1", httpsport, "largefile.txt", 2730088);
            test (false, "http", root+"/test1", port, "smallfile.txt", 23);
            test (false, "http", root+"/test1", port, "largefile.txt", 2730088);
            test (false, "https", root+"/test1", httpsport, "smallfile.txt", 23);
            test (false, "https", root+"/test1", httpsport, "largefile.txt", 2730088);
            System.out.println ("OK");
        } finally {
            delay();
            s1.stop(2);
            s2.stop(2);
            executor.shutdown ();
        }
    }

    static void test (boolean fixedLen, String protocol, String root, int port, String f, int size) throws Exception {
        URL url = new URL (protocol+"://localhost:"+port+"/test1/"+f);
        HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
        if (urlc instanceof HttpsURLConnection) {
            HttpsURLConnection urlcs = (HttpsURLConnection) urlc;
            urlcs.setHostnameVerifier (new HostnameVerifier () {
                public boolean verify (String s, SSLSession s1) {
                    return true;
                }
            });
            urlcs.setSSLSocketFactory (ctx.getSocketFactory());
        }
        byte [] buf = new byte [4096];

        if (fixedLen) {
            urlc.setRequestProperty ("XFixed", "yes");
        }
        InputStream is = urlc.getInputStream();
        File temp = File.createTempFile ("Test1", null);
        temp.deleteOnExit();
        OutputStream fout = new BufferedOutputStream (new FileOutputStream(temp));
        int c, count = 0;
        while ((c=is.read(buf)) != -1) {
            count += c;
            fout.write (buf, 0, c);
        }
        is.close();
        fout.close();

        if (count != size) {
            throw new RuntimeException ("wrong amount of data returned");
        }
        String orig = root + "/" + f;
        compare (new File(orig), temp);
        temp.delete();
    }

    /* compare the contents of the two files */

    static void compare (File f1, File f2) throws IOException {
        InputStream i1 = new BufferedInputStream (new FileInputStream(f1));
        InputStream i2 = new BufferedInputStream (new FileInputStream(f2));

        int c1,c2;
        try {
            while ((c1=i1.read()) != -1) {
                c2 = i2.read();
                if (c1 != c2) {
                    throw new RuntimeException ("file compare failed 1");
                }
            }
            if (i2.read() != -1) {
                throw new RuntimeException ("file compare failed 2");
            }
        } finally {
            i1.close();
            i2.close();
        }
    }
}
