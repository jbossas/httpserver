package org.jboss.sun.net.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jboss.com.sun.net.httpserver.Headers;

public class AJPUtil {
	public static final int REQUEST_BLOCK_LENGTH = 8186;
    public static final int BODY_CHUNK_MAX_SIZE = 8189;
    
    public static void writeEndResponse(DataOutputStream dos, boolean keepAlive) throws IOException {
		writeResponseHeaderInitial(dos);
		dos.writeByte(0);
		dos.writeByte(2);
		dos.writeByte(5);
		writeBoolean(dos, keepAlive);
	}
    
	public static void writeCPong(DataOutputStream dos) throws IOException {
		writeResponseHeaderInitial(dos);
		dos.writeByte(0);
		dos.writeByte(1);
		dos.writeByte(9);
	}
	
	public static void writeBody(DataOutputStream dos, byte[] contents) throws IOException {
            int curPos = 0;
            while (curPos < contents.length) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            DataOutputStream dosTmp = new DataOutputStream(bos);
                            dosTmp.writeByte(3);

                            if ((contents.length-curPos) > BODY_CHUNK_MAX_SIZE) {
                                    writeInt(dosTmp, BODY_CHUNK_MAX_SIZE);
                                    dosTmp.write(contents, curPos, BODY_CHUNK_MAX_SIZE);
                                    curPos+=BODY_CHUNK_MAX_SIZE;
                            } else {
                                    writeInt(dosTmp, contents.length-curPos);
                                    dosTmp.write(contents, curPos, contents.length-curPos);
                                    curPos=contents.length;
                            }

                            byte[] bodyPacket = bos.toByteArray();
                            writeResponseHeaderInitial(dos);
                            writeInt(dos, bodyPacket.length);
                            dos.write(bodyPacket);
            }
    }
    
    public static void writeRequestBodyChunk(DataOutputStream dos, int reqBlockLength) throws IOException {
    	writeResponseHeaderInitial(dos);
    	writeInt(dos, 3);
    	dos.writeByte(6);
    	writeInt(dos, reqBlockLength);
    }
    
    public static void writeResponseHeaders(DataOutputStream dos, int responseCode, Headers rspHeaders, String contentType, long contentLength) throws IOException {
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dosTmp = new DataOutputStream(bos);
		dosTmp.writeByte(4);
		writeInt(dosTmp, responseCode);
		writeString(dosTmp, Code.msg(responseCode));
                
                // Number of headers
                int numHeaders = 0;
                if (!rspHeaders.containsKey("Content-Length")) {
                    numHeaders++;
                }
                for (Map.Entry<String, List<String>> entry : rspHeaders.entrySet()) {
                    numHeaders += entry.getValue().size();
                }
		writeInt(dosTmp, numHeaders);
                
                for (Map.Entry<String, List<String>> entry : rspHeaders.entrySet()) {
                    for (String val : entry.getValue()) {
                        writeString(dosTmp, entry.getKey());
                        writeString(dosTmp, val);
                    }
                }
//		//dosTmp.writeByte(0xA001);
//                if (contentType != null && contentType.length() > 0) {
//                    writeString(dosTmp, "Content-Type");
//                    writeString(dosTmp, contentType);
//                }
//		
//		//dosTmp.writeByte(0xA003);
                if (contentLength > 0) {
                    writeString(dosTmp, "Content-Length");
                    writeString(dosTmp, String.valueOf(contentLength));
                }
		
		byte[] headersPacket = bos.toByteArray();
		writeResponseHeaderInitial(dos);
		writeInt(dos, headersPacket.length);
		dos.write(headersPacket);
    }
    
    public static void writeInt(DataOutputStream dos, int val) throws IOException {
    	dos.writeChar(val);
    }
    
    public static void writeString(DataOutputStream dos, String val) throws IOException {
        int len = val == null ? 0 : val.length();
    	writeInt(dos, len);
        if (val != null) {
            dos.writeBytes(val);
        }
    	dos.writeByte(0);
    }
    
    public static void writeResponseHeaderInitial(DataOutputStream dos) throws IOException {
    	dos.writeByte('A');
    	dos.writeByte('B');
    }
    
    public static void writeBoolean(DataOutputStream dos, boolean bool) throws IOException {
    	dos.writeByte(bool ? 1 : 0);
    }
}
