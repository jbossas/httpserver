package org.jboss.sun.net.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class AJPUtil {
	public static final int REQUEST_BLOCK_LENGTH = 8186;
    public static final int BODY_CHUNK_MAX_SIZE = 8189;
                
	public static void writeEndResponse(DataOutputStream dos) throws IOException {
		writeResponseHeaderInitial(dos);
		dos.writeByte(0);
		dos.writeByte(2);
		dos.writeByte(5);
		writeBoolean(dos, false); // write keep-alive
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
    
    public static void writeResponseHeaders(DataOutputStream dos, String contentType, long contentLength) throws IOException {
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dosTmp = new DataOutputStream(bos);
		dosTmp.writeByte(4);
		writeInt(dosTmp, 200);
		writeString(dosTmp, "OK");
		writeInt(dosTmp, 2);
		
		//dosTmp.writeByte(0xA001);
		writeString(dosTmp, "Content-Type");
		writeString(dosTmp, contentType);
		
		//dosTmp.writeByte(0xA003);
		writeString(dosTmp, "Content-Length");
		writeString(dosTmp, String.valueOf(contentLength));
		
		byte[] headersPacket = bos.toByteArray();
		writeResponseHeaderInitial(dos);
		writeInt(dos, headersPacket.length);
		dos.write(headersPacket);
    }
    
    public static void writeInt(DataOutputStream dos, int val) throws IOException {
    	dos.writeChar(val);
    }
    
    public static void writeString(DataOutputStream dos, String val) throws IOException {
    	writeInt(dos, val.length());
    	dos.writeBytes(val);
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
