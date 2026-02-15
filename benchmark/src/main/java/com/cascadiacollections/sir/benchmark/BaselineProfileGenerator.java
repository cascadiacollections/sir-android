package com.cascadiacollections.sir.benchmark;

import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.UiDevice;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlin.Unit;

/**
 * Generates Baseline Profile for the SIR app.
 * <p>
 * Baseline Profiles improve app startup by pre-compiling critical code paths.
 * The generated profile is bundled with the APK and used by ART for AOT compilation.
 * <p>
 * Run with: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * -P android.testInstrumentationRunnerArguments.class=com.cascadiacollections.sir.benchmark.BaselineProfileGenerator
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BaselineProfileGenerator {

    private static final String PACKAGE_NAME = "com.cascadiacollections.sir";

    @Rule
    public BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void generateBaselineProfile() {
        baselineProfileRule.collect(
            PACKAGE_NAME,
            10,  // maxIterations
            3,   // stableIterations
            null, // outputFilePrefix
            true, // includeInStartupProfile
            true, // strictStability
            (MacrobenchmarkScope scope) -> {
                // Cold start - most critical path for startup performance
                scope.pressHome();
                scope.startActivityAndWait();

                // Wait for UI to settle and media session to initialize
                UiDevice device = scope.getDevice();
                device.waitForIdle();

                // Simulate user interaction - tap to start playback
                // This captures the playback initialization code path
                device.click(device.getDisplayWidth() / 2, device.getDisplayHeight() / 2);

                // Wait for playback to start and UI to update
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // Ignore
                }

                // Tap again to stop (exercises pause path)
                device.click(device.getDisplayWidth() / 2, device.getDisplayHeight() / 2);

                // Wait for state change
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Ignore
                }

                return Unit.INSTANCE;
            }
        );
    }
}

