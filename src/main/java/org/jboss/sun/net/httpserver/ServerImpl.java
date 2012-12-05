/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.jboss.sun.net.httpserver;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.com.sun.net.httpserver.Filter;
import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpServer;
import org.jboss.com.sun.net.httpserver.HttpsConfigurator;
import org.jboss.sun.net.httpserver.HttpConnection.State;

/**
 * Provides implementation for both HTTP and HTTPS
 */
class ServerImpl implements TimeSource {

    private String protocol;
    private boolean ajp = true;
    private boolean https;
    private Executor executor;
    private HttpsConfigurator httpsConfig;
    private SSLContext sslContext;
    private ContextList contexts;
    private InetSocketAddress address;
    private ServerSocketChannel schan;
    private Selector selector;
    private SelectionKey listenerKey;
    private Set<HttpConnection> idleConnections;
    private Set<HttpConnection> allConnections;
    /* following two are used to keep track of the times
     * when a connection/request is first received
     * and when we start to send the response
     */
    private Set<HttpConnection> reqConnections;
    private Set<HttpConnection> rspConnections;
    private List<Event> events;
    private Object lolock = new Object();
    private volatile boolean finished = false;
    private volatile boolean terminating = false;
    private boolean bound = false;
    private boolean started = false;
    private volatile long time;  /* current time */
    private volatile long subticks = 0;
    private volatile long ticks; /* number of clock ticks since server started */
    private HttpServer wrapper;

    private final ServerConfig serverConfig;
    private final int clockTick;
    private final long idleInterval;
    private final int maxIdleConnections;
    private final long timerMillis;
    private final long maxReqTime;
    private final long maxRspTime;
    private final boolean timer1Enabled;
    private final boolean debug;

    private Timer timer, timer1;
    private Logger logger;

    ServerImpl (
        HttpServer wrapper, String protocol, InetSocketAddress addr, int backlog
    ) throws IOException {
        this(wrapper, protocol, addr, backlog, null);
    }

    ServerImpl (
            HttpServer wrapper, String protocol, InetSocketAddress addr, int backlog, Map<String, String> configuration
        ) throws IOException {
        ServerConfig sc = new ServerConfig(configuration);
        clockTick = sc.getClockTick();
        idleInterval = sc.getIdleInterval();
        maxIdleConnections = sc.getMaxIdleConnections();
        timerMillis = sc.getTimerMillis();
        maxReqTime = getTimeMillis(sc.getMaxReqTime());
        maxRspTime = getTimeMillis(sc.getMaxRspTime());
        timer1Enabled = maxReqTime != -1 || maxRspTime != -1;
        debug = sc.debugEnabled();
        this.serverConfig = sc;
        
        this.protocol = protocol;
        this.wrapper = wrapper;
        this.logger = Logger.getLogger ("com.sun.net.httpserver");
        sc.checkLegacyProperties (logger);
        https = protocol.equalsIgnoreCase ("https");
        this.address = addr;
        contexts = new ContextList();
        schan = ServerSocketChannel.open();
        if (addr != null) {
            ServerSocket socket = schan.socket();
            socket.bind (addr, backlog);
            bound = true;
        }
        selector = Selector.open ();
        schan.configureBlocking (false);
        listenerKey = schan.register (selector, SelectionKey.OP_ACCEPT);
        dispatcher = new Dispatcher();
        idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        allConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        reqConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        rspConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        time = System.currentTimeMillis();
        timer = new Timer ("server-timer", true);
        timer.schedule (new ServerTimerTask(), clockTick, clockTick);
        if (timer1Enabled) {
            timer1 = new Timer ("server-timer1", true);
            timer1.schedule (new ServerTimerTask1(),timerMillis,timerMillis);
            logger.config ("HttpServer timer1 enabled period in ms:  "+timerMillis);
            logger.config ("MAX_REQ_TIME:  "+maxReqTime);
            logger.config ("MAX_RSP_TIME:  "+maxRspTime);
        }
        events = new LinkedList<Event>();
        logger.config ("HttpServer created "+protocol+" "+ addr);
    }

    public void bind (InetSocketAddress addr, int backlog) throws IOException {
        if (bound) {
            throw new BindException ("HttpServer already bound");
        }
        if (addr == null) {
            throw new NullPointerException ("null address");
        }
        ServerSocket socket = schan.socket();
        socket.bind (addr, backlog);
        bound = true;
    }

    public void start () {
        if (!bound || started || finished) {
            throw new IllegalStateException ("server in wrong state");
        }
        if (executor == null) {
            executor = new DefaultExecutor();
        }
        Thread t = new Thread (dispatcher);
        started = true;
        t.start();
    }

    public void setExecutor (Executor executor) {
        if (started) {
            throw new IllegalStateException ("server already started");
        }
        this.executor = executor;
    }

    private static class DefaultExecutor implements Executor {
        public void execute (Runnable task) {
            task.run();
        }
    }

    public Executor getExecutor () {
        return executor;
    }

    public void setHttpsConfigurator (HttpsConfigurator config) {
        if (config == null) {
            throw new NullPointerException ("null HttpsConfigurator");
        }
        if (started) {
            throw new IllegalStateException ("server already started");
        }
        this.httpsConfig = config;
        sslContext = config.getSSLContext();
    }

    public HttpsConfigurator getHttpsConfigurator () {
        return httpsConfig;
    }
    
    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public void stop (int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException ("negative delay parameter");
        }
        terminating = true;
        try { schan.close(); } catch (IOException e) {}
        selector.wakeup();
        long latest = System.currentTimeMillis() + delay * 1000;
        while (System.currentTimeMillis() < latest) {
            delay();
            if (finished) {
                break;
            }
        }
        finished = true;
        selector.wakeup();
        synchronized (allConnections) {
            for (HttpConnection c : allConnections) {
                c.close();
            }
        }
        allConnections.clear();
        idleConnections.clear();
        timer.cancel();
        if (timer1Enabled) {
            timer1.cancel();
        }
    }

    Dispatcher dispatcher;

    public synchronized HttpContextImpl createContext (String path, HttpHandler handler) {
        if (handler == null || path == null) {
            throw new NullPointerException ("null handler, or path parameter");
        }
        HttpContextImpl context = new HttpContextImpl (protocol, path, handler, this);
        contexts.add (context);
        logger.config ("context created: " + path);
        return context;
    }

    public synchronized HttpContextImpl createContext (String path) {
        if (path == null) {
            throw new NullPointerException ("null path parameter");
        }
        HttpContextImpl context = new HttpContextImpl (protocol, path, null, this);
        contexts.add (context);
        logger.config ("context created: " + path);
        return context;
    }

    public synchronized void removeContext (String path) throws IllegalArgumentException {
        if (path == null) {
            throw new NullPointerException ("null path parameter");
        }
        contexts.remove (protocol, path);
        logger.config ("context removed: " + path);
    }

    public synchronized void removeContext (HttpContext context) throws IllegalArgumentException {
        if (!(context instanceof HttpContextImpl)) {
            throw new IllegalArgumentException ("wrong HttpContext type");
        }
        contexts.remove ((HttpContextImpl)context);
        logger.config ("context removed: " + context.getPath());
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress)schan.socket().getLocalSocketAddress();
    }

    Selector getSelector () {
        return selector;
    }

    void addEvent (Event r) {
        synchronized (lolock) {
            events.add (r);
            selector.wakeup();
        }
    }

    /* main server listener task */

    class Dispatcher implements Runnable {

        private void handleEvent (Event r) {
            ExchangeImpl t = r.exchange;
            HttpConnection c = t.getConnection();
            try {
                if (r instanceof WriteFinishedEvent) {

                    int exchanges = endExchange();
                    if (terminating && exchanges == 0) {
                        finished = true;
                    }
                    responseCompleted (c);
                    LeftOverInputStream is = t.getOriginalInputStream();
                    if (!is.isEOF()) {
                        t.close = true;
                    }
                    if (t.close || idleConnections.size() >= maxIdleConnections) {
                        c.close();
                        allConnections.remove (c);
                    } else {
                        if (is.isDataBuffered()) {
                            /* don't re-enable the interestops, just handle it */
                            requestStarted (c);
                            handle (c.getChannel(), c);
                        } else {
                            connsToRegister.add (c);
                        }
                    }
                }
            } catch (IOException e) {
                logger.log (
                    Level.FINER, "Dispatcher (1)", e
                );
                c.close();
            }
        }

        final LinkedList<HttpConnection> connsToRegister =
                new LinkedList<HttpConnection>();

        void reRegister (HttpConnection c) {
            /* re-register with selector */
            try {
                SocketChannel chan = c.getChannel();
                chan.configureBlocking (false);
                SelectionKey key = chan.register (selector, SelectionKey.OP_READ);
                key.attach (c);
                c.selectionKey = key;
                c.time = getTime() + idleInterval;
                idleConnections.add (c);
            } catch (IOException e) {
                dprint(e);
                logger.log(Level.FINER, "Dispatcher(8)", e);
                c.close();
            }
        }

        public void run() {
            while (!finished) {
                try {
                    ListIterator<HttpConnection> li =
                        connsToRegister.listIterator();
                    for (HttpConnection c : connsToRegister) {
                        reRegister(c);
                    }
                    connsToRegister.clear();

                    List<Event> list = null;
                    selector.select(1000);
                    synchronized (lolock) {
                        if (events.size() > 0) {
                            list = events;
                            events = new LinkedList<Event>();
                        }
                    }

                    if (list != null) {
                        for (Event r: list) {
                            handleEvent (r);
                        }
                    }

                    /* process the selected list now  */

                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selected.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove ();
                        if (key.equals (listenerKey)) {
                            if (terminating) {
                                continue;
                            }
                            SocketChannel chan = schan.accept();
                            if (chan == null) {
                                continue; /* cancel something ? */
                            }
                            chan.configureBlocking (false);
                            SelectionKey newkey = chan.register (selector, SelectionKey.OP_READ);
                            HttpConnection c = new HttpConnection (ServerImpl.this);
                            c.selectionKey = newkey;
                            c.setChannel (chan);
                            newkey.attach (c);
                            requestStarted (c);
                            allConnections.add (c);
                        } else {
                            try {
                                if (key.isReadable()) {
                                    boolean closed;
                                    SocketChannel chan = (SocketChannel)key.channel();
                                    HttpConnection conn = (HttpConnection)key.attachment();

                                    key.cancel();
                                    chan.configureBlocking (true);
                                    if (idleConnections.remove(conn)) {
                                        // was an idle connection so add it
                                        // to reqConnections set.
                                        requestStarted (conn);
                                    }
                                    handle (chan, conn);
                                } else {
                                    //DISABLED assert false;
                                }
                            } catch (CancelledKeyException e) {
                                handleException(key, null);
                            } catch (IOException e) {
                                handleException(key, e);
                            }
                        }
                    }
                    // call the selector just to process the cancelled keys
                    selector.selectNow();
                } catch (IOException e) {
                    logger.log (Level.FINER, "Dispatcher (4)", e);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.log (Level.FINER, "Dispatcher (7)", e);
                }
            }
        }

        private void handleException (SelectionKey key, Exception e) {
            HttpConnection conn = (HttpConnection)key.attachment();
            if (e != null) {
                logger.log (Level.FINER, "Dispatcher (2)", e);
            }
            closeConnection(conn);
        }

        public void handle (SocketChannel chan, HttpConnection conn)
        throws IOException
        {
            try {
                Exchange t = new Exchange (chan, protocol, conn);
                executor.execute (t);
            } catch (HttpError e1) {
                logger.log (Level.FINER, "Dispatcher (4)", e1);
                closeConnection(conn);
            } catch (IOException e) {
                logger.log (Level.FINER, "Dispatcher (5)", e);
                closeConnection(conn);
            }
        }
    }    

    synchronized void dprint (String s) {
        if (debug) {
            System.out.println (s);
        }
    }

    synchronized void dprint (Exception e) {
        if (debug) {
            System.out.println (e);
            e.printStackTrace();
        }
    }

    Logger getLogger () {
        return logger;
    }

    private void closeConnection(HttpConnection conn) {
        conn.close();
        allConnections.remove(conn);
        switch (conn.getState()) {
        case REQUEST:
            reqConnections.remove(conn);
            break;
        case RESPONSE:
            rspConnections.remove(conn);
            break;
        case IDLE:
            idleConnections.remove(conn);
            break;
        }
        //DISABLED assert !reqConnections.remove(conn);
        //DISABLED assert !rspConnections.remove(conn);
        //DISABLED assert !idleConnections.remove(conn);
    }

        /* per exchange task */

    class Exchange implements Runnable {
        SocketChannel chan;
        HttpConnection connection;
        HttpContextImpl context;
        InputStream rawin;
        OutputStream rawout;
        String protocol;
        ExchangeImpl tx;
        HttpContextImpl ctx;
        boolean rejected = false;

        Exchange (SocketChannel chan, String protocol, HttpConnection conn) throws IOException {
            this.chan = chan;
            this.connection = conn;
            this.protocol = protocol;
        }

        public void run () {
            /* context will be null for new connections */
            context = connection.getHttpContext();
            boolean newconnection;
            SSLEngine engine = null;
            String requestLine = null;
            SSLStreams sslStreams = null;
            if (ajp) {
            	try {
            		rawin = new Request.ReadStream (ServerImpl.this, chan);
            		rawout = new Request.WriteStream (ServerImpl.this, chan);
            		RequestAJP req = new RequestAJP(rawin, rawout);
            		Headers headers = req.headers();
            		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            			String key = entry.getKey();
            			List<String> values = entry.getValue();
            			for (String val : values) {
            				System.out.println("Header: " + key + "Val: " + val);
            			}
            		}
            		requestLine = req.requestLine();
	                if (requestLine == null) {
	                    /* connection closed */
	                    closeConnection(connection);
	                    return;
	                }
	                int space = requestLine.indexOf (' ');
	                if (space == -1) {
	                    reject (Code.HTTP_BAD_REQUEST,
	                            requestLine, "Bad request line");
	                    return;
	                }
	                String method = requestLine.substring (0, space);
	                int start = space+1;
	                space = requestLine.indexOf(' ', start);
	                if (space == -1) {
	                    reject (Code.HTTP_BAD_REQUEST,
	                            requestLine, "Bad request line");
	                    return;
	                }
	                String uriStr = requestLine.substring (start, space);
	                URI uri = new URI (uriStr);
	                start = space+1;
	                String version = requestLine.substring (start);
	                String s = headers.getFirst ("Transfer-encoding");
	                long clen = 0L;
	                if (s !=null && s.equalsIgnoreCase ("chunked")) {
	                    clen = -1L;
	                } else {
	                    s = headers.getFirst ("Content-Length");
	                    if (s != null) {
	                        clen = Long.parseLong(s);
	                    }
	                    if (clen == 0) {
	                        requestCompleted (connection);
	                    }
	                }
	                ctx = contexts.findContext (protocol, uri.getPath());
	                if (ctx == null) {
	                    reject (Code.HTTP_NOT_FOUND,
	                            requestLine, "No context found for request");
	                    return;
	                }
	                connection.setContext (ctx);
	                if (ctx.getHandler() == null) {
	                    reject (Code.HTTP_INTERNAL_ERROR,
	                            requestLine, "No handler for context");
	                    return;
	                }
	                tx = new ExchangeImpl (
	                    method, uri, req, clen, connection
	                );
	                String chdr = headers.getFirst("Connection");
	                Headers rheaders = tx.getResponseHeaders();
	
	                if (chdr != null && chdr.equalsIgnoreCase ("close")) {
	                    tx.close = true;
	                }
	                if (version.equalsIgnoreCase ("http/1.0")) {
	                    tx.http10 = true;
	                    if (chdr == null) {
	                        tx.close = true;
	                        rheaders.set ("Connection", "close");
	                    } else if (chdr.equalsIgnoreCase ("keep-alive")) {
	                        rheaders.set ("Connection", "keep-alive");
	                        int idle=(int)idleInterval/1000;
	                        int max=(int)maxIdleConnections;
	                        String val = "timeout="+idle+", max="+max;
	                        rheaders.set ("Keep-Alive", val);
	                    }
	                }
	
                    connection.setParameters (
                        rawin, rawout, chan, engine, sslStreams,
                        sslContext, protocol, ctx, rawin
                    );

                    /* check if client sent an Expect 100 Continue.
	                 * In that case, need to send an interim response.
	                 * In future API may be modified to allow app to
	                 * be involved in this process.
	                 */
	                String exp = headers.getFirst("Expect");

                        // NOT APPLICABLE for AJP
//	                if (exp != null && exp.equalsIgnoreCase ("100-continue")) {
//	                    logReply (100, requestLine, null);
//	                    sendReply (
//	                        Code.HTTP_CONTINUE, false, null
//	                    );
//	                }
                        
                        
	                /* uf is the list of filters seen/set by the user.
	                 * sf is the list of filters established internally
	                 * and which are not visible to the user. uc and sc
	                 * are the corresponding Filter.Chains.
	                 * They are linked together by a LinkHandler
	                 * so that they can both be invoked in one call.
	                 */
	                List<Filter> sf = ctx.getSystemFilters();
	                List<Filter> uf = ctx.getFilters();
	
	                Filter.Chain sc = new Filter.Chain(sf, ctx.getHandler());
	                Filter.Chain uc = new Filter.Chain(uf, new LinkHandler (sc));
	
	                /* set up the two stream references */
	                tx.getRequestBody();
	                tx.getResponseBody();
	                uc.doFilter(new AJPExchangeImpl(tx, rawin, rawout));
//	                if (https) {
//	                    uc.doFilter (new HttpsExchangeImpl (tx));
//	                } else {
//	                    uc.doFilter (new HttpExchangeImpl (tx));
//	                }
            	} catch (IOException e1) {
            		e1.printStackTrace();
	                logger.log (Level.FINER, "ServerImpl.Exchange (1)", e1);
	                closeConnection(connection);
	            } catch (NumberFormatException e3) {
	                reject (Code.HTTP_BAD_REQUEST,
	                        requestLine, "NumberFormatException thrown");
	            //} catch (URISyntaxException e) {
	                //reject (Code.HTTP_BAD_REQUEST,
	                        //requestLine, "URISyntaxException thrown");
	            } catch (Exception e4) {
                        e4.printStackTrace();
	                logger.log (Level.FINER, "ServerImpl.Exchange (2)", e4);
	                closeConnection(connection);
	            }
            } else {
	            try {
	                if (context != null ) {
	                    this.rawin = connection.getInputStream();
	                    this.rawout = connection.getRawOutputStream();
	                    newconnection = false;
	                } else {
	                    /* figure out what kind of connection this is */
	                    newconnection = true;
	                    if (https) {
	                        if (sslContext == null) {
	                            logger.warning ("SSL connection received. No https contxt created");
	                            throw new HttpError ("No SSL context established");
	                        }
	                        sslStreams = new SSLStreams (ServerImpl.this, sslContext, chan);
	                        rawin = sslStreams.getInputStream();
	                        rawout = sslStreams.getOutputStream();
	                        engine = sslStreams.getSSLEngine();
	                        connection.sslStreams = sslStreams;
	                    } else {
	                        rawin = new BufferedInputStream(
	                            new Request.ReadStream (
	                                ServerImpl.this, chan
	                        ));
	                        rawout = new Request.WriteStream (
	                            ServerImpl.this, chan
	                        );
	                    }
	                    connection.raw = rawin;
	                    connection.rawout = rawout;
	                }
	                Request req = new Request (rawin, rawout);
	                requestLine = req.requestLine();
	                if (requestLine == null) {
	                    /* connection closed */
	                    closeConnection(connection);
	                    return;
	                }
	                int space = requestLine.indexOf (' ');
	                if (space == -1) {
	                    reject (Code.HTTP_BAD_REQUEST,
	                            requestLine, "Bad request line");
	                    return;
	                }
	                String method = requestLine.substring (0, space);
	                int start = space+1;
	                space = requestLine.indexOf(' ', start);
	                if (space == -1) {
	                    reject (Code.HTTP_BAD_REQUEST,
	                            requestLine, "Bad request line");
	                    return;
	                }
	                String uriStr = requestLine.substring (start, space);
	                URI uri = new URI (uriStr);
	                start = space+1;
	                String version = requestLine.substring (start);
	                Headers headers = req.headers();
	                String s = headers.getFirst ("Transfer-encoding");
	                long clen = 0L;
	                if (s !=null && s.equalsIgnoreCase ("chunked")) {
	                    clen = -1L;
	                } else {
	                    s = headers.getFirst ("Content-Length");
	                    if (s != null) {
	                        clen = Long.parseLong(s);
	                    }
	                    if (clen == 0) {
	                        requestCompleted (connection);
	                    }
	                }
	                ctx = contexts.findContext (protocol, uri.getPath());
	                if (ctx == null) {
	                    reject (Code.HTTP_NOT_FOUND,
	                            requestLine, "No context found for request");
	                    return;
	                }
	                connection.setContext (ctx);
	                if (ctx.getHandler() == null) {
	                    reject (Code.HTTP_INTERNAL_ERROR,
	                            requestLine, "No handler for context");
	                    return;
	                }
	                tx = new ExchangeImpl (
	                    method, uri, req, clen, connection
	                );
	                String chdr = headers.getFirst("Connection");
	                Headers rheaders = tx.getResponseHeaders();
	
	                if (chdr != null && chdr.equalsIgnoreCase ("close")) {
	                    tx.close = true;
	                }
	                if (version.equalsIgnoreCase ("http/1.0")) {
	                    tx.http10 = true;
	                    if (chdr == null) {
	                        tx.close = true;
	                        rheaders.set ("Connection", "close");
	                    } else if (chdr.equalsIgnoreCase ("keep-alive")) {
	                        rheaders.set ("Connection", "keep-alive");
	                        int idle=(int)idleInterval/1000;
	                        int max=(int)maxIdleConnections;
	                        String val = "timeout="+idle+", max="+max;
	                        rheaders.set ("Keep-Alive", val);
	                    }
	                }
	
	                if (newconnection) {
	                    connection.setParameters (
	                        rawin, rawout, chan, engine, sslStreams,
	                        sslContext, protocol, ctx, rawin
	                    );
	                }
	                /* check if client sent an Expect 100 Continue.
	                 * In that case, need to send an interim response.
	                 * In future API may be modified to allow app to
	                 * be involved in this process.
	                 */
	                String exp = headers.getFirst("Expect");
	                if (exp != null && exp.equalsIgnoreCase ("100-continue")) {
	                    logReply (100, requestLine, null);
	                    sendReply (
	                        Code.HTTP_CONTINUE, false, null
	                    );
	                }
	                /* uf is the list of filters seen/set by the user.
	                 * sf is the list of filters established internally
	                 * and which are not visible to the user. uc and sc
	                 * are the corresponding Filter.Chains.
	                 * They are linked together by a LinkHandler
	                 * so that they can both be invoked in one call.
	                 */
	                List<Filter> sf = ctx.getSystemFilters();
	                List<Filter> uf = ctx.getFilters();
	
	                Filter.Chain sc = new Filter.Chain(sf, ctx.getHandler());
	                Filter.Chain uc = new Filter.Chain(uf, new LinkHandler (sc));
	
	                /* set up the two stream references */
	                tx.getRequestBody();
	                tx.getResponseBody();
	                if (https) {
	                    uc.doFilter (new HttpsExchangeImpl (tx));
	                } else {
	                    uc.doFilter (new HttpExchangeImpl (tx));
	                }
	
	            } catch (IOException e1) {
	                logger.log (Level.FINER, "ServerImpl.Exchange (1)", e1);
	                closeConnection(connection);
	            } catch (NumberFormatException e3) {
	                reject (Code.HTTP_BAD_REQUEST,
	                        requestLine, "NumberFormatException thrown");
	            } catch (URISyntaxException e) {
	                reject (Code.HTTP_BAD_REQUEST,
	                        requestLine, "URISyntaxException thrown");
	            } catch (Exception e4) {
	                logger.log (Level.FINER, "ServerImpl.Exchange (2)", e4);
	                closeConnection(connection);
	            }
            }
        }

        /* used to link to 2 or more Filter.Chains together */

        class LinkHandler implements HttpHandler {
            Filter.Chain nextChain;

            LinkHandler (Filter.Chain nextChain) {
                this.nextChain = nextChain;
            }

            public void handle (HttpExchange exchange) throws IOException {
                nextChain.doFilter (exchange);
            }
        }

        void reject (int code, String requestStr, String message) {
            rejected = true;
            logReply (code, requestStr, message);
            sendReply (
                code, false, "<h1>"+code+Code.msg(code)+"</h1>"+message
            );
            closeConnection(connection);
        }

        void sendReply (
            int code, boolean closeNow, String text)
        {
            try {
                StringBuilder builder = new StringBuilder (512);
                builder.append ("HTTP/1.1 ")
                    .append (code).append (Code.msg(code)).append ("\r\n");

                if (text != null && text.length() != 0) {
                    builder.append ("Content-Length: ")
                        .append (text.length()).append ("\r\n")
                        .append ("Content-Type: text/html\r\n");
                } else {
                    builder.append ("Content-Length: 0\r\n");
                    text = "";
                }
                if (closeNow) {
                    builder.append ("Connection: close\r\n");
                }
                builder.append ("\r\n").append (text);
                String s = builder.toString();
                byte[] b = s.getBytes("ISO8859_1");
                rawout.write (b);
                rawout.flush();
                if (closeNow) {
                    closeConnection(connection);
                }
            } catch (IOException e) {
                logger.log (Level.FINER, "ServerImpl.sendReply", e);
                closeConnection(connection);
            }
        }

    }

    void logReply (int code, String requestStr, String text) {
        if (!logger.isLoggable(Level.FINE)) {
            return;
        }
        if (text == null) {
            text = "";
        }
        String r;
        if (requestStr.length() > 80) {
           r = requestStr.substring (0, 80) + "<TRUNCATED>";
        } else {
           r = requestStr;
        }
        String message = r + " [" + code + " " +
                    Code.msg(code) + "] ("+text+")";
        logger.fine (message);
    }

    long getTicks() {
        return ticks;
    }

    public long getTime() {
        return time;
    }

    void delay () {
        Thread.yield();
        try {
            Thread.sleep (200);
        } catch (InterruptedException e) {}
    }

    private int exchangeCount = 0;

    synchronized void startExchange () {
        exchangeCount ++;
    }

    synchronized int endExchange () {
        exchangeCount --;
        //DISABLED assert exchangeCount >= 0;
        return exchangeCount;
    }

    HttpServer getWrapper () {
        return wrapper;
    }

    void requestStarted (HttpConnection c) {
        c.creationTime = getTime();
        c.setState (State.REQUEST);
        reqConnections.add (c);
    }

    // called after a request has been completely read
    // by the server. This stops the timer which would
    // close the connection if the request doesn't arrive
    // quickly enough. It then starts the timer
    // that ensures the client reads the response in a timely
    // fashion.

    void requestCompleted (HttpConnection c) {
        //DISABLED assert c.getState() == State.REQUEST;
        reqConnections.remove (c);
        c.rspStartedTime = getTime();
        rspConnections.add (c);
        c.setState (State.RESPONSE);
    }

    // called after response has been sent
    void responseCompleted (HttpConnection c) {
        //DISABLED assert c.getState() == State.RESPONSE;
        rspConnections.remove (c);
        c.setState (State.IDLE);
    }

    /**
     * TimerTask run every CLOCK_TICK ms
     */
    class ServerTimerTask extends TimerTask {
        public void run () {
            LinkedList<HttpConnection> toClose = new LinkedList<HttpConnection>();
            time = System.currentTimeMillis();
            ticks ++;
            synchronized (idleConnections) {
                for (HttpConnection c : idleConnections) {
                    if (c.time <= time) {
                        toClose.add (c);
                    }
                }
                for (HttpConnection c : toClose) {
                    idleConnections.remove (c);
                    allConnections.remove (c);
                    c.close();
                }
            }
        }
    }

    class ServerTimerTask1 extends TimerTask {

        // runs every TIMER_MILLIS
        public void run () {
            LinkedList<HttpConnection> toClose = new LinkedList<HttpConnection>();
            time = System.currentTimeMillis();
            synchronized (reqConnections) {
                if (maxReqTime != -1) {
                    for (HttpConnection c : reqConnections) {
                        if (c.creationTime + timerMillis + maxReqTime <= time) {
                            toClose.add (c);
                        }
                    }
                    for (HttpConnection c : toClose) {
                        logger.log (Level.FINE, "closing: no request: " + c);
                        reqConnections.remove (c);
                        allConnections.remove (c);
                        c.close();
                    }
                }
            }
            toClose = new LinkedList<HttpConnection>();
            synchronized (rspConnections) {
                if (maxRspTime != -1) {
                    for (HttpConnection c : rspConnections) {
                        if (c.rspStartedTime + timerMillis +maxRspTime <= time) {
                            toClose.add (c);
                        }
                    }
                    for (HttpConnection c : toClose) {
                        logger.log (Level.FINE, "closing: no response: " + c);
                        rspConnections.remove (c);
                        allConnections.remove (c);
                        c.close();
                    }
                }
            }
        }
    }

    void logStackTrace (String s) {
        logger.finest (s);
        StringBuilder b = new StringBuilder ();
        StackTraceElement[] e = Thread.currentThread().getStackTrace();
        for (int i=0; i<e.length; i++) {
            b.append (e[i].toString()).append("\n");
        }
        logger.finest (b.toString());
    }

    static long getTimeMillis(long secs) {
        if (secs == -1) {
            return -1;
        } else {
            return secs * 1000;
        }
    }
}
