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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import com.google.vr180.common.IoUtils;
import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.concurrent.GuardedBy;
import javax.net.ServerSocketFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpService;

/**
 * HttpsSocketServer opens a server socket on port 8443 and accpets client requests, which are
 * handled by the provided Apache HttpService.
 */
public class HttpSocketServer implements Runnable {
  /** Port hosting the server. */
  public static final int PORT = 8443;

  private static final String TAG = "HttpSocketServer";

  private final Context context;
  private final HttpService httpService;
  private final ServerSocketFactory serverSocketFactory;
  private final ExecutorService executorThreadPool;
  private final WifiStateReceiver wifiStateReceiver;

  @GuardedBy("this")
  private Thread listenThread;

  private volatile boolean stopped = true;
  private ServerSocket serverSocket;

  /** Constructs a new HTTP server that hosts the provided HttpService instance. */
  public HttpSocketServer(
      Context context, HttpService httpService, ServerSocketFactory serverSocketFactory) {
    this.context = context;
    this.httpService = httpService;
    this.serverSocketFactory = serverSocketFactory;
    executorThreadPool = Executors.newCachedThreadPool();
    wifiStateReceiver = new WifiStateReceiver();
  }

  /** Runs the listener thread. */
  @Override
  public void run() {
    try {
      startServerSocket();
    } catch (IOException e) {
      Log.e(TAG, "Error opening HTTP server socket", e);
      synchronized (this) {
        // Set the listenThread to null so we'll start again.
        listenThread = null;
      }
      return;
    }

    try {
      while (!stopped) {
        Log.d(TAG, "Accepting connection...");
        Socket socket = acceptSocket();
        Log.d(TAG, "Connection to " + socket.getInetAddress() + " is accepted");
        executorThreadPool.execute(() -> handleSocket(socket));
      }
    } catch (IOException e) {
      Log.d(TAG, "The process was interrupted");
    } finally {
      IoUtils.closeSilently(serverSocket);
    }

    Log.d(TAG, "HttpSocketServer stopped.");
  }

  public synchronized void startServer() {
    if (stopped) {
      Log.d(TAG, "Starting the server");
      stopped = false;
      startIfNotRunning();
      context.registerReceiver(
          wifiStateReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    }
  }

  /** Close the server and stop listening for connections. */
  public synchronized void stopServer() {
    if (!stopped) {
      Log.d(TAG, "Stopping the server");
      stopped = true;
      context.unregisterReceiver(wifiStateReceiver);
      IoUtils.closeSilently(serverSocket);
      listenThread = null;
    }
  }

  private void handleSocket(Socket socket) {
    DefaultHttpServerConnection serverConnection = null;
    try {
      serverConnection = new DefaultHttpServerConnection();
      serverConnection.bind(socket, httpService.getParams());
      while (serverConnection.isOpen()) {
        try {
          httpService.handleRequest(serverConnection, new BasicHttpContext(null));
        } catch (ConnectionClosedException ex) {
          Log.d(TAG, "Connection closed", ex);
          return;
        } catch (HttpException e) {
          Log.e(TAG, "Error handling request", e);
          return;
        }
      }
    } catch (IOException ioe) {
      Log.e(TAG, "Error handling socket", ioe);
    } finally {
      closeSilently(serverConnection);
      IoUtils.closeSilently(socket);
    }
  }

  private static void closeSilently(DefaultHttpServerConnection connection) {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (IOException e) {
      // Ignore.
    }
  }

  /** A helper method to start listening (overridden in the test). */
  protected void startServerSocket() throws IOException {
    serverSocket = serverSocketFactory.createServerSocket(PORT);
  }

  /** A helper method to accept a socket (overridden in the test). */
  protected Socket acceptSocket() throws IOException {
    return serverSocket.accept();
  }

  /** Starts a new listener thread if one is not running. */
  private synchronized void startIfNotRunning() {
    if (listenThread != null) {
      return;
    }

    listenThread = new Thread(this, "HttpSocketServer Listen Thread");
    listenThread.start();
  }

  private class WifiStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      // Connection state changed, try starting the listening thread again.
      startIfNotRunning();
    }
  }
}
