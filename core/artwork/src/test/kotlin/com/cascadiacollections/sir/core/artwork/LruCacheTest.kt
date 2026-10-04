package com.cascadiacollections.sir.core.artwork

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class LruCacheTest {

    @Test
    fun `evicts the least recently used entry`() {
        val cache = LruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"] // touch a, so b is now eldest
        cache["c"] = 3

        assertThat(cache["a"]).isEqualTo(1)
        assertThat(cache["b"]).isNull()
        assertThat(cache["c"]).isEqualTo(3)
        assertThat(cache.size()).isEqualTo(2)
    }

    @Test
    fun `contains distinguishes a cached null from a missing key`() {
        val cache = LruCache<String, Int?>(2)
        cache["miss"] = null
        assertThat(cache.contains("miss")).isTrue()
        assertThat(cache.contains("other")).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `size must be positive`() {
        LruCache<String, Int>(0)
    }
}
