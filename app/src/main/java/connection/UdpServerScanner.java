package connection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import protocol.Protocol;

/**
 * Escáner UDP multinterfaz para Android.
 * Escucha en el puerto 9547 (0.0.0.0) para capturar todas las emisiones UDP
 * y determina con precisión si el paquete proviene de Wi-Fi, Cable USB / Ethernet, etc.
 */
public class UdpServerScanner {

    private static final String TAG = "UdpServerScanner";
    public static final int DEFAULT_PORT = 9547;

    private final Context context;
    private final LogicalBridge bridge;
    private final ObjectMapper mapper;
    private volatile boolean running = false;
    private final List<DatagramSocket> activeSockets = Collections.synchronizedList(new ArrayList<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UdpServerScanner(Context context, LogicalBridge bridge) {
        this.context = context.getApplicationContext();
        this.bridge = bridge;
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void startListeners() {
        startListeners(DEFAULT_PORT);
    }

    public void startListeners(int port) {
        running = true;

        new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.bind(new InetSocketAddress(port));
                activeSockets.add(socket);

                Log.i(TAG, "Socket UDP iniciado escuchando en 0.0.0.0:" + port);

                byte[] buf = new byte[4096];
                while (running && socket != null && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    if (packet.getLength() >= buf.length) {
                        Log.w(TAG, "Posible truncamiento de paquete UDP");
                    }

                    String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    InetAddress senderAddr = packet.getAddress();
                    String connectionType = determineTransportTypeForSender(senderAddr);

                    Log.d(TAG, "Trama UDP recibida de " + senderAddr.getHostAddress() + " (" + connectionType + ") -> " + json);

                    Protocol server;
                    try {
                        server = parseProtocol(json);
                    } catch (Exception parseEx) {
                        Log.e(TAG, "JSON inválido recibido: " + json, parseEx);
                        continue;
                    }

                    if (server != null) {
                        server.setConnectionType(connectionType);
                        mainHandler.post(() -> notifyServerDiscovered(server));
                    }
                }
            } catch (SocketException e) {
                if (running) {
                    Log.e(TAG, "Socket UDP cerrado inesperadamente", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error escuchando UDP en puerto " + port, e);
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                activeSockets.remove(socket);
            }
        }, "UdpServerScanner-Thread").start();
    }

    public void stopListeners() {
        running = false;
        synchronized (activeSockets) {
            for (DatagramSocket s : activeSockets) {
                if (s != null && !s.isClosed()) {
                    s.close();
                }
            }
            activeSockets.clear();
        }
        Log.i(TAG, "Listeners UDP detenidos.");
    }

    private Protocol parseProtocol(String json) throws Exception {
        return mapper.readValue(json, Protocol.class);
    }

    private void notifyServerDiscovered(Protocol server) {
        if (bridge != null) {
            bridge.onComunicationFound(server);
        }
    }

    /**
     * Determina el tipo de transporte (Wi-Fi, Cable USB / Ethernet) para una IP emisora
     * comparando las subredes de las interfaces de red locales activas.
     */
    public String determineTransportTypeForSender(InetAddress senderAddress) {
        if (senderAddress == null) {
            return "Red Local";
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                    InetAddress localAddr = ifaceAddr.getAddress();
                    if (localAddr instanceof Inet4Address && senderAddress instanceof Inet4Address) {
                        short prefix = ifaceAddr.getNetworkPrefixLength();
                        if (isSameSubnet((Inet4Address) localAddr, (Inet4Address) senderAddress, prefix)) {
                            return getTransportTypeForInterface(iface, localAddr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al determinar subred", e);
        }

        return "Red Local";
    }

    private boolean isSameSubnet(Inet4Address addr1, Inet4Address addr2, short prefixLength) {
        if (prefixLength <= 0 || prefixLength > 32) {
            prefixLength = 24;
        }
        int mask = prefixLength == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLength);
        int ip1 = inet4ToInt(addr1);
        int ip2 = inet4ToInt(addr2);
        return (ip1 & mask) == (ip2 & mask);
    }

    private int inet4ToInt(Inet4Address addr) {
        byte[] bytes = addr.getAddress();
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private String getTransportTypeForInterface(NetworkInterface iface, InetAddress localIp) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network network : cm.getAllNetworks()) {
                LinkProperties linkProps = cm.getLinkProperties(network);
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);

                if (linkProps != null && caps != null) {
                    for (LinkAddress la : linkProps.getLinkAddresses()) {
                        if (la.getAddress().equals(localIp)) {
                            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                                return "Wi-Fi";
                            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                                return "Cable USB";
                            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                                return "Datos Móviles";
                            }
                        }
                    }
                }
            }
        }

        if (iface != null) {
            String name = iface.getName().toLowerCase();
            if (name.contains("wlan")) {
                return "Wi-Fi";
            } else if (name.contains("rndis") || name.contains("usb") || name.contains("eth")) {
                return "Cable USB";
            }
        }

        return "Red Local";
    }
}
