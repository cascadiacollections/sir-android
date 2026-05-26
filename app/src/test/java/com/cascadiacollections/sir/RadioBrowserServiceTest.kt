package com.cascadiacollections.sir

import kotlinx.coroutines.test.runTest
import org.junit.Test

class RadioBrowserServiceTest {
    @Test
    fun testSearchStations() = runTest {
        val service = RadioBrowserService()
        val result = service.searchStations("jazz", limit = 10)
        
        result.onSuccess { stations ->
            println("✅ Search succeeded! Found ${stations.size} stations")
            if (stations.isNotEmpty()) {
                println("First station: ${stations[0].name} - ${stations[0].url}")
            }
            assert(stations.size > 0) { "Expected results but got empty list" }
        }.onFailure { e ->
            println("❌ Search failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
