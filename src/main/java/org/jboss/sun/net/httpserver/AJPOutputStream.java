package org.jboss.sun.net.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AJPOutputStream extends FilterOutputStream {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	
	public AJPOutputStream(OutputStream os) {
		super(os);
	}
	
	OutputStream getFilteredOutputStream() {
		return out;
	}
	@Override
	public void close() throws IOException {
		byte[] buffContents = bos.toByteArray();
		DataOutputStream dos = new DataOutputStream(out);
		AJPUtil.writeBody(dos, buffContents);
                AJPUtil.writeEndResponse(dos);
		bos.reset();
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		bos.write(b);
	}
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		bos.write(b, off, len);
		byte[] newBuff = new byte[len];
		System.arraycopy(b, off, newBuff, 0, len);
	}
	@Override
	public void write(int b) throws IOException {
		bos.write(b);
	}
}
