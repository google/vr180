// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.vr180.communication.http;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ServerSocketFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class HttpSocketServerTest {

  private static final int TIMEOUT_MS = 5000;

  @Test
  public void testHttpSocketServer() throws Exception {
    Map<String, HttpRequestHandler> handlerMap = new HashMap<String, HttpRequestHandler>();
    handlerMap.put("/", new HelloWorldHandler());
    HttpService httpService = HttpServiceFactory.constructHttpService(handlerMap);
    CompletableFuture<Boolean> finished = new CompletableFuture<Boolean>();
    TestHttpSocketServer httpSocketServer = new TestHttpSocketServer(httpService, finished);
    finished.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    String result = new String(httpSocketServer.getOutputData(), StandardCharsets.UTF_8);
    assertThat(result).contains("hello world");
  }

  @Test
  public void testClientCloseConnection() throws Exception {
    HttpService mockHttpService = mock(HttpService.class);
    doThrow(new ConnectionClosedException("client close"))
        .when(mockHttpService)
        .handleRequest(any(), any());
    when(mockHttpService.getParams()).thenReturn(mock(HttpParams.class));
    CompletableFuture<Boolean> finished = new CompletableFuture<Boolean>();
    TestHttpSocketServer httpSocketServer = new TestHttpSocketServer(mockHttpService, finished);
    finished.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    // Check that it tried to call accept again.
    assertThat(httpSocketServer.getAcceptCallsCount()).isGreaterThan(1);
  }

  private static class HelloWorldHandler implements HttpRequestHandler {
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
      BasicHttpEntity entity = new BasicHttpEntity();
      InputStream stream = new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8));
      entity.setContentType("text/plain");
      entity.setContent(stream);
      response.setEntity(entity);
    }
  }

  private static class TestHttpSocketServer extends HttpSocketServer {
    private final CompletableFuture<Boolean> finished;
    private final ByteArrayOutputStream byteArrayOutputStream = new SocketOutputStream();
    private int acceptCalls = 0;

    public TestHttpSocketServer(HttpService httpService, CompletableFuture<Boolean> finished)
        throws Exception {
      super(RuntimeEnvironment.application, httpService, mock(ServerSocketFactory.class));
      startServer();
      this.finished = finished;
    }

    public byte[] getOutputData() {
      return byteArrayOutputStream.toByteArray();
    }

    public int getAcceptCallsCount() {
      return acceptCalls;
    }

    @Override
    protected void startServerSocket() throws IOException {
      // Do nothing.
    }

    @Override
    protected Socket acceptSocket() throws IOException {
      ++acceptCalls;
      if (acceptCalls == 1) {
        final Socket mockSocket = mock(Socket.class);
        byte[] requestBytes =
            "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(requestBytes);
        when(mockSocket.getInputStream()).thenReturn(byteArrayInputStream);
        when(mockSocket.getOutputStream()).thenReturn(byteArrayOutputStream);
        when(mockSocket.getInetAddress()).thenReturn(InetAddress.getLocalHost());
        doAnswer(
                new Answer<Void>() {
                  @Override
                  public Void answer(InvocationOnMock invocation) throws Throwable {
                    finished.complete(true);
                    return null;
                  }
                })
            .when(mockSocket)
            .close();
        return mockSocket;
      }

      // Wait before returning an error accepting the connection (so the previously handled socket
      // can be processed).
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        // Ignore.
      }
      throw new IOException("Only one socket accepted");
    }

    /** An extended output stream that notifies when the response is written. */
    private class SocketOutputStream extends ByteArrayOutputStream {
      @Override
      public synchronized void write(byte[] b, int off, int len) {
        super.write(b, off, len);
        if (size() > 0) {
          finished.complete(true);
        }
      }
    }
  }
}
