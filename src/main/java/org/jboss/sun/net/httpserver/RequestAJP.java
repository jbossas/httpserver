package org.jboss.sun.net.httpserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

import org.jboss.com.sun.net.httpserver.Headers;

public class RequestAJP extends Request {

    private static Logger log = Logger.getLogger(RequestAJP.class.getCanonicalName());
    
    final static int BUF_LEN = 8192;
    final static byte CR = 13;
    final static byte LF = 10;

    private AJPMessage ajpMsg;
    private Headers headers;
    private boolean regularRequest;
    private boolean shouldClose;
	
    RequestAJP (InputStream rawInputStream, OutputStream rawout) throws IOException {
    	super(rawInputStream, rawout, true);
        DataInputStream dis = new DataInputStream(is);
        ajpMsg = new AJPMessage();
        ajpMsg.startRead(dis);
        int msgType = ajpMsg.readType();
        if (msgType == 2) {
            startLine = ajpMsg.readFirstRequestLine();
            regularRequest = true;
        } else if (msgType == 10) {
            // this is a CPing, we should immediately return a CPong now
            DataOutputStream dos = new DataOutputStream(os);
            AJPUtil.writeCPong(dos);
            regularRequest = false;
        } else if (msgType == -1) {
            regularRequest = false;
            shouldClose = true;
        } else {
            log.warning("Received unsupported AJP Message type: " + msgType);
            regularRequest = false;
        }
    }

    public boolean isShouldClose() {
        return shouldClose;
    }
    
    /**
     * Returns true if this is a regular request, false if this
     * is a ping or other instruction message to be handled
     * internally by the RequestAJP class.
     * 
     * @return 
     */
    public boolean isRegularRequest() {
        return regularRequest;
    }
    
    
    Headers headers() throws IOException {
    	if (headers == null) {
    		headers = ajpMsg.readHeaders();
    	}
    	return headers;
    }
}
