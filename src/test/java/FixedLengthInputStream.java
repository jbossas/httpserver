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
 * @bug 6756771 6755625
 * @summary  com.sun.net.httpserver.HttpServer should handle POSTs larger than 2Gig
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public class FixedLengthInputStream
{
    static final long POST_SIZE = 4L * 1024L * 1024L * 1024L; // 4Gig

    void test(String[] args) throws IOException {
        HttpServer httpServer = startHttpServer();
        int port = httpServer.getAddress().getPort();
        try {
            URL url = new URL("http://localhost:" + port + "/flis/");
            HttpURLConnection uc = (HttpURLConnection)url.openConnection();
            uc.setDoOutput(true);
            uc.setRequestMethod("POST");
            uc.setFixedLengthStreamingMode(Integer.MAX_VALUE);
            OutputStream os = uc.getOutputStream();

            /* create a 32K byte array with data to POST */
            int thirtyTwoK = 32 * 1024;
            byte[] ba = new byte[thirtyTwoK];
            for (int i =0; i<thirtyTwoK; i++)
                ba[i] = (byte)i;

            long times = POST_SIZE / thirtyTwoK;
            for (int i=0; i<times; i++) {
                os.write(ba);
            }

            os.close();
            InputStream is = uc.getInputStream();
            while(is.read(ba) != -1);
            is.close();

            pass();
        } finally {
            httpServer.stop(0);
        }
    }

    /**
     * Http Server
     */
    HttpServer startHttpServer() throws IOException {
        if (debug) {
            Logger logger =
            Logger.getLogger("com.sun.net.httpserver");
            Handler outHandler = new StreamHandler(System.out,
                                     new SimpleFormatter());
            outHandler.setLevel(Level.FINEST);
            logger.setLevel(Level.FINEST);
            logger.addHandler(outHandler);
        }
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/flis/", new MyHandler(POST_SIZE));
        httpServer.start();
        return httpServer;
    }

    class MyHandler implements HttpHandler {
        static final int BUFFER_SIZE = 32 * 1024;
        long expected;

        MyHandler(long expected){
            this.expected = expected;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            InputStream is = t.getRequestBody();
            byte[] ba = new byte[BUFFER_SIZE];
            int read;
            long count = 0L;
            while((read = is.read(ba)) != -1) {
                count += read;
            }
            is.close();

            check(count == expected, "Expected: " + expected + ", received "
                    + count);

            debug("Received " + count + " bytes");

            t.sendResponseHeaders(200, -1);
            t.close();
        }
    }

         //--------------------- Infrastructure ---------------------------
    boolean debug = true;
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void check(boolean cond, String failMessage) {if (cond) pass(); else fail(failMessage);}
    void debug(String message) {if(debug) { System.out.println(message); }  }
    public static void main(String[] args) throws Throwable {
        Class<?> k = new Object(){}.getClass().getEnclosingClass();
        try {k.getMethod("instanceMain",String[].class)
                .invoke( k.newInstance(), (Object) args);}
        catch (Throwable e) {throw e.getCause();}}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}

}
