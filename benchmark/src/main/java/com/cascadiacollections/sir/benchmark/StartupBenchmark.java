package com.cascadiacollections.sir.benchmark;

import androidx.benchmark.macro.BaselineProfileMode;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import kotlin.Unit;

/**
 * Startup benchmark for measuring app launch performance.
 * <p>
 * Run with: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * <p>
 * Results will show startup time improvements with Baseline Profiles.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class StartupBenchmark {

    private static final String PACKAGE_NAME = "com.cascadiacollections.sir";

    @Rule
    public MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    /**
     * Measures cold startup time without any compilation (worst case).
     */
    @Test
    public void startupNoCompilation() {
        startup(new CompilationMode.None());
    }

    /**
     * Measures cold startup time with Baseline Profile applied.
     * This represents typical user experience after app install.
     */
    @Test
    public void startupWithBaselineProfile() {
        startup(new CompilationMode.Partial(BaselineProfileMode.Require, 0));
    }

    /**
     * Measures cold startup time with full AOT compilation.
     * This is the best-case scenario (all code pre-compiled).
     */
    @Test
    public void startupFullCompilation() {
        startup(new CompilationMode.Full());
    }

    private void startup(CompilationMode compilationMode) {
        benchmarkRule.measureRepeated(
            PACKAGE_NAME,
            Collections.singletonList(new StartupTimingMetric()),
            compilationMode,
            StartupMode.COLD,
            5,
            (MacrobenchmarkScope scope) -> {
                scope.pressHome();
                scope.startActivityAndWait();
                return Unit.INSTANCE;
            }
        );
    }
}

