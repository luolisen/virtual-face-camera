package io.github.alanlaw.vfc.utils;

import android.util.Log;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;

import io.github.alanlaw.vfc.ConfigManager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VideoManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void tearDown() {
        VideoManager.video_path = "/storage/emulated/0/DCIM/Camera1/";
        VideoManager.current_video_path = null;
        VideoManager.setConfigManager(null);
    }

    @Test
    public void listVideoFiles_filtersSupportedExtensionsAndSortsByName() throws Exception {
        File dir = temporaryFolder.newFolder("videos");
        temporaryFolder.newFile("videos/zeta.txt");
        temporaryFolder.newFile("videos/c_clip.mkv");
        temporaryFolder.newFile("videos/a_clip.mp4");
        temporaryFolder.newFile("videos/b_clip.mov");

        File[] files = VideoManager.listVideoFiles(dir);

        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }

        assertArrayEquals(new String[] { "a_clip.mp4", "b_clip.mov", "c_clip.mkv" }, names);
    }

    @Test
    public void listVideoFiles_returnsNullForMissingOrEmptyDirectory() throws Exception {
        File emptyDir = temporaryFolder.newFolder("empty");
        File missingDir = new File(temporaryFolder.getRoot(), "missing");

        assertNull(VideoManager.listVideoFiles(emptyDir));
        assertNull(VideoManager.listVideoFiles(missingDir));
    }

    @Test
    public void updateVideoPath_usesSelectedVideoAndRefreshesAfterConfigUpdate() throws Exception {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class)) {
            logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);

            File dir = temporaryFolder.newFolder("refresh");
            File alpha = temporaryFolder.newFile("refresh/alpha.mp4");
            File beta = temporaryFolder.newFile("refresh/beta.mp4");

            ConfigManager configManager = configFromJson("{\"selected_video\":\"alpha.mp4\"}");
            configureVideoManager(dir, configManager);

            VideoManager.updateVideoPath(false);
            assertEquals(alpha.getAbsolutePath(), VideoManager.getCurrentVideoPath());

            configManager.updateConfigFromJSON("{\"selected_video\":\"beta.mp4\"}");
            VideoManager.updateVideoPath(false);

            assertEquals(beta.getAbsolutePath(), VideoManager.getCurrentVideoPath());
        }
    }

    @Test
    public void updateVideoPath_fallsBackToCamMp4WhenSelectedVideoIsMissing() throws Exception {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class)) {
            logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);

            File dir = temporaryFolder.newFolder("camFallback");
            File cam = temporaryFolder.newFile("camFallback/Cam.mp4");
            temporaryFolder.newFile("camFallback/other.mp4");

            configureVideoManager(dir, configFromJson("{\"selected_video\":\"missing.mp4\"}"));

            VideoManager.updateVideoPath(false);

            assertEquals(cam.getAbsolutePath(), VideoManager.getCurrentVideoPath());
        }
    }

    @Test
    public void updateVideoPath_fallsBackToFirstAvailableVideoWhenCamIsMissing() throws Exception {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class)) {
            logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);

            File dir = temporaryFolder.newFolder("anyFallback");
            File alpha = temporaryFolder.newFile("anyFallback/alpha.mp4");
            temporaryFolder.newFile("anyFallback/zulu.mkv");

            configureVideoManager(dir, configFromJson("{\"selected_video\":\"missing.mp4\"}"));

            VideoManager.updateVideoPath(false);

            assertEquals(alpha.getAbsolutePath(), VideoManager.getCurrentVideoPath());
        }
    }

    @Test
    public void updateVideoPath_randomModeUsesConfiguredSelectionWithoutForceRandom() throws Exception {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class)) {
            logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);

            File dir = temporaryFolder.newFolder("randomMode");
            temporaryFolder.newFile("randomMode/alpha.mp4");
            File beta = temporaryFolder.newFile("randomMode/beta.mp4");

            configureVideoManager(dir, configFromJson("{"
                    + "\"enable_random_play\":true,"
                    + "\"selected_video\":\"beta.mp4\""
                    + "}"));

            VideoManager.updateVideoPath(false);

            assertEquals(beta.getAbsolutePath(), VideoManager.getCurrentVideoPath());
        }
    }

    private void configureVideoManager(File dir, ConfigManager configManager) {
        VideoManager.video_path = dir.getAbsolutePath() + File.separator;
        VideoManager.current_video_path = null;
        VideoManager.setConfigManager(configManager);
    }

    private ConfigManager configFromJson(String json) {
        ConfigManager configManager = new ConfigManager(false);
        configManager.updateConfigFromJSON(json);
        return configManager;
    }
}
