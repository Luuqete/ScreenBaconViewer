package com.screenbacon.viewer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;

/**
 * Clase que expone los métodos de WebBridge a JavaScript mediante @JavascriptInterface.
 * Inyectada en el WebView como "AndroidBridge".
 */
public class WebAppInterface implements WebBridge {

    private static final String TAG = "WebAppInterface";

    public interface WebBridgeCallback {
        void onSocketConnected();
        void onSocketDisconnected(String reason);
    }

    private final Handler mainHandler;
    private final WebBridgeCallback callback;

    public WebAppInterface(WebBridgeCallback callback) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
    }

    @Override
    @JavascriptInterface
    public void onSocketConnected() {
        Log.i(TAG, "JS -> onSocketConnected() recibido");
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSocketConnected();
            }
        });
    }

    @Override
    @JavascriptInterface
    public void onSocketDisconnected(String reason) {
        Log.i(TAG, "JS -> onSocketDisconnected(reason=" + reason + ") recibido");
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSocketDisconnected(reason);
            }
        });
    }
}
