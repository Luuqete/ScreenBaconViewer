package protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Protocol {
    private String ip;
    private String deviceId;
    private String sessionId;
    private String port;
    private String version;
    private String devicename;
    private String username;
    private String connectionType = "Wi-Fi"; // "Wi-Fi", "Cable USB", "Red Local", etc.

    public Protocol() {
    }

    public Protocol(
            String ip,
            String deviceId,
            String sessionId,
            String port,
            String version,
            String username,
            String devicename
    ) {
        this(ip, deviceId, sessionId, port, version, username, devicename, null);
    }

    public Protocol(
            @JsonProperty("ip") String ip,
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("port") String port,
            @JsonProperty("version") String version,
            @JsonProperty("username") String username,
            @JsonProperty("devicename") String devicename,
            @JsonProperty("hostname") String hostname
    ) {
        this.ip = ip;
        this.deviceId = deviceId;
        this.sessionId = sessionId;
        this.port = port;
        this.version = version;
        this.username = username;
        this.devicename = (devicename != null && !devicename.isEmpty()) ? devicename : hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDevicename() {
        return devicename;
    }

    public void setDevicename(String devicename) {
        this.devicename = devicename;
    }

    @JsonProperty("hostname")
    public void setHostname(String hostname) {
        if (this.devicename == null || this.devicename.isEmpty()) {
            this.devicename = hostname;
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getConnectionType() {
        return connectionType != null ? connectionType : "Wi-Fi";
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    @Override
    public String toString() {
        return "Protocol[ip=" + ip + ", deviceId=" + deviceId + ", sessionId=" + sessionId + ", port=" + port + ", version=" + version + ", username=" + username + ", devicename=" + devicename + ", connectionType=" + connectionType + "]";
    }
}