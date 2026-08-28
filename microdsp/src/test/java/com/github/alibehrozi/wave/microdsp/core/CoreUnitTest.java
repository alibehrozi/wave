package com.github.alibehrozi.wave.microdsp.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Unit tests for DSP Core structures and types.
 */
public class CoreUnitTest {

    @Test
    public void testDataTypeEnums() {
        assertEquals(0, DataType.BYTE.ordinal());
        assertEquals(1, DataType.COMPLEX_FLOAT.ordinal());
        assertEquals(2, DataType.COMPLEX_DOUBLE.ordinal());
        assertEquals(3, DataType.FLOAT.ordinal());
        assertEquals(4, DataType.DOUBLE.ordinal());
        assertEquals(5, DataType.INT32.ordinal());
        assertEquals(6, DataType.SHORT.ordinal());

        assertEquals(DataType.BYTE, DataType.valueOf("BYTE"));
        assertEquals(DataType.COMPLEX_FLOAT, DataType.valueOf("COMPLEX_FLOAT"));
        assertEquals(DataType.COMPLEX_DOUBLE, DataType.valueOf("COMPLEX_DOUBLE"));
        assertEquals(DataType.FLOAT, DataType.valueOf("FLOAT"));
        assertEquals(DataType.DOUBLE, DataType.valueOf("DOUBLE"));
        assertEquals(DataType.INT32, DataType.valueOf("INT32"));
        assertEquals(DataType.SHORT, DataType.valueOf("SHORT"));
    }

    @Test
    public void testPortDirectionEnums() {
        assertEquals(0, Port.Direction.INPUT.ordinal());
        assertEquals(1, Port.Direction.OUTPUT.ordinal());
        assertEquals(Port.Direction.INPUT, Port.Direction.valueOf("INPUT"));
        assertEquals(Port.Direction.OUTPUT, Port.Direction.valueOf("OUTPUT"));
    }

    @Test
    public void testPortConstructorValidation() {
        try {
            new Port(0, "in", Port.Direction.INPUT);
            fail("Expected exception for zero port handle");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        try {
            new Port(12345, null, Port.Direction.INPUT);
            fail("Expected exception for null port name");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        try {
            new Port(12345, "", Port.Direction.INPUT);
            fail("Expected exception for empty port name");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        try {
            new Port(12345, "in", null);
            fail("Expected exception for null direction");
        } catch (IllegalArgumentException expected) {
            // Expected
        }
    }
}
