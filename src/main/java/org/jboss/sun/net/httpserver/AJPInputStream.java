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
    private boolean endOfStreamReached;

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
        } else {
            this.endOfStreamReached = true;
        }
    }
    
    @Override
    protected int readImpl(byte[] b, int offset, int len) throws IOException {
        if (len == 0) {
            return 0;
        } else if (bytesRead >= exchangeImpl.reqContentLen) {
            return -1;
        }
        int currentOffset = offset;
        int methodBytesRead = 0;
        int remainingLen = len;
        while (remainingLen > 0) {
            if (currentBuffer == null || currentBufferPos == currentBuffer.length) {
                if (endOfStreamReached) {
                    break;
                }
                loadBuffer();
            }
            if (remainingLen <= (currentBuffer.length - currentBufferPos)) {
                System.arraycopy(currentBuffer, currentBufferPos, b, currentOffset, remainingLen);
                currentOffset+=remainingLen;
                currentBufferPos += remainingLen;
                bytesRead += remainingLen;
                methodBytesRead += remainingLen;
                remainingLen = 0;
            } else {
                int bToCopy = currentBuffer.length - currentBufferPos;
                System.arraycopy(currentBuffer, currentBufferPos, b, currentOffset, bToCopy);
                currentOffset+=bToCopy;
                currentBufferPos += bToCopy;
                bytesRead += bToCopy;
                methodBytesRead += bToCopy;
                remainingLen = remainingLen - bToCopy;
            }
        }
        
        return methodBytesRead;
    }

}
