package com.cascadiacollections.sir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that installs an [UnconfinedTestDispatcher] as [Dispatchers.Main]
 * for the duration of each test. Required by any test that touches
 * `viewModelScope.launch` or other Main-dispatched coroutines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineRule : TestWatcher() {

    val testDispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}
