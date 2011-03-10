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
 * @run main/othervm -Dorg.jboss.httpserver.idleInterval=4 Test4
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;

/**
 * Test pipe-lining (block after read)
 */
public class Test4 extends Test {
    static int count = 1;
    public static void main (String[] args) throws Exception {
        System.out.print ("Test4: ");
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        int port = server.getAddress().getPort();
        HttpContext c2 = server.createContext ("/test", handler);
        c2.getAttributes().put ("name", "This is the http handler");

        ExecutorService exec = Executors.newCachedThreadPool();
        server.setExecutor (exec);
        try {
            server.start ();
            doClient(port);
            System.out.println ("OK");
        } finally {
            delay();
            server.stop(2);
            exec.shutdown();
        }
    }

    static class Handler implements HttpHandler {
        volatile int invocation = 0;
        public void handle (HttpExchange t)
            throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            int x = invocation ++;
            rmap.set ("XTest", Integer.toString (x));

            switch (x) {
            case 0:
                checkBody (is, body1);
                try {Thread.sleep (2000); } catch (Exception e) {}
                break;
            case 1:
                checkBody (is, body2);
                try {Thread.sleep (1000); } catch (Exception e) {}
                break;
            case 2:
                checkBody (is, body3);
                break;
            case 3:
                checkBody (is, body4);
                break;
            }
            t.sendResponseHeaders (200, -1);
            t.close();
        }
    }

    static void checkBody (InputStream is, String cmp) throws IOException {
        byte [] b = new byte [1024];
        int count = 0, c;
        while ((c=is.read(b, count, b.length-count)) != -1) {
            count+=c;
        }
        is.close();
        String s = new String (b, 0, count, "ISO8859_1");
        if (!s.equals (cmp)) {
            throw new RuntimeException ("strings not equal");
        }
    }

    static String body1 = "1234567890abcdefghij";
    static String body2 = "2234567890abcdefghij0123456789";
    static String body3 = "3wertyuiop";
    static String body4 = "4234567890";

    static String result =
        "HTTP/1.1 200 OK.*Xtest: 0.*"+
        "HTTP/1.1 200 OK.*Xtest: 1.*"+
        "HTTP/1.1 200 OK.*Xtest: 2.*"+
        "HTTP/1.1 200 OK.*Xtest: 3.*";

    public static void doClient (int port) throws Exception {
        String s = "GET /test/1.html HTTP/1.1\r\nContent-length: 20\r\n"+
        "\r\n" +body1 +
        "GET /test/2.html HTTP/1.1\r\nContent-length: 30\r\n"+
        "\r\n"+ body2 +
        "GET /test/3.html HTTP/1.1\r\nContent-length: 10\r\n"+
        "\r\n"+ body3 +
        "GET /test/4.html HTTP/1.1\r\nContent-length: 10\r\n"+
        "\r\n"+body4;

        Socket socket = new Socket ("localhost", port);
        OutputStream os = socket.getOutputStream();
        os.write (s.getBytes());
        InputStream is = socket.getInputStream();
        int c, count=0;
        byte[] b = new byte [1024];
        while ((c=is.read(b, count, b.length-count)) != -1) {
            count +=c;
        }
        is.close();
        socket.close();
        s = new String (b,0,count, "ISO8859_1");
        if (!compare (s, result)) {
            throw new RuntimeException ("wrong string result");
        }
    }

    static boolean compare (String s, String result) {
        Pattern pattern = Pattern.compile (result,
                Pattern.DOTALL|Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher (s);
        return matcher.matches();
    }

}
