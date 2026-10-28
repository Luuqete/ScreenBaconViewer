package com.screenbacon.viewer;

import org.junit.Test;
import static org.junit.Assert.*;
import protocol.Protocol;

public class ProtocolTest {
    @Test
    public void protocolRecordTest() {
        Protocol protocol = new Protocol("192.168.1.10", "device1", "session1", "9547", "1.0", "user1", "device_name");
        assertEquals("192.168.1.10", protocol.getIp());
        assertEquals("9547", protocol.getPort());
        assertEquals("session1", protocol.getSessionId());
        assertEquals("device1", protocol.getDeviceId());
        assertEquals("user1", protocol.getUsername());
        assertEquals("device_name", protocol.getDevicename());
    }
}
