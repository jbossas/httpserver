/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package org.jboss.com.sun.net.httpserver;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class BZ1312064 {

    private static HttpServer server;
    private static ExecutorService executor;
    private static SimpleAuthenticator authenticator;
    private static Map<Pattern, Charset> browserCharsetMap = new HashMap<Pattern, Charset>();

    static {
        browserCharsetMap.put(Pattern.compile(".*Firefox.*"), Charset.forName("8859_1"));
    }

    // set up one server instance for all tests to speed things up
    @BeforeClass
    public static void setUpServer() throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);

        authenticator = new SimpleAuthenticator();
        ctx.setAuthenticator (authenticator);
        executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();
    }

    @AfterClass
    public static void shutDownServer() {
        server.stop(2);
        executor.shutdown();
    }

    @After
    public void cleanUpAllowedCredentials() {
        authenticator.purge();
    }

    @Test
    public void testASCIIPassword() throws Exception {
        authenticator.accept("fred", "xyz");

        final int responseCode = makeCall("fred", "xyz", null, "UTF-8");

        Assert.assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }

    @Test
    public void testNonAsciiPasswordOnUtf8Browser() throws Exception {
        authenticator.accept("fred", "test123!ü");

        final int responseCode = makeCall("fred", "test123!ü", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36", "UTF-8");

        Assert.assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }

    @Test
    public void testNonAsciiPasswordOnIso8859Browser() throws Exception {
        authenticator.accept("fred", "test123!ü");

        final int responseCode = makeCall("fred", "test123!ü", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1", "8859_1");

        Assert.assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }

    private int makeCall(String username, String password, String userAgent, String encoding) throws IOException {
        URL url = new URL ("http://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection ();

        final String encodedCredentials = Base64.byteArrayToBase64((username + ":" + password).getBytes(encoding));
        urlc.addRequestProperty("Authorization", "Basic " + encodedCredentials);
        if (userAgent != null) {
            urlc.addRequestProperty("User-Agent", userAgent);
        }
        urlc.setRequestMethod("GET");

        return urlc.getResponseCode();
    }

    public static boolean error = false;


    static class SimpleAuthenticator extends BasicAuthenticator {
        private Map<String, String> acceptedCredentials = new HashMap<String, String>();

        SimpleAuthenticator() {
            super ("foobar@test.realm", Charset.forName("UTF-8"), BZ1312064.browserCharsetMap);
        }

        public boolean checkCredentials (String username, String pw) {
            return acceptedCredentials.containsKey(username) && acceptedCredentials.get(username).equals(pw);
        }

        public void accept(String username, String password) {
            acceptedCredentials.put(username, password);
        }

        public void purge() {
            acceptedCredentials.clear();
        }
    }

    static class Handler implements HttpHandler {
        public void handle (HttpExchange t)
                throws IOException
        {
            t.sendResponseHeaders (200, -1);
            t.close();
        }
    }
}
