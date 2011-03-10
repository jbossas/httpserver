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
 * @bug 6361557
 * @summary  Lightweight HTTP server quickly runs out of file descriptors on Linux
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;

/**
 * The test simply opens 10,000 separate connections
 * and invokes one http request on each. The client does
 * not close any sockets until after they are closed
 * by the server. This verifies the basic ability
 * of the server to manage a reasonable number of connections
 */
public class B6361557 {

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
            t.sendResponseHeaders (200, -1);
            t.close();
        }
    }

    public static void main (String[] args) throws Exception {
        Handler handler = new Handler();
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test", handler);

        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        final int NUM = 10000;
        ByteBuffer buf = ByteBuffer.allocate (4096);
        InetSocketAddress destaddr = new InetSocketAddress (
                "127.0.0.1", server.getAddress().getPort()
        );
        System.out.println ("destaddr " + destaddr);

        Selector selector = Selector.open ();
        int i = 0;
        while (true) {
            i ++;
            int selres = selector.select (1);
            Set<SelectionKey> selkeys = selector.selectedKeys();
            for (SelectionKey key : selkeys) {
                if (key.isReadable()) {
                    SocketChannel chan = (SocketChannel)key.channel();
                    buf.clear();
                    try {
                        int x = chan.read (buf);
                        if (x == -1) {
                            chan.close();
                        }
                    } catch (IOException e) {}
                }
            }
            if (i< NUM) {
                SocketChannel schan = SocketChannel.open (destaddr);
                String cmd = "GET /test/foo.html HTTP/1.1\r\nContent-length: 0\r\n\r\n";
                buf.rewind ();
                buf.put (cmd.getBytes());
                buf.flip();
                int c = 0;
                while (buf.remaining() > 0) {
                    c += schan.write (buf);
                }
                schan.configureBlocking (false);
                schan.register (selector, SelectionKey.OP_READ, null);
            } else {
                System.out.println ("Finished clients");
                server.stop (1);
                executor.shutdown ();
                return;
            }
        }
    }
}
