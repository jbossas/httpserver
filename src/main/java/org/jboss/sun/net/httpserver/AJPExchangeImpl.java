package org.jboss.sun.net.httpserver;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpPrincipal;

class AJPExchangeImpl extends HttpExchange {

    ExchangeImpl impl;
    InputStream rawIn;
    OutputStream rawOut;
    AJPInputStream ajpInputStream;
    AJPOutputStream ajpOutputStream;
    boolean writefinished = false;

    AJPExchangeImpl (ExchangeImpl impl, InputStream rawIn, OutputStream rawOut) {
        this.impl = impl;
        this.rawIn = rawIn;
        this.rawOut = rawOut;
    }

    public Headers getRequestHeaders () {
        return impl.getRequestHeaders();
    }

    public Headers getResponseHeaders () {
        return impl.getResponseHeaders();
    }

    public URI getRequestURI () {
        return impl.getRequestURI();
    }

    public String getRequestMethod (){
        return impl.getRequestMethod();
    }

    public HttpContextImpl getHttpContext (){
        return impl.getHttpContext();
    }

    public void close () {
        try {
            ajpOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        impl.close();
    }

    public int getResponseCode () {
        return impl.getResponseCode();
    }

    public InputStream getRequestBody () {
        if (ajpInputStream == null) {
            ajpInputStream = new AJPInputStream(impl, rawIn, rawOut);
        }
        return ajpInputStream;
    }

    public AJPOutputStream getResponseBody () {
    	if (ajpOutputStream == null) {
            ajpOutputStream = new AJPOutputStream(this, rawOut);
    	}
    	return ajpOutputStream;
    }

    public void sendResponseHeaders (int rCode, long contentLen)
    throws IOException
    {
    	PlaceholderOutputStream o = impl.getPlaceholderResponseBody();
    	o.setWrappedStream(impl.ros);
    	DataOutputStream dos = new DataOutputStream(getResponseBody().getFilteredOutputStream());
    	AJPUtil.writeResponseHeaders(dos, rCode, impl.rspHdrs, impl.rspHdrs.getFirst("Content-Type"), contentLen);
    }

    public InetSocketAddress getRemoteAddress (){
        return impl.getRemoteAddress();
    }

    public InetSocketAddress getLocalAddress (){
        return impl.getLocalAddress();
    }

    public String getProtocol (){
        return impl.getProtocol();
    }

    public Object getAttribute (String name) {
        return impl.getAttribute (name);
    }

    public Object getAttribute(String name, HttpExchange.AttributeScope scope) {
        return impl.getAttribute(name, scope);
    }

    public void setAttribute (String name, Object value) {
        impl.setAttribute (name, value);
    }

    public void setAttribute(String name, Object value, HttpExchange.AttributeScope scope) {
        impl.setAttribute(name, value, scope);
    }


    public void setStreams (InputStream i, OutputStream o) {
        impl.setStreams (i, o);
    }

    public HttpPrincipal getPrincipal () {
        return impl.getPrincipal();
    }

    ExchangeImpl getExchangeImpl () {
        return impl;
    }
}
