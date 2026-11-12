package com.parkhere.drivewatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarKeyTest {
    /** The Pleos emulator answers "invalid" (with quotes); only a real 17-character VIN counts. */
    @Test fun onlyRealVinsCount() {
        assertNull(CarKey.validVin("\"invalid\""))
        assertNull(CarKey.validVin(""))
        assertNull(CarKey.validVin("KMHL1234IOQ567890")) // I, O, Q never appear in a VIN
        assertEquals("KMHL14JA5PA123456", CarKey.validVin(" \"kmhl14ja5pa123456\" "))
    }

    /** The server sees a fixed-length hex hash, never the VIN, and VIN and ANDROID_ID keys cannot collide. */
    @Test fun keyIsAHashNotTheValue() {
        val k = CarKey.hash("vin", "KMHL14JA5PA123456")
        assertEquals(32, k.length)
        assertEquals(k, CarKey.hash("vin", "KMHL14JA5PA123456"))
        assertNotEquals(k, CarKey.hash("android_id", "KMHL14JA5PA123456"))
        assert(Regex("^[0-9a-f]{32}$").matches(k))
    }
}
