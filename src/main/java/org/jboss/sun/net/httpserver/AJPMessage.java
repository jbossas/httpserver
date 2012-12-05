package org.jboss.sun.net.httpserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.jboss.com.sun.net.httpserver.Headers;

public class AJPMessage {

    public static final byte REQ_ATTRIBUTE_CONTEXT = 1;  // Unused
    public static final byte REQ_ATTRIBUTE_SERVLET_PATH = 2;  // Unused
    public static final byte REQ_ATTRIBUTE_REMOTE_USER = 3;
    public static final byte REQ_ATTRIBUTE_AUTH_TYPE = 4;
    public static final byte REQ_ATTRIBUTE_QUERY_STRING = 5;
    public static final byte REQ_ATTRIBUTE_JVM_ROUTE = 6;
    public static final byte REQ_ATTRIBUTE_SSL_CERT = 7;
    public static final byte REQ_ATTRIBUTE_SSL_CIPHER = 8;
    public static final byte REQ_ATTRIBUTE_SSL_SESSION = 9;
    public static final byte REQ_ATTRIBUTE_SSL_KEYSIZE = 11;
    public static final byte REQ_ATTRIBUTE_SECRET = 12;
    public static final byte REQ_ATTRIBUTE_STORED_METHOD = 13;
    private static final String[] predefinedRequestMethods = {
        "OPTIONS",
        "GET",
        "HEAD",
        "POST",
        "PUT",
        "DELETE",
        "TRACE",
        "PROPFIND",
        "PROPPATCH",
        "MKCOL",
        "COPY",
        "MOVE",
        "LOCK",
        "UNLOCK",
        "ACL",
        "REPORT",
        "VERSION-CONTROL",
        "CHECKIN",
        "CHECKOUT",
        "UNCHECKOUT",
        "SEARCH",
        "MKWORKSPACE",
        "UPDATE",
        "LABEL",
        "MERGE",
        "BASELINE_CONTROL",
        "MKACTIVITY"
    };
    private static final String[] predefinedHeaders = {
        "accept",
        "accept-charset",
        "accept-encoding",
        "accept-language",
        "authorization",
        "connection",
        "content-type",
        "content-length",
        "cookie",
        "cookie2",
        "host",
        "pragma",
        "referer",
        "user-agent"
    };
    private DataInputStream dis;
    private boolean toJava;
    private Integer requestType;
    private int packetLen;

    public void startRead(DataInputStream dis) {
        this.dis = dis;
        toJava = true;
        requestType = null;
        packetLen = 0;
    }

    public int readType() throws IOException {
        byte byte1 = dis.readByte();
        byte byte2 = dis.readByte();
        if (byte1 != 18 || byte2 != 52) {
            throw new RuntimeException("Unexpected bytes!");
        }
        this.packetLen = readIntUnsigned(dis);
        this.requestType = (int) dis.readByte();
        return this.requestType;
    }

    public String readFirstRequestLine() throws IOException {
        byte requestMethod = dis.readByte();
        String requestMethodStr = predefinedRequestMethods[requestMethod - 1];
        String proto = readString(dis);
        String reqUri = readString(dis);
        return requestMethodStr + " " + reqUri + " " + proto;
    }

    public Headers readHeaders() throws IOException {
        Headers headers = new Headers();
        String remoteAddr = readString(dis);
        String remoteHost = readString(dis);
        String serverName = readString(dis);
        int serverPort = readIntUnsigned(dis);
        boolean ssl = readBoolean(dis);
        int numHeaders = readIntUnsigned(dis);

        for (int i = 0; i < numHeaders; i++) {
            int headerFirstInt = readIntUnsigned(dis);
            int headerType = headerFirstInt;
            headerType &= 0xFF00;
            if (0xA000 == headerType) {
                int headerID = headerFirstInt & 0xFF;
                String header = predefinedHeaders[headerID - 1];
                String headerValue = readString(dis);
                headers.add(header, headerValue);
            } else {
                String header = readString(dis, headerFirstInt);
                String headerValue = readString(dis);
                headers.add(header, headerValue);
            }
        }
        //String contentLengthStr = headers.get("content-length");
        //long contentLength = -1;
        //if (contentLengthStr != null) {
        //try {
        //contentLength = Long.parseLong(contentLengthStr);
        //} catch (Exception e) {
        //System.out.println("Failed to parse content length");
        //}
        //}

        // read attributes
        byte attType;
        while ((attType = dis.readByte()) != -1) {
            if (attType == 0x0A) {
                String name = readString(dis);
                String val = readString(dis);
                System.out.println("Nm: " + name + ", val: " + val);
            } else {
                String val = readString(dis);
                System.out.println("Val: " + val);
            }
        }
        return headers;
    }

    public byte[] readIncomingBody(DataInputStream is) throws IOException {
        byte byte1 = dis.readByte();
        byte byte2 = dis.readByte();
        if (byte1 != 18 || byte2 != 52) {
            throw new RuntimeException("Unexpected bytes!");
        }
        this.packetLen = readIntUnsigned(dis);
        this.requestType = (int) dis.readByte();
        dis.readByte();
        
        byte[] buff = new byte[packetLen - 2];
        for (int i = 0; i < buff.length; i++) {
            byte b = dis.readByte();
            buff[i] = b;
        }
        return buff;
    }
    
    public void readIncomingMessage(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(is);
        byte byte1 = dis.readByte();
        byte byte2 = dis.readByte();
        if (byte1 != 18 || byte2 != 52) {
            throw new RuntimeException("Unexpected bytes!");
        }
        this.packetLen = readIntUnsigned(dis);
        this.requestType = (int) dis.readByte();
        if (this.requestType == 2) {
            byte requestMethod = dis.readByte();
            String proto = readString(dis);
            String reqUri = readString(dis);
            String remoteAddr = readString(dis);
            String remoteHost = readString(dis);
            String serverName = readString(dis);
            int serverPort = readIntUnsigned(dis);
            boolean ssl = readBoolean(dis);
            int numHeaders = readIntUnsigned(dis);

            Map<String, String> headerMap = new HashMap<String, String>();
            for (int i = 0; i < numHeaders; i++) {
                int headerFirstInt = readIntUnsigned(dis);
                int headerType = headerFirstInt;
                headerType &= 0xFF00;
                if (0xA000 == headerType) {
                    int headerID = headerFirstInt & 0xFF;
                    String header = predefinedHeaders[headerID - 1];
                    String headerValue = readString(dis);
                    headerMap.put(header.toLowerCase(), headerValue);
                } else {
                    String header = readString(dis, headerFirstInt);
                    String headerValue = readString(dis);
                    headerMap.put(header.toLowerCase(), headerValue);
                }
            }
            String contentLengthStr = headerMap.get("content-length");
            long contentLength = -1;
            if (contentLengthStr != null) {
                try {
                    contentLength = Long.parseLong(contentLengthStr);
                } catch (Exception e) {
                    System.out.println("Failed to parse content length");
                }
            }

            // read attributes
            byte attType;
            while ((attType = dis.readByte()) != -1) {
                if (attType == 0x0A) {
                    String name = readString(dis);
                    String val = readString(dis);
                    System.out.println("Nm: " + name + ", val: " + val);
                } else {
                    String val = readString(dis);
                    System.out.println("Val: " + val);
                }
            }
        }
    }

    private static void writeInt(DataOutputStream dos, int val) throws IOException {
        dos.writeChar(val);
    }

    private static void writeString(DataOutputStream dos, String val) throws IOException {
        writeInt(dos, val.length());
        dos.writeBytes(val);
        dos.writeByte(0);
    }

    private static void writeResponseHeaderInitial(DataOutputStream dos) throws IOException {
        dos.writeByte('A');
        dos.writeByte('B');
    }

    private static void writeBoolean(DataOutputStream dos, boolean bool) throws IOException {
        dos.writeByte(bool ? 1 : 0);
    }

    private static boolean readBoolean(DataInputStream dis) throws IOException {
        return dis.readBoolean();
    }

    private static String readString(DataInputStream dis) throws IOException {
        int strLen = readIntUnsigned(dis);
        return readString(dis, strLen);
    }

    private static String readString(DataInputStream dis, int strLen) throws IOException {
        if (strLen == 65535) {
            return "";
        } else {
            byte[] strBytes = new byte[strLen];
            for (int i = 0; i < strLen; i++) {
                strBytes[i] = dis.readByte();
            }
            // read the null terminator
            dis.readByte();
            return new String(strBytes);
        }
    }

    private static int readIntUnsigned(DataInputStream dis) throws IOException {
        short signedShort = dis.readShort();
        int intVal = signedShort >= 0 ? signedShort : 0x10000 + signedShort;
        return intVal;
    }
}
