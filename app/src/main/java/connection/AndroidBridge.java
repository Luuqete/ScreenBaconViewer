package connection;

import android.os.Handler;
import android.os.Looper;
import protocol.Protocol;

/**
 * AndroidBridge extiende LogicalBridge para conectar los eventos de red UDP
 * con el hilo principal (UI Thread / Main Thread) de Android mediante Handler y Looper.
 */
public class AndroidBridge extends LogicalBridge {

    /**
     * Interfaz de callback para notificar eventos de descubrimiento a la UI (MainActivity).
     */
    public interface OnServerDiscoveredListener {
        void onServerDiscovered(Protocol server);
    }

    private final Handler mainHandler;
    private OnServerDiscoveredListener listener;

    public AndroidBridge() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public AndroidBridge(OnServerDiscoveredListener listener) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listener = listener;
    }

    public void setOnServerDiscoveredListener(OnServerDiscoveredListener listener) {
        this.listener = listener;
    }

    /**
     * Este método se ejecuta en el hilo secundario del UdpServer.
     * Redirige la ejecución de forma segura al Main Thread de Android.
     */
    @Override
    public void onComunicationFound(Protocol server) {
        if (server == null) {
            return;
        }

        // Despachar al hilo principal (UI Thread)
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onServerDiscovered(server);
            }
        });
    }
}
