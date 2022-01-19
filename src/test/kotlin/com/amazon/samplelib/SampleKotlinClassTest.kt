package com.amazon.samplelib

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 *  SampleKotlinClassTest.
 */
class SampleKotlinClassTest {
    @Test
    fun sampleMethodTest() {
        val sampleKotlinClass = SampleKotlinClass()
        assertEquals("sampleMethod() called!", sampleKotlinClass.sampleMethod())
    }
}
