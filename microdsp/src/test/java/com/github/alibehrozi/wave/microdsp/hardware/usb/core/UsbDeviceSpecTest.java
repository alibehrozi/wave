package com.github.alibehrozi.wave.microdsp.hardware.usb.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsbDeviceSpecTest {

    @Test
    public void testConstructorAndGetters() {
        UsbDeviceSpec spec = new UsbDeviceSpec(0x1D50, 0x6089, "HackRF One");

        assertEquals(0x1D50, spec.getVendorId());
        assertEquals(0x6089, spec.getProductId());
        assertEquals("HackRF One", spec.getName());
    }

    @Test
    public void testConstructorWithoutName() {
        UsbDeviceSpec spec = new UsbDeviceSpec(0x0BDA, 0x2838);

        assertEquals(0x0BDA, spec.getVendorId());
        assertEquals(0x2838, spec.getProductId());
        assertNull(spec.getName());
    }

    @Test
    public void testMatches() {
        UsbDeviceSpec spec = new UsbDeviceSpec(0x1D50, 0x6089, "HackRF");

        assertTrue(spec.matches(0x1D50, 0x6089));
        assertFalse(spec.matches(0x1D50, 0x604B));
        assertFalse(spec.matches(0x0BDA, 0x6089));
        assertFalse(spec.matches(0, 0));

        UsbDeviceSpec rad1o = new UsbDeviceSpec(0x1D50, 0xCC15, "rad1o");
        assertTrue(rad1o.matches(0x1D50, 0xCC15));

        UsbDeviceSpec dfuSpec = new UsbDeviceSpec(0x1FC9, 0x000C, "HackRF (DFU Mode)");
        assertTrue(dfuSpec.matches(0x1FC9, 0x000C));
    }

    @Test
    public void testEqualsAndHashCode() {
        UsbDeviceSpec spec1 = new UsbDeviceSpec(0x1D50, 0x6089, "HackRF A");
        UsbDeviceSpec spec2 = new UsbDeviceSpec(0x1D50, 0x6089, "HackRF B");
        UsbDeviceSpec spec3 = new UsbDeviceSpec(0x0BDA, 0x2838, "RTL-SDR");

        // Equals should compare vendorId and productId
        assertEquals(spec1, spec2);
        assertEquals(spec1.hashCode(), spec2.hashCode());

        assertNotEquals(spec1, spec3);
        assertNotEquals(spec1, null);
        assertNotEquals(spec1, "not a spec");
    }

    @Test
    public void testToString() {
        UsbDeviceSpec specWithName = new UsbDeviceSpec(0x1D50, 0x6089, "HackRF One");
        assertEquals("HackRF One", specWithName.toString());

        UsbDeviceSpec specWithoutName = new UsbDeviceSpec(0x1D50, 0x6089);
        assertNotNull(specWithoutName.toString());
        assertTrue(specWithoutName.toString().contains("VID:0x1d50"));
        assertTrue(specWithoutName.toString().contains("PID:0x6089"));
    }
}
