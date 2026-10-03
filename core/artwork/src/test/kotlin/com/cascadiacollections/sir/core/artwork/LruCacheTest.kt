package com.cascadiacollections.sir.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LruCacheTest {

    @Test
    fun `evicts the least recently used entry`() {
        val cache = LruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"] // touch a, so b is now eldest
        cache["c"] = 3

        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size())
    }

    @Test
    fun `contains distinguishes a cached null from a missing key`() {
        val cache = LruCache<String, Int?>(2)
        cache["miss"] = null
        assertTrue(cache.contains("miss"))
        assertFalse(cache.contains("other"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `size must be positive`() {
        LruCache<String, Int>(0)
    }
}
