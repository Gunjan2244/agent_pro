package com.sledje.agentpro.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mirrors private.normalize_in_phone (docs/BACKEND.md). */
class PhoneTest {
    @Test
    fun normalisesIndianMobiles() {
        assertEquals("+919876543210", Phone.normalize("9876543210"))
        assertEquals("+919876543210", Phone.normalize("+91 98765 43210"))
        assertEquals("+919876543210", Phone.normalize("919876543210"))
        assertEquals("+919876543210", Phone.normalize("09876543210"))
        assertEquals("+916123456789", Phone.normalize("(612) 345-6789"))
        assertEquals("+917123456789", Phone.normalize("712.345.6789"))
    }

    @Test
    fun rejectsOthers() {
        assertNull(Phone.normalize("5876543210"))   // must start 6-9
        assertNull(Phone.normalize("987654321"))    // 9 digits
        assertNull(Phone.normalize("98765432101"))  // 11 digits
        assertNull(Phone.normalize("+19876543210")) // foreign
        assertNull(Phone.normalize("0221234567"))   // landline
        assertNull(Phone.normalize("+91 5876543210"))
        assertNull(Phone.normalize(""))
        assertNull(Phone.normalize("98765abcde"))
    }

    @Test
    fun display() {
        assertEquals("+91 98765 43210", Phone.display("9876543210"))
        assertEquals("12345", Phone.display("12345"))
    }
}
