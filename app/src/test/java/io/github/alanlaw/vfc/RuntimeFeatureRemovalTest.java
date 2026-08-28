package io.github.alanlaw.vfc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;

/**
 * Source-level guardrails for v0.2.1 features that must stay removed at runtime.
 * These checks intentionally cover wiring and packaging contracts that cannot be
 * observed through the JVM-only Android unit-test runtime.
 */
public class RuntimeFeatureRemovalTest {
    @Test
    public void hookMainDoesNotInitializeAudioHooks() throws IOException {
        String hookMain = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/HookMain.java");
        String bridge = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/utils/CameraServerBridge.kt");
        String cmake = readProjectFile("app/src/main/cpp/CMakeLists.txt");

        assertFalse(hookMain.contains("new MicrophoneHandler"));
        assertFalse(hookMain.contains("NativeAudioHook.init"));
        assertFalse(hookMain.contains("camswap-native-hook"));
        assertFalse(bridge.contains("camswap-native-hook"));
        assertFalse(cmake.contains("camswap-native-hook"));
        assertFalse(Files.exists(projectPath("app/src/main/java/io/github/alanlaw/vfc/MicrophoneHandler.java")));
        assertFalse(Files.exists(projectPath("app/src/main/java/io/github/alanlaw/vfc/NativeAudioHook.java")));
    }

    @Test
    public void notificationControlHasNoRuntimeEntryPoint() throws IOException {
        String manifest = readProjectFile("app/src/main/AndroidManifest.xml");
        String settings = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/ui/SettingsScreen.kt");
        String home = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/ui/HomeScreen.kt");
        String mainViewModel = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/ui/MainViewModel.kt");
        String ipc = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/IpcContract.java");

        assertFalse(manifest.contains("NotificationService"));
        assertFalse(manifest.contains("POST_NOTIFICATIONS"));
        assertFalse(manifest.contains("FOREGROUND_SERVICE"));
        assertFalse(settings.contains("NotificationService"));
        assertFalse(settings.contains("KEY_NOTIFICATION_CONTROL_ENABLED"));
        assertFalse(home.contains("notificationControlEnabled"));
        assertFalse(mainViewModel.contains("notificationControlEnabled"));
        assertFalse(ipc.contains("ACTION_NEXT"));
        assertFalse(ipc.contains("ACTION_ROTATE"));
        assertFalse(ipc.contains("ACTION_EXIT"));
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return new String(Files.readAllBytes(projectPath(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path projectPath(String relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && current != null; i++) {
            if (Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("settings.gradle.kts"))) {
                return current.resolve(relativePath);
            }
            current = current.getParent();
        }
        throw new AssertionError("Project file not found: " + relativePath);
    }
}
