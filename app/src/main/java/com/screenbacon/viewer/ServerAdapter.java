package com.screenbacon.viewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import protocol.Protocol;

/**
 * Adaptador para listar los servidores Deskreen / ScreenBacon descubiertos,
 * mostrando el nombre de dispositivo (devicename) y usuario (username) en lugar de IDs crudos.
 */
public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ServerViewHolder> {

    public interface OnServerClickListener {
        void onServerClick(Protocol server);
    }

    private final List<DiscoveredDevice> devices;
    private final OnServerClickListener listener;

    public ServerAdapter(List<DiscoveredDevice> devices, OnServerClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ServerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server, parent, false);
        return new ServerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServerViewHolder holder, int position) {
        DiscoveredDevice device = devices.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return devices != null ? devices.size() : 0;
    }

    public static class ServerViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDeviceId;
        private final TextView tvAddress;
        private final TextView tvSessionId;
        private final Button btnConnectWifi;
        private final Button btnConnectEthernet;
        private final Button btnConnectGeneric;

        public ServerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceId = itemView.findViewById(R.id.tvDeviceId);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvSessionId = itemView.findViewById(R.id.tvSessionId);
            btnConnectWifi = itemView.findViewById(R.id.btnConnectWifi);
            btnConnectEthernet = itemView.findViewById(R.id.btnConnectEthernet);
            btnConnectGeneric = itemView.findViewById(R.id.btnConnectGeneric);
        }

        public void bind(DiscoveredDevice device, OnServerClickListener listener) {
            // Nombre del dispositivo y usuario (ocultando el deviceId técnico)
            String devName = device.getDevicename();
            String userName = device.getUsername();

            String title;
            if (devName != null && !devName.isEmpty() && userName != null && !userName.isEmpty()) {
                if (devName.equalsIgnoreCase(userName)) {
                    title = devName;
                } else {
                    title = devName + " (" + userName + ")";
                }
            } else if (devName != null && !devName.isEmpty()) {
                title = devName;
            } else if (userName != null && !userName.isEmpty()) {
                title = userName;
            } else {
                title = "Servidor ScreenBacon";
            }
            tvDeviceId.setText(title);

            // Dirección IP y Tipo de conexión
            StringBuilder addressText = new StringBuilder();
            Protocol wifi = device.getWifiProtocol();
            Protocol eth = device.getEthernetProtocol();
            Protocol generic = device.getGenericProtocol();

            if (wifi != null && eth != null) {
                addressText.append("Wi-Fi: ").append(wifi.getIp()).append(":").append(wifi.getPort())
                        .append("  |  USB: ").append(eth.getIp()).append(":").append(eth.getPort());
            } else if (eth != null) {
                addressText.append("Cable USB: ").append(eth.getIp()).append(":").append(eth.getPort());
            } else if (wifi != null) {
                addressText.append("Wi-Fi: ").append(wifi.getIp()).append(":").append(wifi.getPort());
            } else if (generic != null) {
                addressText.append(generic.getConnectionType()).append(": ").append(generic.getIp()).append(":").append(generic.getPort());
            }
            tvAddress.setText(addressText.toString());

            // Detalle de usuario y sesión
            StringBuilder detailBuilder = new StringBuilder();
            if (userName != null && !userName.isEmpty() && !title.contains(userName)) {
                detailBuilder.append("Usuario: ").append(userName).append("  •  ");
            }
            detailBuilder.append("Sesión: ").append(device.getSessionId() != null ? device.getSessionId() : "-");
            tvSessionId.setText(detailBuilder.toString());

            // Configurar visibilidad y textos de los botones según interfaces detectadas
            if (wifi != null && eth != null) {
                btnConnectWifi.setVisibility(View.VISIBLE);
                btnConnectWifi.setText("Wi-Fi");
                btnConnectWifi.setOnClickListener(v -> {
                    if (listener != null) listener.onServerClick(wifi);
                });

                btnConnectEthernet.setVisibility(View.VISIBLE);
                btnConnectEthernet.setText("Cable USB");
                btnConnectEthernet.setOnClickListener(v -> {
                    if (listener != null) listener.onServerClick(eth);
                });

                btnConnectGeneric.setVisibility(View.GONE);
            } else if (eth != null) {
                btnConnectWifi.setVisibility(View.GONE);
                btnConnectEthernet.setVisibility(View.VISIBLE);
                btnConnectEthernet.setText("Conectar (USB)");
                btnConnectEthernet.setOnClickListener(v -> {
                    if (listener != null) listener.onServerClick(eth);
                });
                btnConnectGeneric.setVisibility(View.GONE);
            } else if (wifi != null) {
                btnConnectWifi.setVisibility(View.VISIBLE);
                btnConnectWifi.setText("Conectar (Wi-Fi)");
                btnConnectWifi.setOnClickListener(v -> {
                    if (listener != null) listener.onServerClick(wifi);
                });
                btnConnectEthernet.setVisibility(View.GONE);
                btnConnectGeneric.setVisibility(View.GONE);
            } else if (generic != null) {
                btnConnectWifi.setVisibility(View.GONE);
                btnConnectEthernet.setVisibility(View.GONE);
                btnConnectGeneric.setVisibility(View.VISIBLE);
                btnConnectGeneric.setText("Conectar (" + generic.getConnectionType() + ")");
                btnConnectGeneric.setOnClickListener(v -> {
                    if (listener != null) listener.onServerClick(generic);
                });
            }

            // Clic en la tarjeta entera selecciona la opción por defecto preferida
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    Protocol preferred = device.getPreferredProtocol();
                    if (preferred != null) {
                        listener.onServerClick(preferred);
                    }
                }
            });
        }
    }
}
