package org.jboss.sun.net.httpserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 */
public class AJPInputStream extends LeftOverInputStream {

    private ExchangeImpl exchangeImpl;
    private InputStream ris;
    private DataInputStream dis;
    private OutputStream ros;
    private DataOutputStream dos;
    private byte[] currentBuffer;
    private int currentBufferPos;
    private int bytesRead = 0;

    public AJPInputStream(ExchangeImpl exchangeImpl, InputStream ris, OutputStream ros) {
        super(exchangeImpl, ris);
        this.exchangeImpl = exchangeImpl;
        this.ris = ris;
        this.dis = new DataInputStream(ris);
        this.ros = ros;
        this.dos = new DataOutputStream(ros);
    }

    private void loadBuffer() throws IOException {
        AJPMessage ajpMessage = new AJPMessage();
        ajpMessage.startRead(dis);
        currentBuffer = ajpMessage.readIncomingBody(dis);
        currentBufferPos = 0;
        
        if ((bytesRead + currentBuffer.length) < exchangeImpl.reqContentLen) {
            AJPUtil.writeRequestBodyChunk(dos, AJPUtil.REQUEST_BLOCK_LENGTH);
        }
    }
    
    @Override
    protected int readImpl(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        } else if (bytesRead >= exchangeImpl.reqContentLen) {
            return -1;
        }
        if (currentBuffer == null) {
            loadBuffer();
        }
        int methodBytesRead = 0;
        int remainingLen = len;
        while (remainingLen > 0) {
            if (currentBufferPos == currentBuffer.length) {
                loadBuffer();
            }
            if (remainingLen <= (currentBuffer.length - currentBufferPos)) {
                System.arraycopy(currentBuffer, currentBufferPos, b, off, remainingLen);
                currentBufferPos += remainingLen;
                bytesRead += remainingLen;
                methodBytesRead += remainingLen;
                remainingLen = 0;
            } else {
                int bToCopy = currentBuffer.length - currentBufferPos;
                System.arraycopy(currentBuffer, currentBufferPos, b, off, bToCopy);
                currentBufferPos += bToCopy;
                bytesRead += bToCopy;
                methodBytesRead += bToCopy;
                remainingLen = remainingLen - bToCopy;
            }
        }
        
        return methodBytesRead;
    }

}
