/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.ajp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Efficient conversion of bytes  to character .
 *
 *  This uses the standard JDK mechanism - a reader - but provides mechanisms
 *  to recycle all the objects that are used. It is compatible with JDK1.1
 *  and up,
 *  ( nio is better, but it's not available even in 1.2 or 1.3 )
 *
 *  Not used in the current code, the performance gain is not very big
 *  in the current case ( since String is created anyway ), but it will
 *  be used in a later version or after the remaining optimizations.
 */
public class B2CConverter {

    private static final Map<String, Charset> encodingToCharsetCache =
        new HashMap<String, Charset>();

    public static final Charset ISO_8859_1;
    public static final Charset UTF_8;

    static {
        for (Charset charset: Charset.availableCharsets().values()) {
            encodingToCharsetCache.put(
                    charset.name().toLowerCase(Locale.US), charset);
            for (String alias : charset.aliases()) {
                encodingToCharsetCache.put(
                        alias.toLowerCase(Locale.US), charset);
            }
        }
        Charset iso88591 = null;
        Charset utf8 = null;
        try {
            iso88591 = getCharset("ISO-8859-1");
            utf8 = getCharset("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Impossible. All JVMs must support these.
            e.printStackTrace();
        }
        ISO_8859_1 = iso88591;
        UTF_8 = utf8;
    }

    public static Charset getCharset(String enc)
            throws UnsupportedEncodingException{

        // Encoding names should all be ASCII
        String lowerCaseEnc = enc.toLowerCase(Locale.US);

        Charset charset = encodingToCharsetCache.get(lowerCaseEnc);

        if (charset == null) {
            // Pre-population of the cache means this must be invalid
            throw new UnsupportedEncodingException(
                    "Unknown encoding: " + enc);
        }
        return charset;
    }

    private IntermediateInputStream iis;
    private ReadConvertor conv;
    private String encoding;

    /**
     * @deprecated Unused. Will be removed in Tomcat 8.0.x onwards.
     */
    @Deprecated
    protected B2CConverter() {
    }

    /** Create a converter, with bytes going to a byte buffer
     */
    public B2CConverter(String encoding)
        throws IOException
    {
        this.encoding=encoding;
        reset();
    }


    /** Reset the internal state, empty the buffers.
     *  The encoding remain in effect, the internal buffers remain allocated.
     */
    public  void recycle() {
        conv.recycle();
    }

    static final int BUFFER_SIZE=8192;
    char result[]=new char[BUFFER_SIZE];

    public void reset()
        throws IOException
    {
        // destroy the reader/iis
        iis=new IntermediateInputStream();
        conv = new ReadConvertor(iis, getCharset(encoding));
    }

}

// -------------------- Private implementation --------------------



/**
 *
 */
final class  ReadConvertor extends InputStreamReader {

    /** Create a converter.
     */
    public ReadConvertor(IntermediateInputStream in, Charset charset) {
        super(in, charset);
    }

    /** Overridden - will do nothing but reset internal state.
     */
    @Override
    public  final void close() throws IOException {
        // NOTHING
        // Calling super.close() would reset out and cb.
    }

    @Override
    public  final int read(char cbuf[], int off, int len)
        throws IOException
    {
        // will do the conversion and call write on the output stream
        return super.read( cbuf, off, len );
    }

    /** Reset the buffer
     */
    public  final void recycle() {
        try {
            // Must clear super's buffer.
            while (ready()) {
                // InputStreamReader#skip(long) will allocate buffer to skip.
                read();
            }
        } catch(IOException ioe){
        }
    }
}


/** Special output stream where close() is overridden, so super.close()
    is never called.

    This allows recycling. It can also be disabled, so callbacks will
    not be called if recycling the converter and if data was not flushed.
*/
final class IntermediateInputStream extends InputStream {
    ByteChunk bc = null;

    public IntermediateInputStream() {
    }

    @Override
    public  final void close() throws IOException {
        // shouldn't be called - we filter it out in writer
        throw new IOException("close() called - shouldn't happen ");
    }

    @Override
    public  final  int read(byte cbuf[], int off, int len) throws IOException {
        return bc.substract(cbuf, off, len);
    }

    @Override
    public  final int read() throws IOException {
        return bc.substract();
    }

    // -------------------- Internal methods --------------------


    void setByteChunk( ByteChunk mb ) {
        bc = mb;
    }

}
