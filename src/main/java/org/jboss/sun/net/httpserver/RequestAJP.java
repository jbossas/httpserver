package org.jboss.sun.net.httpserver;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import org.jboss.com.sun.net.httpserver.Headers;

public class RequestAJP extends Request {

    final static int BUF_LEN = 8192;
    final static byte CR = 13;
    final static byte LF = 10;

    private AJPMessage ajpMsg;
    private Headers headers;
	
    RequestAJP (InputStream rawInputStream, OutputStream rawout) throws IOException {
    	super(rawInputStream, rawout, true);
        DataInputStream dis = new DataInputStream(is);
        ajpMsg = new AJPMessage();
        ajpMsg.startRead(dis);
        int msgType = ajpMsg.readType();
        startLine = ajpMsg.readFirstRequestLine();
    }
    
    Headers headers() throws IOException {
    	if (headers == null) {
    		headers = ajpMsg.readHeaders();
    	}
    	return headers;
    }
}
