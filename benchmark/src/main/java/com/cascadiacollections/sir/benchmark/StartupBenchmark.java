package com.cascadiacollections.sir.benchmark;

import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Startup performance benchmarks for SIR Internet Radio App.
 * 
 * Measures cold, warm, and hot startup times with different compilation modes
 * to validate the impact of baseline profiles on startup performance.
 * 
 * Run on a physical device with:
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * 
 * Results will show:
 * - Time to initial display (TTID)
 * - Time to fully drawn (TTFD) 
 * - Comparison across compilation modes (None, Partial, Full)
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class StartupBenchmark {
    
    @Rule
    public MacrobenchmarkRule rule = new MacrobenchmarkRule();

    /**
     * Cold startup benchmark with no compilation (worst case).
     * Baseline: Expected 800-1200ms on modern devices.
     */
    @Test
    public void startupNoCompilation() {
        benchmarkStartup(CompilationMode.None());
    }

    /**
     * Cold startup benchmark with baseline profile (production case).
     * Expected: 15-30% faster than no compilation.
     */
    @Test
    public void startupBaselineProfile() {
        benchmarkStartup(CompilationMode.Partial());
    }

    /**
     * Cold startup benchmark with full AOT compilation (best case).
     * Expected: Fastest startup, but larger APK size.
     */
    @Test
    public void startupFullCompilation() {
        benchmarkStartup(CompilationMode.Full());
    }

    /**
     * Warm startup benchmark - app in background, process alive.
     */
    @Test
    public void startupWarm() {
        rule.measureRepeated(
            "com.cascadiacollections.sir",
            new StartupTimingMetric(),
            5,  // iterations
            StartupMode.WARM,
            device -> {
                device.pressHome();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                String packageName = "com.cascadiacollections.sir";
                android.content.Intent intent = device.getLauncherPackageName() != null ?
                    device.getContext().getPackageManager().getLaunchIntentForPackage(packageName) :
                    null;
                
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    device.getContext().startActivity(intent);
                }
                
                device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000);
                device.waitForIdle(2000);
                
                return null;
            }
        );
    }

    /**
     * Hot startup benchmark - app in foreground, just resumed.
     */
    @Test
    public void startupHot() {
        rule.measureRepeated(
            "com.cascadiacollections.sir",
            new StartupTimingMetric(),
            5,  // iterations
            StartupMode.HOT,
            device -> {
                device.pressHome();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                String packageName = "com.cascadiacollections.sir";
                android.content.Intent intent = device.getLauncherPackageName() != null ?
                    device.getContext().getPackageManager().getLaunchIntentForPackage(packageName) :
                    null;
                
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    device.getContext().startActivity(intent);
                }
                
                device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000);
                device.waitForIdle(1000);
                
                return null;
            }
        );
    }

    /**
     * Helper method to benchmark cold startup with specified compilation mode.
     */
    private void benchmarkStartup(CompilationMode compilationMode) {
        rule.measureRepeated(
            "com.cascadiacollections.sir",
            new StartupTimingMetric(),
            5,  // iterations
            StartupMode.COLD,
            compilationMode,
            device -> {
                device.pressHome();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                String packageName = "com.cascadiacollections.sir";
                android.content.Intent intent = device.getLauncherPackageName() != null ?
                    device.getContext().getPackageManager().getLaunchIntentForPackage(packageName) :
                    null;
                
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    device.getContext().startActivity(intent);
                }
                
                device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000);
                device.waitForIdle(2000);
                
                return null;
            }
        );
    }
}
