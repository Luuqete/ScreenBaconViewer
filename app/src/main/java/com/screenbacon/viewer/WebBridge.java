package com.screenbacon.viewer;

import android.webkit.JavascriptInterface;

/**
 * Interfaz para el puente JavaScript - Android (Deskreen / ScreenBacon WebBridge).
 */
public interface WebBridge {

    @JavascriptInterface
    void onSocketConnected();

    @JavascriptInterface
    void onSocketDisconnected(String reason);
}
