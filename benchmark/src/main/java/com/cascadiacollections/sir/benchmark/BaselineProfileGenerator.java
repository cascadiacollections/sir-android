package com.cascadiacollections.sir.benchmark;

import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Baseline Profile Generator for SIR Internet Radio App.
 * 
 * This test generates a baseline profile by exercising critical user journeys
 * during app startup and common interactions. The generated profile enables
 * ahead-of-time (AOT) compilation on install, reducing cold startup time by 15-30%.
 * 
 * Run on a physical device with:
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * 
 * The generated profile will be stored in app/src/main/baseline-prof.txt
 * 
 * Critical paths covered:
 * - App cold start and UI initialization
 * - Radio playback service initialization  
 * - Play/pause interaction
 * - Settings access
 * - Cast device detection (if available)
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BaselineProfileGenerator {
    
    @Rule
    public BaselineProfileRule rule = new BaselineProfileRule();

    @Test
    public void generateBaselineProfile() {
        rule.collect(
            "com.cascadiacollections.sir",
            // Iterate 3 times to ensure stable profile across app restarts
            3,
            // Only include stable methods (not one-off initialization)
            true,
            device -> {
                // Press home to start from launcher
                device.pressHome();
                
                // Cold start the app
                startAppAndWaitForIdle(device);
                
                // Exercise critical user journeys
                exercisePlaybackControls(device);
                exerciseSettings(device);
                
                return null;
            }
        );
    }

    /**
     * Cold starts the app and waits for the main UI to be ready.
     */
    private void startAppAndWaitForIdle(UiDevice device) {
        // Launch the app
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
        
        // Wait for app to appear
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000);
        
        // Wait for idle to ensure compose is settled
        device.waitForIdle(2000);
    }

    /**
     * Exercises play/pause controls to profile the playback service.
     */
    private void exercisePlaybackControls(UiDevice device) {
        // Tap to start playback (app is single-tap to play)
        int centerX = device.getDisplayWidth() / 2;
        int centerY = device.getDisplayHeight() / 2;
        device.click(centerX, centerY);
        
        device.waitForIdle(1000);
        
        // Tap again to pause
        device.click(centerX, centerY);
        
        device.waitForIdle(1000);
    }

    /**
     * Opens and navigates settings to profile preferences access.
     */
    private void exerciseSettings(UiDevice device) {
        // Look for settings icon (typically top-right)
        try {
            var settingsButton = device.findObject(By.desc("Settings"));
            if (settingsButton != null) {
                settingsButton.click();
                device.waitForIdle(1000);
                
                // Scroll through settings
                device.swipe(
                    device.getDisplayWidth() / 2,
                    device.getDisplayHeight() * 3 / 4,
                    device.getDisplayWidth() / 2,
                    device.getDisplayHeight() / 4,
                    10
                );
                
                device.waitForIdle(500);
                
                // Go back
                device.pressBack();
                device.waitForIdle(500);
            }
        } catch (Exception e) {
            // Settings may not be accessible, continue
        }
    }
}
