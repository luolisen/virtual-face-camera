package io.github.alanlaw.vfc.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import io.github.alanlaw.vfc.ConfigManager
import io.github.alanlaw.vfc.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PresetUiState(
    val id: String,
    val name: String,
    val bindings: Map<String, String>
) {
    fun videoFor(shortcutKey: String): String = bindings[shortcutKey].orEmpty()
}

data class MainUiState(
    val isModuleDisabled: Boolean = false,
    val forcePrivateDir: Boolean = false,
    val disableToast: Boolean = false,
    val enableRandomPlay: Boolean = false,
    val enablePhotoFake: Boolean = false,
    val videoAspectMode: String = ConfigManager.ASPECT_MODE_FIT,
    val presets: List<PresetUiState> = emptyList(),
    val currentPresetId: String? = null,

    val overlayControlEnabled: Boolean = false,
    val hasPermission: Boolean = false,
    val isXposedActive: Boolean = false,
    val targetAppsCount: Int = 0,
    val originalVideoName: String? = null,
    val latestVersion: String? = null,

    // Stream mode
    val mediaSourceType: String = ConfigManager.MEDIA_SOURCE_LOCAL,
    val streamUrl: String = "",
    val streamAutoReconnect: Boolean = true,
    val streamLocalFallback: Boolean = true,
    val streamTransportHint: String = "auto",
    val streamTimeoutMs: Long = 8000L,

    // Injection Engine Mode
    val injectionMode: String = ConfigManager.INJECTION_MODE_LSPOSED
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configManager = ConfigManager(false)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var hasAutoInjectedStartup = false

    init {
        // Keep App-side writes on the same Provider-backed path as the overlay
        // and target-process readers so changes are delivered immediately.
        configManager.setContext(application.applicationContext)
        configManager.migrateV02Configuration()
        loadConfig()
        checkLatestVersion()
    }

    fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            configManager.migrateV02Configuration()
            val currentMode = configManager.getString(ConfigManager.KEY_INJECTION_MODE, ConfigManager.INJECTION_MODE_LSPOSED)
            val presetState = readPresetState()
            
            _uiState.update { currentState ->
                currentState.copy(
                    isModuleDisabled = configManager.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false),
                    forcePrivateDir = configManager.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false),
                    disableToast = configManager.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false),
                    enableRandomPlay = configManager.getBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, false),
                    enablePhotoFake = configManager.getBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, false),
                    videoAspectMode = configManager.getString(
                        ConfigManager.KEY_VIDEO_ASPECT_MODE, ConfigManager.ASPECT_MODE_FIT),
                    presets = presetState.first,
                    currentPresetId = presetState.second,
                    overlayControlEnabled = configManager.getBoolean(ConfigManager.KEY_OVERLAY_CONTROL_ENABLED, false),
                    targetAppsCount = configManager.targetPackages.size,
                    originalVideoName = configManager.getString(ConfigManager.KEY_ORIGINAL_VIDEO_NAME, null),
                    // Stream config
                    mediaSourceType = configManager.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL),
                    streamUrl = configManager.getString(ConfigManager.KEY_STREAM_URL, ""),
                    streamAutoReconnect = configManager.getBoolean(ConfigManager.KEY_STREAM_AUTO_RECONNECT, true),
                    streamLocalFallback = configManager.getBoolean(ConfigManager.KEY_STREAM_LOCAL_FALLBACK, true),
                    streamTransportHint = configManager.getString(ConfigManager.KEY_STREAM_TRANSPORT_HINT, "auto"),
                    streamTimeoutMs = configManager.getLong(ConfigManager.KEY_STREAM_TIMEOUT_MS, 8000L),
                    injectionMode = currentMode
                )
            }

            // 仅在应用启动首次，且已处于 Root 模式且未运行时尝试启动，避免 onResume 重复触发 Root 授权
            if (currentMode == ConfigManager.INJECTION_MODE_CAMSERVER && !hasAutoInjectedStartup) {
                hasAutoInjectedStartup = true
                if (!io.github.alanlaw.vfc.utils.CameraServerBridge.isRunning()) {
                    try {
                        io.github.alanlaw.vfc.utils.CameraServerBridge.injectCameraServer(getApplication())
                    } catch (e: Exception) {
                        io.github.alanlaw.vfc.utils.LogUtil.log("【CS】启动 Root 推流引擎异常: ${e.message}")
                    }
                }
            }
        }
    }

    fun setModuleDisabled(disabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_DISABLE_MODULE, disabled)
            _uiState.update { it.copy(isModuleDisabled = disabled) }
        }
    }

    fun setForcePrivateDir(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, enabled)
            _uiState.update { it.copy(forcePrivateDir = enabled) }
        }
    }

    fun setDisableToast(disabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_DISABLE_TOAST, disabled)
            _uiState.update { it.copy(disableToast = disabled) }
        }
    }

    fun setEnableRandomPlay(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, enabled)
            _uiState.update { it.copy(enableRandomPlay = enabled) }
        }
    }

    fun setEnablePhotoFake(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, enabled)
            _uiState.update { it.copy(enablePhotoFake = enabled) }
        }
    }

    fun setOverlayControlEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_OVERLAY_CONTROL_ENABLED, enabled)
            _uiState.update { it.copy(overlayControlEnabled = enabled) }
        }
    }

    fun setVideoAspectMode(mode: String) {
        val normalized = if (mode == ConfigManager.ASPECT_MODE_CROP) {
            ConfigManager.ASPECT_MODE_CROP
        } else {
            ConfigManager.ASPECT_MODE_FIT
        }
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_VIDEO_ASPECT_MODE, normalized)
            _uiState.update { it.copy(videoAspectMode = normalized) }
        }
    }

    fun createPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.createPreset()
            refreshPresetState()
        }
    }

    fun renamePreset(presetId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configManager.renamePreset(presetId, name)) {
                refreshPresetState()
            }
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configManager.deletePreset(presetId)) {
                refreshPresetState()
            }
        }
    }

    fun setCurrentPreset(presetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configManager.setCurrentPreset(presetId)) {
                refreshPresetState()
            }
        }
    }

    fun bindPresetShortcut(presetId: String, shortcutKey: String, videoName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configManager.bindPresetShortcut(presetId, shortcutKey, videoName)) {
                refreshPresetState()
            }
        }
    }

    fun unbindPresetShortcut(presetId: String, shortcutKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configManager.unbindPresetShortcut(presetId, shortcutKey)) {
                refreshPresetState()
            }
        }
    }

    private fun readPresetState(): Pair<List<PresetUiState>, String?> {
        val presets = configManager.listPresets().map { preset ->
            PresetUiState(
                id = preset.id,
                name = preset.name,
                bindings = preset.bindings.toMap()
            )
        }
        val currentId = configManager.getCurrentPreset()?.id
        return presets to currentId
    }

    private suspend fun refreshPresetState() {
        val presetState = readPresetState()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(presets = presetState.first, currentPresetId = presetState.second)
            }
        }
    }

    // ---- Stream config setters ----

    fun setMediaSourceType(type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, type)
            _uiState.update { it.copy(mediaSourceType = type) }
        }
    }

    fun setStreamUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_STREAM_URL, url)
            _uiState.update { it.copy(streamUrl = url) }
        }
    }

    fun setStreamAutoReconnect(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_STREAM_AUTO_RECONNECT, enabled)
            _uiState.update { it.copy(streamAutoReconnect = enabled) }
        }
    }

    fun setStreamLocalFallback(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_STREAM_LOCAL_FALLBACK, enabled)
            _uiState.update { it.copy(streamLocalFallback = enabled) }
        }
    }

    fun setStreamTransportHint(hint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_STREAM_TRANSPORT_HINT, hint)
            _uiState.update { it.copy(streamTransportHint = hint) }
        }
    }

    fun setStreamTimeoutMs(timeout: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setLong(ConfigManager.KEY_STREAM_TIMEOUT_MS, timeout)
            _uiState.update { it.copy(streamTimeoutMs = timeout) }
        }
    }

    fun switchInjectionMode(context: Context, targetMode: String, onRootFailed: () -> Unit) {
        if (targetMode == ConfigManager.INJECTION_MODE_CAMSERVER) {
            viewModelScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.requesting_root_permission),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val hasRoot = io.github.alanlaw.vfc.utils.CameraServerBridge.requestRootPermission()
                if (!hasRoot) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.root_permission_required),
                            Toast.LENGTH_LONG
                        ).show()
                        onRootFailed()
                    }
                    return@launch
                }

                setInjectionMode(ConfigManager.INJECTION_MODE_CAMSERVER)
                val injectSuccess = io.github.alanlaw.vfc.utils.CameraServerBridge.injectCameraServer(context)

                withContext(Dispatchers.Main) {
                    if (injectSuccess) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.root_mode_activated),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.root_inject_failed_tip),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            setInjectionMode(ConfigManager.INJECTION_MODE_LSPOSED)
        }
    }

    fun setInjectionMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldMode = _uiState.value.injectionMode
            configManager.setString(ConfigManager.KEY_INJECTION_MODE, mode)
            _uiState.update { it.copy(injectionMode = mode) }

            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】==================== 运行模式切换 ====================")
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】模式状态变动: $oldMode -> $mode")
            if (mode == ConfigManager.INJECTION_MODE_CAMSERVER) {
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】当前生效模式: CameraServer 系统底层 Native Hook 模式 (全局生效/零沙箱特征)")
                val pid = io.github.alanlaw.vfc.utils.CameraServerBridge.getCameraServerPid()
                val injected = io.github.alanlaw.vfc.utils.CameraServerBridge.isHookInjected()
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】系统 cameraserver PID: $pid | 注入状态: $injected")
            } else {
                io.github.alanlaw.vfc.utils.CameraServerBridge.stopFrameFeeder()
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】当前生效模式: LSPosed 沙箱 App 注入模式")
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】Xposed 激活状态: ${_uiState.value.isXposedActive} | 目标包数量: ${_uiState.value.targetAppsCount}")
            }
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】【ModeSwitch】======================================================")
        }
    }

    fun updatePermissionStatus(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
    }

    fun updateXposedStatus(isActive: Boolean) {
        _uiState.update { it.copy(isXposedActive = isActive) }
    }

    fun setLanguage(context: Context, language: String) {
        io.github.alanlaw.vfc.utils.LocaleHelper.setLocale(context, language)
        // Restart app to apply changes
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun checkLatestVersion() {
        _uiState.update { it.copy(latestVersion = null) }
    }
}
