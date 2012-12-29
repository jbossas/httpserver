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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;

public class AJPTestWebRequestHandler implements HttpHandler {

    public static final String RESPONSE_TEXT = "<html><body><p>Hello World</p></body></html>";
    
    public void handle(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        Headers map = exchange.getRequestHeaders();
        Headers rmap = exchange.getResponseHeaders();
        
        byte[] buff = new byte[16536];
        int bRead;
        while ( (bRead = is.read(buff, 0, buff.length)) != -1);
        is.close();
        
        exchange.sendResponseHeaders (200, RESPONSE_TEXT.length());
        
        OutputStream responseBody = exchange.getResponseBody();
        Writer responseWriter = new OutputStreamWriter(responseBody);
        PrintWriter out = new PrintWriter(responseWriter);
        out.print(RESPONSE_TEXT);
        out.flush();
        out.close();
    }
}
