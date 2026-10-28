package com.screenbacon.viewer;

import protocol.Protocol;

/**
 * Representa un dispositivo/servidor ScreenBacon descubierto,
 * que puede estar disponible a través de múltiples interfaces (Wi-Fi, Cable USB, etc.).
 */
public class DiscoveredDevice {

    private final String deviceId;
    private String devicename;
    private String username;
    private String sessionId;
    private Protocol wifiProtocol;
    private Protocol ethernetProtocol;
    private Protocol genericProtocol;

    public DiscoveredDevice(Protocol initialProtocol) {
        this.deviceId = initialProtocol.getDeviceId();
        updateWith(initialProtocol);
    }

    public void updateWith(Protocol protocol) {
        if (protocol.getDevicename() != null && !protocol.getDevicename().isEmpty()) {
            this.devicename = protocol.getDevicename();
        }
        if (protocol.getUsername() != null && !protocol.getUsername().isEmpty()) {
            this.username = protocol.getUsername();
        }
        if (protocol.getSessionId() != null && !protocol.getSessionId().isEmpty()) {
            this.sessionId = protocol.getSessionId();
        }

        String type = protocol.getConnectionType();
        if (type != null) {
            String lower = type.toLowerCase();
            if (lower.contains("usb") || lower.contains("ethernet") || lower.contains("cable")) {
                this.ethernetProtocol = protocol;
            } else if (lower.contains("wi-fi") || lower.contains("wifi")) {
                this.wifiProtocol = protocol;
            } else {
                this.genericProtocol = protocol;
            }
        } else {
            this.genericProtocol = protocol;
        }
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDevicename() {
        return devicename;
    }

    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Protocol getWifiProtocol() {
        return wifiProtocol;
    }

    public Protocol getEthernetProtocol() {
        return ethernetProtocol;
    }

    public Protocol getGenericProtocol() {
        return genericProtocol;
    }

    public boolean hasBothWifiAndEthernet() {
        return wifiProtocol != null && ethernetProtocol != null;
    }

    /**
     * Devuelve el protocolo preferido por defecto (Cable USB > Wi-Fi > Genérico).
     */
    public Protocol getPreferredProtocol() {
        if (ethernetProtocol != null) {
            return ethernetProtocol;
        }
        if (wifiProtocol != null) {
            return wifiProtocol;
        }
        return genericProtocol;
    }
}
