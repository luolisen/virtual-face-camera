package io.github.alanlaw.vfc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Source-level guardrails for removed features that must stay absent at runtime.
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
        String watcher = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/ConfigWatcher.java");

        assertFalse(hookMain.contains("new MicrophoneHandler"));
        assertFalse(hookMain.contains("NativeAudioHook.init"));
        assertFalse(hookMain.contains("camswap-native-hook"));
        assertFalse(bridge.contains("camswap-native-hook"));
        assertFalse(cmake.contains("camswap-native-hook"));
        assertFalse(Files.exists(projectPath("app/src/main/java/io/github/alanlaw/vfc/MicrophoneHandler.java")));
        assertFalse(Files.exists(projectPath("app/src/main/java/io/github/alanlaw/vfc/NativeAudioHook.java")));

        int viewportCallback = hookMain.indexOf("public void onViewportChanged()");
        assertTrue(viewportCallback >= 0);
        int nextMethod = hookMain.indexOf("private static void switchVideo", viewportCallback);
        assertTrue(nextMethod > viewportCallback);
        String viewportBody = hookMain.substring(viewportCallback,
                nextMethod);
        assertTrue(viewportBody.contains("playerManager.updateViewport()"));
        assertFalse(viewportBody.contains("playerManager.restartAll()"));
        assertTrue(watcher.contains("boolean viewportChanged"));
        assertTrue(watcher.contains("callback.onViewportChanged()"));
        assertTrue(watcher.contains("!mediaChanged && !renderingChanged && !viewportChanged"));
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

    @Test
    public void dynamicV3KeepsCameraRolesAndRawViewportContract() throws IOException {
        String renderer = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/GLVideoRenderer.java");
        String relay = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/SurfaceRelay.java");
        String shader = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/GLHelper.java");
        String camera1 = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/Camera1Handler.java");
        String manager = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/MediaPlayerManager.java");
        String cmake = readProjectFile("app/src/main/cpp/CMakeLists.txt");

        assertTrue(renderer.contains("ensureDynamicGeometry"));
        assertTrue(renderer.contains("GLES20.glViewport(0, 0, rawTargetWidth, rawTargetHeight)"));
        assertTrue(renderer.contains("RenderTargetRole.CAPTURE"));
        assertTrue(renderer.contains("refreshSurfaceSizeIfNeeded"));
        assertFalse(frameBody(renderer).contains("eglQuerySurface"));
        assertFalse(frameBody(relay).contains("eglQuerySurface"));
        assertFalse(frameTaskBody(renderer).contains("while"));
        assertFalse(frameTaskBody(relay).contains("while"));
        assertTrue(manager.contains("prepareAsync()"));
        assertTrue(camera1.contains("prepareCamera1Player"));
        assertTrue(relay.contains("ensureDynamicGeometry"));
        assertTrue(shader.contains("uSTMatrix * uDynamicTextureMatrix * uCropMatrix"));
        assertTrue(camera1.contains("Camera1SessionRegistry.registerOpened"));
        assertTrue(camera1.contains("Camera1SessionRegistry.applyCurrentPreviewTransform"));
        assertTrue(cmake.contains("add_library(vfc_surface_bridge SHARED"));
        assertTrue(cmake.contains("cs_camserver"));
        assertTrue(cmake.contains("cs_injector"));
        assertTrue(cmake.contains("cs_daemon"));
    }

    @Test
    public void renderConfigDoesNotRestartYuvDecoder() throws IOException {
        String hookMain = readProjectFile(
                "app/src/main/java/io/github/alanlaw/vfc/HookMain.java");
        int configCallback = hookMain.indexOf("public void onRenderingConfigChanged");
        int viewportCallback = hookMain.indexOf("public void onViewportChanged", configCallback);
        assertTrue(configCallback >= 0);
        assertTrue(viewportCallback > configCallback);
        String body = hookMain.substring(configCallback, viewportCallback);
        assertFalse(body.contains("restartYuvDecoderForSourceChange"));
    }

    private static String frameTaskBody(String source) {
        int start = source.indexOf("private void runCoalescedFrame");
        int end = source.indexOf("private void drawFrame", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        return source.substring(start, end);
    }

    private static String frameBody(String source) {
        int start = source.indexOf("private void drawFrame");
        int end = source.indexOf("private void renderToBackBuffer", start);
        if (end < 0) {
            end = source.indexOf("private void refreshSurfaceSizeIfNeeded", start);
        }
        assertTrue(start >= 0);
        assertTrue(end > start);
        return source.substring(start, end);
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
