package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SdrDeviceInfoTest {

    @Test
    public void testConstructorWithDuplexMode() {
        SdrDeviceInfo info = new SdrDeviceInfo(
                "Great Scott Gadgets",
                "HackRF One",
                "r9",
                "2024.02.1",
                "0000000000000000a06068dc265b45c3",
                0x1D50,
                0x6089,
                SdrRole.HALF_DUPLEX
        );

        assertEquals("Great Scott Gadgets", info.getManufacturer());
        assertEquals("HackRF One", info.getProduct());
        assertEquals("r9", info.getHardwareRevision());
        assertEquals("2024.02.1", info.getFirmwareVersion());
        assertEquals("0000000000000000a06068dc265b45c3", info.getSerialNumber());
        assertEquals(0x1D50, info.getUsbVendorId());
        assertEquals(0x6089, info.getUsbProductId());
        assertEquals(SdrRole.HALF_DUPLEX, info.getRole());
        assertTrue(info.supportsRx());
        assertTrue(info.supportsTx());
        assertFalse(info.isFullDuplex());
        assertTrue(info.isHalfDuplex());
    }

    @Test
    public void testDefaultConstructorSetsHalfDuplex() {
        SdrDeviceInfo info = new SdrDeviceInfo(
                "Great Scott Gadgets",
                "HackRF One",
                "r9",
                "2024.02.1",
                "0000000000000000a06068dc265b45c3",
                0x1D50,
                0x6089
        );

        assertEquals(SdrRole.HALF_DUPLEX, info.getRole());
        assertTrue(info.supportsRx());
        assertTrue(info.supportsTx());
    }

    @Test
    public void testRxOnlyDeviceInfo() {
        SdrDeviceInfo info = new SdrDeviceInfo(
                "RTL-SDR Blog",
                "V4",
                null,
                null,
                null,
                0x0BDA,
                0x2838,
                SdrRole.RX_ONLY
        );

        assertEquals("RTL-SDR Blog", info.getManufacturer());
        assertEquals("V4", info.getProduct());
        assertNull(info.getHardwareRevision());
        assertNull(info.getFirmwareVersion());
        assertNull(info.getSerialNumber());
        assertEquals(0x0BDA, info.getUsbVendorId());
        assertEquals(0x2838, info.getUsbProductId());
        assertEquals(SdrRole.RX_ONLY, info.getRole());
        assertTrue(info.supportsRx());
        assertFalse(info.supportsTx());
        assertFalse(info.isFullDuplex());
        assertFalse(info.isHalfDuplex());
    }

    @Test
    public void testFullDuplexDeviceInfo() {
        SdrDeviceInfo info = new SdrDeviceInfo(
                "Analog Devices",
                "ADALM-Pluto",
                "Rev. C",
                "v0.31",
                "123456",
                0x0456,
                0xB673,
                SdrRole.FULL_DUPLEX
        );

        assertEquals(SdrRole.FULL_DUPLEX, info.getRole());
        assertTrue(info.supportsRx());
        assertTrue(info.supportsTx());
        assertTrue(info.isFullDuplex());
        assertFalse(info.isHalfDuplex());
    }

    @Test
    public void testToString() {
        SdrDeviceInfo info = new SdrDeviceInfo(
                "Great Scott Gadgets",
                "HackRF One",
                "r9",
                "2024.02.1",
                "12345",
                0x1D50,
                0x6089,
                SdrRole.HALF_DUPLEX
        );

        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("HackRF One"));
        assertTrue(str.contains("Great Scott Gadgets"));
        assertTrue(str.contains("12345"));
        assertTrue(str.contains("HALF_DUPLEX"));
    }
}
