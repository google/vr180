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

package com.google.vr180.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraInternalApi.CameraInternalApiRequest;
import com.google.vr180.CameraInternalApi.CameraInternalApiResponse;
import com.google.vr180.CameraInternalApi.CameraInternalStatus;
import com.google.vr180.CameraInternalApi.CameraState;
import com.google.vr180.api.CameraApiHandler;
import com.google.vr180.api.CameraCore;
import com.google.vr180.api.camerainterfaces.CameraInterfaceFactory;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.api.camerainterfaces.SslManager;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.implementations.AndroidStorageStatusProvider;
import com.google.vr180.api.implementations.BroadcastStatusNotifier;
import com.google.vr180.api.implementations.SelfSignedSslManager;
import com.google.vr180.api.implementations.Settings;
import com.google.vr180.api.implementations.StatusNotificationChannel;
import com.google.vr180.api.internal.CameraInternalApiHandler;
import com.google.vr180.api.internal.CameraInternalStatusManager;
import com.google.vr180.capture.CaptureManagerImpl;
import com.google.vr180.capture.camera.CameraConfigurator;
import com.google.vr180.capture.camera.PreviewConfigProvider;
import com.google.vr180.common.InstanceMap;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.common.communication.BluetoothManufacturerDataHelper;
import com.google.vr180.common.crypto.CryptoUtilities;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.logging.MemoryLogger;
import com.google.vr180.communication.bluetooth.BluetoothApiHandler;
import com.google.vr180.communication.bluetooth.BluetoothPairingManager;
import com.google.vr180.communication.bluetooth.BluetoothSocketService;
import com.google.vr180.communication.bluetooth.gatt.BluetoothGattServerHelper;
import com.google.vr180.communication.http.AuthorizationValidator;
import com.google.vr180.communication.http.HttpCameraApiHandler;
import com.google.vr180.communication.http.HttpServiceFactory;
import com.google.vr180.communication.http.HttpSocketServer;
import com.google.vr180.communication.http.MediaDownloadHandler;
import com.google.vr180.device.DeviceInfo;
import com.google.vr180.media.metadata.ProjectionMetadataProvider;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.webrtc.PeerConnectionFactory;

/**
 * CameraController manages starting up the camera's services and connecting together the interface
 * implementations.
 */
public class CameraController implements CameraCore {
  private static final String TAG = "CameraController";
  private static final String MEDIA_FOLDER_RELATIVE_PATH = "/DCIM/VR180/";
  private static final String CAMERA_API_URL = "/daydreamcamera";
  private static final String WIFI_DIRECT_FIELD_TRIAL = "IncludeWifiDirect/Enabled/";
  private static final int SHUTDOWN_PERCENTAGE = 5;

  private final CompositeDisposable disposables = new CompositeDisposable();

  private final Context context;
  private final WifiManager wifiManager;

  /** Notification channel for connecting the status notifier to the api handler. */
  private final StatusNotificationChannel notificationChannel;

  private final StatusNotificationChannel internalStatusNotifier;

  private final CaptureManagerImpl captureManager;
  private final CameraInterfaceFactory interfaceFactory;
  private final CameraApiHandler apiHandler;
  private final CameraInternalApiHandler cameraInternalApiHandler;

  private final BluetoothGattServerHelper bluetoothGattServerHelper;
  private final BluetoothSocketService cameraApiService;
  private final BluetoothPairingManager bluetoothPairingManager;
  private final HttpSocketServer httpSocketServer;
  private final CameraSettings cameraSettings;

  /** Listener for camera core api endpoint status */
  private CameraCoreListener cameraCoreListener;

  public CameraController(Context context) {
    this.context = context;
    wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

    // Register a memory logger to provide debug logs.
    MemoryLogger logger = new MemoryLogger();
    Log.addLogger(logger);
    CapabilitiesProvider capabilitiesProvider = InstanceMap.get(CapabilitiesProvider.class);
    cameraSettings = new Settings(context, capabilitiesProvider.getCapabilities());
    CameraInternalStatusManager cameraInternalStatusManager = new CameraInternalStatusManager();
    SslManager sslManager = SelfSignedSslManager.create();

    notificationChannel = new StatusNotificationChannel();

    // Set up bluetooth server and bluetooth pairing service.
    DeviceInfo deviceInfo = InstanceMap.get(DeviceInfo.class);
    bluetoothGattServerHelper =
        new BluetoothGattServerHelper(context, cameraInternalStatusManager::onConnectionsChanged);
    bluetoothPairingManager =
        new BluetoothPairingManager(
            bluetoothGattServerHelper, cameraSettings, deviceInfo.getBluetoothManufacturerId());
    bluetoothPairingManager.addPairingStatusListener(
        cameraInternalStatusManager::onPairingStatusChanged);
    cameraInternalApiHandler =
        new CameraInternalApiHandler(bluetoothPairingManager, cameraInternalStatusManager);

    // Set up the API handler and bluetooth API service.
    StorageStatusProvider storageStatusProvider =
        new AndroidStorageStatusProvider(context, MEDIA_FOLDER_RELATIVE_PATH, notificationChannel);
    captureManager =
        new CaptureManagerImpl(
            context,
            cameraSettings,
            storageStatusProvider,
            notificationChannel,
            InstanceMap.get(ProjectionMetadataProvider.class),
            InstanceMap.get(PreviewConfigProvider.class),
            InstanceMap.get(CameraConfigurator.class),
            InstanceMap.get(DeviceInfo.class),
            () -> {
              if (cameraCoreListener != null) {
                cameraCoreListener.onInternalError();
              }
            });
    interfaceFactory =
        new CameraInterfaceFactoryImpl(
            context,
            capabilitiesProvider,
            captureManager,
            storageStatusProvider,
            logger,
            sslManager,
            captureManager.getViewfinderCaptureSource(),
            cameraSettings,
            notificationChannel,
            bluetoothPairingManager,
            SHUTDOWN_PERCENTAGE,
            HttpSocketServer.PORT);
    apiHandler = new CameraApiHandler(interfaceFactory);
    cameraApiService =
        new BluetoothSocketService(
            bluetoothGattServerHelper,
            BluetoothConstants.CAMERA_SERVICE_UUID,
            deviceInfo.getBluetoothManufacturerId(),
            getManufacturerData(),
            new BluetoothApiHandler(cameraSettings, apiHandler));

    // Set up the HTTP API server.
    httpSocketServer =
        createHttpsServer(sslManager, apiHandler, interfaceFactory.getFileProvider());

    // Set up notification channels
    notificationChannel.addStatusNotifier(
        new BroadcastStatusNotifier(context, BroadcastStatusNotifier.STATUS_UPDATE_ACTION));
    notificationChannel.addStatusNotifier(
        () -> {
          if (cameraCoreListener != null) {
            cameraCoreListener.onStatusChanged();
          }
        });
    notificationChannel.addStatusNotifier(cameraApiService::notifyStatusChanged);
    internalStatusNotifier = new StatusNotificationChannel();
    internalStatusNotifier.addStatusNotifier(
        new BroadcastStatusNotifier(
            context, BroadcastStatusNotifier.INTERNAL_STATUS_UPDATE_ACTION));
    internalStatusNotifier.addStatusNotifier(
        () -> {
          if (cameraCoreListener != null) {
            cameraCoreListener.onInternalStatusChanged();
          }
        });

    // Monitor the camera internal status and notify listeners.
    disposables.add(
        cameraInternalStatusManager
            .getCameraInternalStatusObservable()
            .subscribe(status -> internalStatusNotifier.notifyStatusChanged()));

    // Monitor the camera (sleep) state and notify listeners.
    disposables.add(
        cameraInternalStatusManager
            .getCameraInternalStatusObservable()
            .map(CameraInternalStatus::getCameraState)
            .distinctUntilChanged()
            .skipWhile(state -> state != CameraState.INACTIVE) // Ignore the initial INACTIVE state
            .subscribe(this::configureCameraState));

    // Monitor the Bluetooth state and enable/disable the bluetooth services accordingly.
    disposables.add(
        getBluetoothEnabledObservable()
            .subscribeOn(Schedulers.io())
            .subscribe(this::enableBluetoothServer));

    // Monitor the WiFi state and enable/disable the HTTP server accordingly
    disposables.add(
        getWifiEnabledObservable().subscribeOn(Schedulers.io()).subscribe(this::enableHttpServer));

    // Initialize WebRTC
    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials(WIFI_DIRECT_FIELD_TRIAL)
            .createInitializationOptions());
  }

  /** Handle API request. */
  @Override
  public CameraApiResponse doRequest(CameraApiRequest request) {
    return apiHandler.handleRequest(request);
  }

  /** Handle internal API request. */
  @Override
  public CameraInternalApiResponse doInternalRequest(CameraInternalApiRequest request) {
    return cameraInternalApiHandler.handleInternalRequest(request);
  }

  /** Set a GLSurfaceView as viewfinder. */
  @Override
  public void setViewfinderView(GLSurfaceView viewfinderView) {
    captureManager.setCaptureView(viewfinderView);
  }

  /** Sets the listener for camera status changes. */
  @Override
  public void setListener(CameraCoreListener listener) {
    this.cameraCoreListener = listener;
  }

  private void onResume() {
    captureManager.onResume();
    interfaceFactory.getWakeManager().setEnabled(true);
  }

  private void onPause() {
    captureManager.onPause();
    interfaceFactory.getWakeManager().setEnabled(false);
  }

  private void configureCameraState(CameraState cameraState) {
    Log.d(TAG, "Changing camera state " + cameraState);
    switch (cameraState) {
      case DEFAULT_ACTIVE:
        onResume();
        break;
      case INACTIVE:
        onPause();
        break;
    }
  }

  private void enableHttpServer(boolean enabled) {
    Log.d(TAG, "enableHttpServer " + enabled);
    if (enabled) {
      httpSocketServer.startServer();
      notificationChannel.notifyStatusChanged();
    } else {
      httpSocketServer.stopServer();
      notificationChannel.notifyStatusChanged();
    }
  }

  // This should be called only when Bluetooth is enabled or disabled. Otherwise, if a connection
  // exists when the GATT services are added or removed, it might cause unexpected connection
  // interruption because many Android devices does not handle GATT service changed notification.
  private void enableBluetoothServer(boolean enabled) {
    Log.d(TAG, "enableBluetoothServer " + enabled);
    if (enabled) {
      bluetoothGattServerHelper.open();
      bluetoothPairingManager.open();
      cameraApiService.start();
      AsyncTask.execute(cameraApiService::enable);
    } else {
      cameraApiService.stop();
      bluetoothPairingManager.close();
      bluetoothGattServerHelper.close();
    }
  }

  private byte[] getManufacturerData() {
    // Check if we have a key pair, and generate one if needed.
    KeyPair localKeyPair = cameraSettings.getLocalKeyPair();
    if (localKeyPair == null) {
      try {
        localKeyPair = CryptoUtilities.generateECDHKeyPair();
      } catch (CryptoUtilities.CryptoException e) {
        throw new RuntimeException("Unable to generate local key pair", e);
      }
      cameraSettings.setLocalKeyPair(localKeyPair);
    }

    try {
      return BluetoothManufacturerDataHelper.generateManufacturerData(
          CryptoUtilities.convertECDHPublicKeyToBytes(localKeyPair.getPublic()));
    } catch (CryptoUtilities.CryptoException e) {
      throw new RuntimeException("Unable to construct manufacterer data.", e);
    }
  }

  private HttpSocketServer createHttpsServer(
      SslManager sslManager, CameraApiHandler apiHandler, FileProvider fileProvider) {
    AuthorizationValidator authValidator = new AuthorizationValidator(cameraSettings);
    Map<String, HttpRequestHandler> handlerMap = new HashMap<String, HttpRequestHandler>();
    handlerMap.put(CAMERA_API_URL, new HttpCameraApiHandler(apiHandler, authValidator));
    handlerMap.put(
        MediaDownloadHandler.PREFIX + "*", new MediaDownloadHandler(fileProvider, authValidator));
    HttpService httpService = HttpServiceFactory.constructHttpService(handlerMap);
    return new HttpSocketServer(context, httpService, sslManager.getServerSocketFactory());
  }

  private Observable<Boolean> getBluetoothEnabledObservable() {
    return BroadcastReceiverObservable.create(
            context, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        .map(
            intent ->
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    == BluetoothAdapter.STATE_ON)
        .startWith(BluetoothAdapter.getDefaultAdapter().isEnabled())
        .distinctUntilChanged()
        .skipWhile(enabled -> !enabled); // Ignore the initial false value
  }

  private Observable<Boolean> getWifiEnabledObservable() {
    return BroadcastReceiverObservable.create(
            context, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        .map(
            intent ->
                intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    == WifiManager.WIFI_STATE_ENABLED)
        .startWith(wifiManager.isWifiEnabled())
        .distinctUntilChanged()
        .skipWhile(enabled -> !enabled); // Ignore the initial false value
  }
}
