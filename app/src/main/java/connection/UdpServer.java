package connection;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import protocol.Protocol;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UdpServer {

    private static final String TAG = "UdpServer";
    private DatagramSocket sock;
    private volatile boolean loop;
    private LogicalBridge bridge;
    private ObjectMapper mapper;

    public UdpServer(LogicalBridge bridge) {
        this.loop = false;
        this.bridge = bridge;
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        initSocket();
    }

    private synchronized void initSocket() {
        try {
            if (this.sock != null && !this.sock.isClosed()) {
                this.sock.close();
            }
            this.sock = new DatagramSocket(null);
            this.sock.setReuseAddress(true);
            this.sock.setBroadcast(true);
            this.sock.bind(new InetSocketAddress(9547));
            Log.i(TAG, "Socket UDP enlazado exitosamente al puerto 9547");
        } catch (IOException e) {
            Log.e(TAG, "Error enlazando DatagramSocket en puerto 9547: " + e.getMessage(), e);
        }
    }

    private void communicationLoop() {
        if (this.sock == null || this.sock.isClosed()) {
            initSocket();
            if (this.sock == null || this.sock.isClosed()) {
                Log.e(TAG, "No se pudo iniciar el socket UDP (puerto en uso o error de red). Abortando loop.");
                return;
            }
        }

        Log.i(TAG, "Iniciando escucha UDP en puerto 9547...");
        try {
            while (loop && this.sock != null && !this.sock.isClosed()) {
                byte[] buffer = new byte[2048];
                DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                this.sock.receive(incoming);

                byte[] data = incoming.getData();
                String rawJson = new String(data, 0, incoming.getLength()).trim();
                Log.i(TAG, "Trama UDP recibida de " + incoming.getAddress().getHostAddress() + ":" + incoming.getPort() + " -> " + rawJson);

                Protocol proto = protocolDeserialization(rawJson);
                if (proto != null && this.bridge != null) {
                    this.bridge.onComunicationFound(proto);
                } else {
                    Log.w(TAG, "No se pudo deserializar el protocolo o bridge es nulo.");
                }
            }
        } catch (IOException e) {
            if (loop) {
                Log.e(TAG, "IOException en communicationLoop: " + e.getMessage());
            }
        }
    }

    public void initCommunication() {
        loop = true;
        new Thread(() -> {
            communicationLoop();
        }).start();
    }

    public synchronized void finishCommunication() {
        loop = false;
        if (this.sock != null && !this.sock.isClosed()) {
            this.sock.close();
            this.sock = null;
            Log.i(TAG, "Socket UDP cerrado.");
        }
    }

    public Protocol protocolDeserialization(String jsonText) {
        Protocol toRet = null;
        try {
            if (jsonText != null && !jsonText.trim().isEmpty()) {
                toRet = mapper.readValue(jsonText, Protocol.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deserializando JSON UDP: " + e.getMessage() + " | JSON: " + jsonText);
        }
        return toRet;
    }
}
