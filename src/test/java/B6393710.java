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
 * @bug 6393710
 * @summary  Non authenticated call followed by authenticated call never returns
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.jboss.com.sun.net.httpserver.BasicAuthenticator;
import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;

/*
 * Test checks for following bug(s) when a POST containing a request body
 * needs to be authenticated
 *
 * 1) we were not reading the request body
 *
 * 2) we were not re-enabling the interestops for the socket channel
 */

public class B6393710 {

    static String CRLF = "\r\n";

    /* Two post requests containing data. The second one
     * has the expected authorization credentials
     */
    static String cmd =
        "POST /test/foo HTTP/1.1"+CRLF+
        "Content-Length: 22"+CRLF+
        "Pragma: no-cache"+CRLF+
        "Cache-Control: no-cache"+CRLF+ CRLF+
        "<item desc=\"excuse\" />"+
        "POST /test/foo HTTP/1.1"+CRLF+
        "Content-Length: 22"+CRLF+
        "Pragma: no-cache"+CRLF+
        "Authorization: Basic ZnJlZDpmcmVkcGFzc3dvcmQ="+CRLF+
        "Cache-Control: no-cache"+CRLF+ CRLF+
        "<item desc=\"excuse\" />";

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);
        ctx.setAuthenticator (new BasicAuthenticator ("test") {
            public boolean checkCredentials (String user, String pass) {
                return user.equals ("fred") && pass.equals("fredpassword");
            }
        });

        server.start ();

        Socket s = new Socket ("localhost", server.getAddress().getPort());
        s.setSoTimeout (5000);

        OutputStream os = s.getOutputStream();
        os.write (cmd.getBytes());
        InputStream is = s.getInputStream ();
        try {
            ok = readAndCheck (is, "401 Unauthorized") &&
                 readAndCheck (is, "200 OK");
        } catch (SocketTimeoutException e) {
            System.out.println ("Did not received expected data");
            ok = false;
        } finally {
            s.close();
            server.stop(2);
        }

        if (requests != 1) {
            throw new RuntimeException ("server handler did not receive the request");
        }
        if (!ok) {
            throw new RuntimeException ("did not get 200 OK");
        }
        System.out.println ("OK");
    }

    /* check for expected string and return true if found in stream */

    static boolean readAndCheck (InputStream is, String expected) throws IOException {
        int c;
        int count = 0;
        int expLen = expected.length();
        expected = expected.toLowerCase();

        while ((c=is.read()) != -1) {
            c = Character.toLowerCase (c);
            if (c == expected.charAt (count)) {
                count ++;
                if (count == expLen) {
                    return true;
                }
            } else {
                count = 0;
            }
        }
        return false;
    }

    public static boolean ok = false;
    static int requests = 0;

    static class Handler implements HttpHandler {
        int invocation = 1;
        public void handle (HttpExchange t)
            throws IOException
        {
            int count = 0;
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            while (is.read () != -1) {
                count ++;
            }
            if (count != 22) {
                System.out.println ("Handler expected 22. got " + count);
                ok = false;
            }
            is.close();
            t.sendResponseHeaders (200, -1);
            t.close();
            requests ++;
        }
    }
}
