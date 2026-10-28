package com.screenbacon.viewer;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.http.SslError;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

import connection.AndroidBridge;
import connection.UdpServerScanner;
import idsParser.IdsParser;
import protocol.Protocol;

/**
 * Actividad Principal de ScreenBaconViewer.
 * 
 * Gestiona:
 * - Escaneo UDP multi-interfaz (Wi-Fi, Cable USB / Ethernet) mediante UdpServerScanner.
 * - Conexión manual por escaneo de código QR (Deskreen) o ingreso de datos (IP, Puerto, Sala).
 * - Modo Kiosk / Pantalla Completa Inmersiva (Immersive Sticky).
 * - Inicialización del WebView con WebAppInterface ("AndroidBridge") para Socket.io / Deskreen.
 * - Overlay de reconexión con reintento automático y cancelación.
 * - Reconexión transparente a nuevas salas UDP ante cambios de sessionId.
 * - Conexión automática por ID guardado y selección manual con botones separados por interfaz.
 */
public class MainActivity extends AppCompatActivity implements 
        AndroidBridge.OnServerDiscoveredListener, 
        WebAppInterface.WebBridgeCallback {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "ScreenBaconPrefs";
    private static final String KEY_DONT_ASK_AGAIN = "dont_ask_again";
    private static final String KEY_AUTO_SAVE_ENABLED = "auto_save_enabled";
    private static final long RETRY_INTERVAL_MS = 3000;

    // Componentes de Red y Puente
    private AndroidBridge bridge;
    private UdpServerScanner udpScanner;
    private WifiManager.MulticastLock multicastLock;

    // Componentes de Interfaz
    private WebView webView;
    private ViewGroup layoutServerDiscovery;
    private ViewGroup layoutReconnecting;
    private TextView tvStatusHeader;
    private TextView tvReconnecting;
    private Button btnCancelReconnection;
    private Button btnScanQr;
    private Button btnManualConnect;
    private RecyclerView rvServers;
    private ServerAdapter serverAdapter;

    // Handlers para reintentos en UI Thread
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = this::performSilentRetry;

    // Estado de la Conexión y Lista de Dispositivos Agrupados
    private final List<DiscoveredDevice> discoveredDevices = new ArrayList<>();
    private boolean isConnected = false;
    private boolean isReconnecting = false;
    private String connectedDeviceId = null;
    private String lastTargetUrl = null;

    // Lanzador para Escaneo de Códigos QR
    private final ActivityResultLauncher<ScanOptions> qrScanLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String contents = result.getContents().trim();
                    Log.i(TAG, "Código QR escaneado: " + contents);
                    handleScannedUrl(contents);
                } else {
                    Log.d(TAG, "Escaneo de QR cancelado por el usuario.");
                }
            }
    );

    // Lanzador para Permiso de Cámara en tiempo de ejecución
    private final ActivityResultLauncher<String> requestCameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchQrScanner();
                } else {
                    Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configurar pantalla completa inmersiva permanente (Kiosk Mode)
        enableImmersiveKioskMode();

        setContentView(R.layout.activity_main);

        // 2. Configurar la ruta de almacenamiento interno para IdsParser
        IdsParser.setBasePath(getFilesDir().getAbsolutePath());

        // 3. Inicializar vistas y listeners de UI
        initViews();

        // 4. Configurar WebView con WebAppInterface y parámetros técnicos
        setupWebView();

        // 5. Configurar Adaptador y Lista de Servidores
        setupRecyclerView();

        // 6. Activar MulticastLock para recepción UDP en redes Wi-Fi
        acquireMulticastLock();

        // 7. Inicializar AndroidBridge y arrancar UdpServerScanner
        bridge = new AndroidBridge(this);
        udpScanner = new UdpServerScanner(this, bridge);
        udpScanner.startListeners(9547);

        Log.d(TAG, "UdpServerScanner iniciado en puerto 9547 en todas las interfaces de red.");
    }

    private void enableImmersiveKioskMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveKioskMode();
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        layoutServerDiscovery = findViewById(R.id.layoutServerDiscovery);
        layoutReconnecting = findViewById(R.id.layoutReconnecting);
        tvStatusHeader = findViewById(R.id.tvStatusHeader);
        tvReconnecting = findViewById(R.id.tvReconnecting);
        btnCancelReconnection = findViewById(R.id.btnCancelReconnection);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnManualConnect = findViewById(R.id.btnManualConnect);
        rvServers = findViewById(R.id.rvServers);

        btnCancelReconnection.setOnClickListener(v -> cancelReconnection());
        btnScanQr.setOnClickListener(v -> startQrScan());
        btnManualConnect.setOnClickListener(v -> showManualConnectDialog());
    }

    private void setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    Log.w(TAG, "Error de carga en MainFrame: " + error);
                    showReconnectionOverlay();
                    scheduleSilentRetry();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Página cargada con éxito: " + url);
                if (url != null && !url.equals("about:blank") && !isReconnecting) {
                    hideReconnectionOverlay();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
    }

    private void setupRecyclerView() {
        serverAdapter = new ServerAdapter(discoveredDevices, this::onServerItemClicked);
        rvServers.setLayoutManager(new LinearLayoutManager(this));
        rvServers.setAdapter(serverAdapter);
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("ScreenBaconUdpLock");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "No se pudo adquirir MulticastLock", e);
        }
    }

    // =========================================================================
    // CONEXIÓN MANUAL Y ESCANEO DE CÓDIGO QR (Deskreen)
    // =========================================================================

    private void startQrScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt(getString(R.string.qr_prompt));
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setCaptureActivity(CaptureActivity.class);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        qrScanLauncher.launch(options);
    }

    private void handleScannedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            Toast.makeText(this, R.string.invalid_url_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String targetUrl = rawUrl;
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "http://" + targetUrl;
        }

        Log.i(TAG, "Conectando mediante QR a: " + targetUrl);
        connectDirectUrl(targetUrl);
    }

    private void showManualConnectDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_manual_connect, null);
        EditText etIp = dialogView.findViewById(R.id.etManualIp);
        EditText etPort = dialogView.findViewById(R.id.etManualPort);
        EditText etSessionId = dialogView.findViewById(R.id.etManualSessionId);

        new AlertDialog.Builder(this)
                .setTitle(R.string.manual_connect_title)
                .setView(dialogView)
                .setPositiveButton(R.string.connect, (dialog, which) -> {
                    String ip = etIp.getText().toString().trim();
                    String portStr = etPort.getText().toString().trim();
                    String sessionId = etSessionId.getText().toString().trim();

                    if (ip.isEmpty() || sessionId.isEmpty()) {
                        Toast.makeText(this, R.string.fill_all_fields_error, Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (portStr.isEmpty()) {
                        portStr = "3131";
                    }

                    String targetUrl = "http://" + ip + ":" + portStr + "/" + sessionId;
                    Log.i(TAG, "Conectando manualmente a: " + targetUrl);
                    connectDirectUrl(targetUrl);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void connectDirectUrl(String targetUrl) {
        lastTargetUrl = targetUrl;
        isConnected = true;
        isReconnecting = false;
        retryHandler.removeCallbacksAndMessages(null);

        layoutServerDiscovery.setVisibility(View.GONE);
        layoutReconnecting.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        webView.loadUrl(lastTargetUrl);
    }

    // =========================================================================
    // IMPLEMENTACIÓN JAVASCRIPT INTERFACE (WebAppInterface.WebBridgeCallback)
    // =========================================================================

    @Override
    public void onSocketConnected() {
        Log.i(TAG, "WebBridge: onSocketConnected() -> Stream activo.");
        isReconnecting = false;
        retryHandler.removeCallbacksAndMessages(null);
        hideReconnectionOverlay();
        webView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSocketDisconnected(String reason) {
        Log.w(TAG, "WebBridge: onSocketDisconnected(reason=" + reason + ")");

        if ("io server disconnect".equalsIgnoreCase(reason)) {
            Log.i(TAG, "Desconexión intencional del servidor. Regresando a la pantalla de selección.");
            cancelReconnection();
        } else {
            Log.i(TAG, "Desconexión de red/socket. Mostrando overlay y esperando reconexión...");
            showReconnectionOverlay();
            scheduleSilentRetry();
        }
    }

    // =========================================================================
    // CONTROL DEL OVERLAY Y REINTENTOS DE RECONEXIÓN
    // =========================================================================

    private void showReconnectionOverlay() {
        isReconnecting = true;
        layoutReconnecting.setVisibility(View.VISIBLE);
    }

    private void hideReconnectionOverlay() {
        layoutReconnecting.setVisibility(View.GONE);
        if (isConnected) {
            webView.setVisibility(View.VISIBLE);
        }
    }

    private void scheduleSilentRetry() {
        retryHandler.removeCallbacks(retryRunnable);
        if (isReconnecting) {
            retryHandler.postDelayed(retryRunnable, RETRY_INTERVAL_MS);
        }
    }

    private void performSilentRetry() {
        if (isReconnecting && lastTargetUrl != null) {
            Log.d(TAG, "Reintentando carga silenciosa de URL: " + lastTargetUrl);
            webView.loadUrl(lastTargetUrl);
            scheduleSilentRetry();
        }
    }

    private void cancelReconnection() {
        Log.i(TAG, "Cancelando reconexión y regresando a la lista de servidores.");
        retryHandler.removeCallbacksAndMessages(null);
        isReconnecting = false;
        isConnected = false;
        connectedDeviceId = null;
        lastTargetUrl = null;

        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setVisibility(View.GONE);
        }

        layoutReconnecting.setVisibility(View.GONE);
        layoutServerDiscovery.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    // DESCUBRIMIENTO UDP Y GESTIÓN DE SESIONES
    // =========================================================================

    @Override
    public void onServerDiscovered(Protocol server) {
        if (server == null) {
            return;
        }

        String displayName = (server.getDevicename() != null && !server.getDevicename().isEmpty())
                ? server.getDevicename()
                : ((server.getUsername() != null && !server.getUsername().isEmpty())
                ? server.getUsername() : server.getDeviceId());

        Log.d(TAG, "Servidor recibido [" + server.getConnectionType() + "]: " + displayName 
                + ", IP=" + server.getIp() + ", Port=" + server.getPort() + ", Session=" + server.getSessionId());

        String incomingDeviceId = server.getDeviceId();

        // CASO 1: Estado de reconexión y llega paquete del mismo deviceId
        if (isConnected && isReconnecting && connectedDeviceId != null && connectedDeviceId.equals(incomingDeviceId)) {
            Log.i(TAG, "Detectada nueva sala para " + displayName + " vía " + server.getConnectionType() + ". Reconectando...");
            retryHandler.removeCallbacksAndMessages(null);
            connectToServer(server);
            return;
        }

        // CASO 2: Ya conectados normalmente -> no interrumpir
        if (isConnected) {
            return;
        }

        // CASO 3: Conexión inicial automática si el ID está guardado
        if (incomingDeviceId != null && bridge.isIdSaved(incomingDeviceId, null)) {
            Log.d(TAG, "Servidor conocido (" + displayName + ") vía " + server.getConnectionType() + ". Conectando a la opción por defecto...");
            connectToServer(server);
        } else {
            // CASO 4: Agregar o actualizar dispositivo agrupado en la lista
            addOrUpdateDiscoveredServer(server);
        }
    }

    private void addOrUpdateDiscoveredServer(Protocol server) {
        int existingIndex = -1;

        for (int i = 0; i < discoveredDevices.size(); i++) {
            DiscoveredDevice dev = discoveredDevices.get(i);
            if (server.getDeviceId() != null && server.getDeviceId().equals(dev.getDeviceId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            DiscoveredDevice existing = discoveredDevices.get(existingIndex);
            existing.updateWith(server);
            serverAdapter.notifyItemChanged(existingIndex);
        } else {
            DiscoveredDevice newDevice = new DiscoveredDevice(server);
            discoveredDevices.add(newDevice);
            serverAdapter.notifyItemInserted(discoveredDevices.size() - 1);
        }

        tvStatusHeader.setText(getString(R.string.servers_found_count, discoveredDevices.size()));
    }

    private void onServerItemClicked(Protocol server) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean dontAskAgain = prefs.getBoolean(KEY_DONT_ASK_AGAIN, false);
        boolean autoSaveEnabled = prefs.getBoolean(KEY_AUTO_SAVE_ENABLED, false);

        if (dontAskAgain) {
            if (autoSaveEnabled && server.getDeviceId() != null) {
                bridge.saveId(server.getDeviceId());
            }
            connectToServer(server);
        } else {
            showAutoConnectDialog(server);
        }
    }

    private void showAutoConnectDialog(Protocol server) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_auto_connect, null);
        CheckBox cbDontAsk = dialogView.findViewById(R.id.cbDontAskAgain);

        String displayName = (server.getDevicename() != null && !server.getDevicename().isEmpty())
                ? server.getDevicename()
                : ((server.getUsername() != null && !server.getUsername().isEmpty())
                ? server.getUsername()
                : server.getIp());

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_auto_connect)
                .setMessage(getString(R.string.dialog_msg_auto_connect, displayName + " (" + server.getIp() + ")", server.getConnectionType()))
                .setView(dialogView)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    if (server.getDeviceId() != null) {
                        bridge.saveId(server.getDeviceId());
                    }

                    if (cbDontAsk.isChecked()) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_DONT_ASK_AGAIN, true)
                                .putBoolean(KEY_AUTO_SAVE_ENABLED, true)
                                .apply();
                    }

                    connectToServer(server);
                })
                .setNegativeButton(R.string.no, (dialog, which) -> {
                    if (cbDontAsk.isChecked()) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_DONT_ASK_AGAIN, true)
                                .putBoolean(KEY_AUTO_SAVE_ENABLED, false)
                                .apply();
                    }

                    connectToServer(server);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Construye la URL objetivo y la carga en el WebView.
     * Formato: http://<ip>:<puerto>/<sessionId>
     */
    private void connectToServer(Protocol server) {
        if (server == null || server.getIp() == null || server.getPort() == null || server.getSessionId() == null) {
            Log.e(TAG, "Datos de servidor incompletos para conectar.");
            return;
        }

        connectedDeviceId = server.getDeviceId();
        lastTargetUrl = "http://" + server.getIp() + ":" + server.getPort() + "/" + server.getSessionId();
        Log.i(TAG, "Conectando al stream vía [" + server.getConnectionType() + "]: " + lastTargetUrl);

        isConnected = true;
        isReconnecting = false;
        retryHandler.removeCallbacksAndMessages(null);

        layoutServerDiscovery.setVisibility(View.GONE);
        layoutReconnecting.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        webView.loadUrl(lastTargetUrl);
    }

    @Override
    public void onBackPressed() {
        if (isConnected || isReconnecting) {
            cancelReconnection();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        retryHandler.removeCallbacksAndMessages(null);

        if (udpScanner != null) {
            udpScanner.stopListeners();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (webView != null) {
            webView.destroy();
        }
    }
}
