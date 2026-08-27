package io.github.zensu357.camswap;

import android.util.Log;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigManagerJvmTest {
    @Test
    public void updateConfigFromJson_readsFormalizedKeys() {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class)) {
            logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);

            ConfigManager configManager = new ConfigManager(false);
            configManager.updateConfigFromJSON("{"
                    + "\"selected_video\":\"demo.mp4\"," 
                    + "\"replace_mode\":\"image\"," 
                    + "\"notification_control_enabled\":true"
                    + "}");

            assertEquals("demo.mp4", configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null));
            assertEquals(ConfigManager.REPLACE_MODE_IMAGE,
                    configManager.getString(ConfigManager.KEY_REPLACE_MODE, ConfigManager.REPLACE_MODE_VIDEO));
            assertTrue(configManager.getBoolean(ConfigManager.KEY_NOTIFICATION_CONTROL_ENABLED, false));
        }
    }

    @Test
    public void ipcAliasesStayInSync() {
        assertEquals(IpcContract.ACTION_UPDATE_CONFIG, ConfigManager.ACTION_UPDATE_CONFIG);
        assertEquals(IpcContract.ACTION_REQUEST_CONFIG, ConfigManager.ACTION_REQUEST_CONFIG);
        assertEquals(IpcContract.EXTRA_CONFIG_JSON, ConfigManager.EXTRA_CONFIG_JSON);
    }

    @Test
    public void aspectAndShortcutDefaultsAreIndependent() {
        try (MockedStatic<Log> logMock = Mockito.mockStatic(Log.class)) {
            logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);

            ConfigManager configManager = new ConfigManager(false);

            assertEquals(ConfigManager.ASPECT_MODE_FIT,
                    configManager.getString(ConfigManager.KEY_VIDEO_ASPECT_MODE, ConfigManager.ASPECT_MODE_FIT));
            assertEquals("", configManager.getShortcutVideo(ConfigManager.KEY_SHORTCUT_DOT_VIDEO));
            assertEquals("", configManager.getShortcutVideo(ConfigManager.KEY_SHORTCUT_LEFT_VIDEO));

            configManager.updateConfigFromJSON("{"
                    + "\"" + ConfigManager.KEY_SHORTCUT_DOT_VIDEO + "\":\"dot.mp4\","
                    + "\"" + ConfigManager.KEY_SHORTCUT_LEFT_VIDEO + "\":\"left.mp4\""
                    + "}");

            assertEquals("dot.mp4", configManager.getShortcutVideo(ConfigManager.KEY_SHORTCUT_DOT_VIDEO));
            assertEquals("left.mp4", configManager.getShortcutVideo(ConfigManager.KEY_SHORTCUT_LEFT_VIDEO));
            assertEquals("", configManager.getShortcutVideo(ConfigManager.KEY_SHORTCUT_RIGHT_VIDEO));
        }
    }
}
