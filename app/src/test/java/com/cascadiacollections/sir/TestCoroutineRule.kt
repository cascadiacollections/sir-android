package com.cascadiacollections.sir

import androidx.lifecycle.ViewModelStore
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
 *
 * Any [androidx.lifecycle.ViewModel] created during the test should be
 * registered via [registerViewModel] so it is cleared (cancelling any
 * `viewModelScope` coroutines) before [Dispatchers.resetMain] runs. Without
 * this, a coroutine still running against the test's Main dispatcher can
 * race the next test's [Dispatchers.setMain] call and fail with
 * `IllegalStateException: Dispatchers.Main is used concurrently with setting it`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineRule : TestWatcher() {

    val testDispatcher = UnconfinedTestDispatcher()

    private val viewModelStore = ViewModelStore()
    private var nextKey = 0

    fun registerViewModel(viewModel: androidx.lifecycle.ViewModel) {
        viewModelStore.put((nextKey++).toString(), viewModel)
    }

    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }
}
