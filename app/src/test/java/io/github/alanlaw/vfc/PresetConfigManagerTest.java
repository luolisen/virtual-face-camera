package io.github.alanlaw.vfc;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PresetConfigManagerTest {
    private MockedStatic<Log> logMock;

    @Before
    public void setUp() {
        logMock = Mockito.mockStatic(Log.class);
        logMock.when(() -> Log.i(Mockito.anyString(), Mockito.anyString())).thenReturn(0);
    }

    @After
    public void tearDown() {
        logMock.close();
    }

    private ConfigManager configWithJson(String json) {
        ConfigManager config = new ConfigManager(false);
        config.setSkipProviderReload(true);
        config.updateConfigFromJSON(json);
        return config;
    }

    @Test
    public void firstPresetUsesDefaultNameAndStableId() {
        ConfigManager config = configWithJson("{}");

        ConfigManager.ShortcutPreset first = config.createPreset();

        assertNotNull(first);
        assertEquals("预设一", first.getName());
        assertNotNull(first.getId());
        assertEquals(first.getId(), config.getCurrentPreset().getId());
        for (String key : ConfigManager.getPresetShortcutKeys()) {
            assertEquals("", first.getVideoName(key));
        }
    }

    @Test
    public void presetNamesIncrementWithoutReusingDeletedName() {
        ConfigManager config = configWithJson("{}");

        ConfigManager.ShortcutPreset first = config.createPreset();
        ConfigManager.ShortcutPreset second = config.createPreset();
        ConfigManager.ShortcutPreset third = config.createPreset();

        assertEquals("预设一", first.getName());
        assertEquals("预设二", second.getName());
        assertEquals("预设三", third.getName());
        assertNotEquals(first.getId(), second.getId());
        assertNotEquals(second.getId(), third.getId());

        assertTrue(config.deletePreset(second.getId()));
        ConfigManager.ShortcutPreset fourth = config.createPreset();
        assertEquals("预设四", fourth.getName());
    }

    @Test
    public void renameTrimsButRejectsEmptyNames() {
        ConfigManager config = configWithJson("{}");
        ConfigManager.ShortcutPreset preset = config.createPreset();

        assertFalse(config.renamePreset(preset.getId(), "   "));
        assertTrue(config.renamePreset(preset.getId(), "  夜间 😊  "));
        assertEquals("夜间 😊", config.getPreset(preset.getId()).getName());
    }

    @Test
    public void currentPresetIsUniqueAndDoesNotChangeSelectedVideo() {
        ConfigManager config = configWithJson("{\"selected_video\":\"playing.mp4\"}");
        ConfigManager.ShortcutPreset first = config.createPreset();
        ConfigManager.ShortcutPreset second = config.createPreset();

        assertTrue(config.setCurrentPreset(second.getId()));
        assertEquals(second.getId(), config.getCurrentPreset().getId());
        assertEquals("playing.mp4", config.getString(ConfigManager.KEY_SELECTED_VIDEO, ""));
        assertEquals(1, config.listPresets().stream()
                .filter(preset -> preset.getId().equals(config.getCurrentPreset().getId()))
                .count());
        assertEquals(first.getId(), config.listPresets().get(0).getId());
    }

    @Test
    public void bindingsAreIndependentBetweenPresets() {
        ConfigManager config = configWithJson("{}");
        ConfigManager.ShortcutPreset first = config.createPreset();
        ConfigManager.ShortcutPreset second = config.createPreset();

        assertTrue(config.bindPresetShortcut(first.getId(), ConfigManager.PRESET_SHORTCUT_DOT, "a.mp4"));
        assertTrue(config.bindPresetShortcut(first.getId(), ConfigManager.PRESET_SHORTCUT_LEFT, "b.mp4"));
        assertTrue(config.bindPresetShortcut(second.getId(), ConfigManager.PRESET_SHORTCUT_DOT, "c.mp4"));

        assertEquals("a.mp4", config.getPreset(first.getId()).getVideoName(ConfigManager.PRESET_SHORTCUT_DOT));
        assertEquals("b.mp4", config.getPreset(first.getId()).getVideoName(ConfigManager.PRESET_SHORTCUT_LEFT));
        assertEquals("c.mp4", config.getPreset(second.getId()).getVideoName(ConfigManager.PRESET_SHORTCUT_DOT));
        assertEquals("", config.getPreset(second.getId()).getVideoName(ConfigManager.PRESET_SHORTCUT_LEFT));
    }

    @Test
    public void deletingPresetsKeepsCurrentOrSelectsFirstRemaining() {
        ConfigManager config = configWithJson("{}");
        ConfigManager.ShortcutPreset first = config.createPreset();
        ConfigManager.ShortcutPreset second = config.createPreset();
        ConfigManager.ShortcutPreset third = config.createPreset();

        assertTrue(config.deletePreset(second.getId()));
        assertEquals(first.getId(), config.getCurrentPreset().getId());
        assertTrue(config.setCurrentPreset(third.getId()));
        assertTrue(config.deletePreset(third.getId()));
        assertEquals(first.getId(), config.getCurrentPreset().getId());
        assertTrue(config.deletePreset(first.getId()));
        assertEquals(0, config.listPresets().size());
        assertEquals("", config.getString(ConfigManager.KEY_CURRENT_PRESET_ID, ""));
    }

    @Test
    public void legacyGlobalBindingsMigrateOnceIntoPresetOne() {
        ConfigManager config = configWithJson("{"
                + "\"" + ConfigManager.KEY_SHORTCUT_DOT_VIDEO + "\":\"dot.mp4\","
                + "\"" + ConfigManager.KEY_SHORTCUT_LEFT_VIDEO + "\":\"left.mp4\""
                + "}");

        assertTrue(config.migrateLegacyShortcutBindingsIfNeeded());
        List<ConfigManager.ShortcutPreset> presets = config.listPresets();
        assertEquals(1, presets.size());
        assertEquals("预设一", presets.get(0).getName());
        assertEquals("dot.mp4", presets.get(0).getVideoName(ConfigManager.PRESET_SHORTCUT_DOT));
        assertEquals("left.mp4", presets.get(0).getVideoName(ConfigManager.PRESET_SHORTCUT_LEFT));
        assertEquals("", presets.get(0).getVideoName(ConfigManager.PRESET_SHORTCUT_RIGHT));
        assertFalse(config.migrateLegacyShortcutBindingsIfNeeded());
        assertEquals(1, config.listPresets().size());
    }

    @Test
    public void invalidBindingAndUnboundShortcutAreSafe() {
        ConfigManager config = configWithJson("{}");
        ConfigManager.ShortcutPreset preset = config.createPreset();

        assertFalse(config.bindPresetShortcut(preset.getId(), ConfigManager.PRESET_SHORTCUT_DOT, "../escape.mp4"));
        assertFalse(config.bindPresetShortcut(preset.getId(), ConfigManager.PRESET_SHORTCUT_DOT, "/tmp/escape.mp4"));
        assertEquals("", config.getCurrentPresetShortcutVideo(ConfigManager.PRESET_SHORTCUT_DOT));
        assertTrue(config.bindPresetShortcut(preset.getId(), ConfigManager.PRESET_SHORTCUT_DOT, "safe.mp4"));
        assertTrue(config.unbindPresetShortcut(preset.getId(), ConfigManager.PRESET_SHORTCUT_DOT));
        assertEquals("", config.getCurrentPresetShortcutVideo(ConfigManager.PRESET_SHORTCUT_DOT));
    }

    @Test
    public void audioFlagsAreForcedOffDuringV02Migration() {
        ConfigManager config = configWithJson("{"
                + "\"" + ConfigManager.KEY_PLAY_VIDEO_SOUND + "\":true,"
                + "\"" + ConfigManager.KEY_ENABLE_MIC_HOOK + "\":true,"
                + "\"" + ConfigManager.KEY_MIC_HOOK_MODE + "\":\"replace\""
                + "}");

        assertTrue(config.enforceAudioFeaturesDisabled());
        assertFalse(config.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, true));
        assertFalse(config.getBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, true));
        assertEquals(ConfigManager.MIC_MODE_MUTE,
                config.getString(ConfigManager.KEY_MIC_HOOK_MODE, ""));
        assertFalse(config.enforceAudioFeaturesDisabled());
    }
}
