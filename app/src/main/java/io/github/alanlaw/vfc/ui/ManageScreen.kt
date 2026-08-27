package io.github.alanlaw.vfc.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alanlaw.vfc.ConfigManager
import io.github.alanlaw.vfc.R

private data class PendingPresetBinding(
    val presetId: String,
    val shortcutKey: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    mainViewModel: MainViewModel,
    mediaViewModel: MediaManagerViewModel
) {
    val mainUiState by mainViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var expandedPresetId by remember { mutableStateOf<String?>(null) }
    var pendingBinding by remember { mutableStateOf<PendingPresetBinding?>(null) }
    var renameTarget by remember { mutableStateOf<PresetUiState?>(null) }
    var deleteTarget by remember { mutableStateOf<PresetUiState?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val binding = pendingBinding
        pendingBinding = null
        if (uri == null || binding == null) {
            return@rememberLauncherForActivityResult
        }
        mediaViewModel.importVideo(uri) { importedName ->
            if (importedName == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.preset_video_import_failed),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                mainViewModel.bindPresetShortcut(binding.presetId, binding.shortcutKey, importedName)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { mainViewModel.createPreset() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_preset)
                )
            }
        }
    ) { innerPadding ->
        if (mainUiState.presets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_presets),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_presets_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { mainViewModel.createPreset() }) {
                        Text(stringResource(R.string.create_preset))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.presets_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                items(mainUiState.presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        isCurrent = preset.id == mainUiState.currentPresetId,
                        expanded = preset.id == expandedPresetId,
                        onToggleExpanded = {
                            expandedPresetId = if (expandedPresetId == preset.id) null else preset.id
                        },
                        onSetCurrent = { mainViewModel.setCurrentPreset(preset.id) },
                        onRename = { renameTarget = preset },
                        onDelete = { deleteTarget = preset },
                        onBind = { shortcutKey ->
                            pendingBinding = PendingPresetBinding(preset.id, shortcutKey)
                            videoPicker.launch(arrayOf("video/*"))
                        },
                        onUnbind = { shortcutKey ->
                            mainViewModel.unbindPresetShortcut(preset.id, shortcutKey)
                        }
                    )
                }
            }
        }
    }

    val targetToRename = renameTarget
    if (targetToRename != null) {
        var name by remember(targetToRename.id) { mutableStateOf(targetToRename.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_preset_title)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.preset_name_label)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = name.trim()
                        if (normalized.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.preset_name_required),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            mainViewModel.renamePreset(targetToRename.id, normalized)
                            renameTarget = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.negative))
                }
            }
        )
    }

    val targetToDelete = deleteTarget
    if (targetToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_preset_title)) },
            text = {
                Text(stringResource(R.string.delete_preset_message, targetToDelete.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.deletePreset(targetToDelete.id)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.delete_preset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.negative))
                }
            }
        )
    }
}

@Composable
private fun PresetCard(
    preset: PresetUiState,
    isCurrent: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetCurrent: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBind: (String) -> Unit,
    onUnbind: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.current_preset),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                RadioButton(
                    selected = isCurrent,
                    onClick = onSetCurrent
                )
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.rename_preset)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_preset),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                    ConfigManager.getPresetShortcutKeys().forEach { shortcutKey ->
                        PresetBindingRow(
                            label = shortcutLabel(shortcutKey),
                            videoName = preset.videoFor(shortcutKey),
                            onBind = { onBind(shortcutKey) },
                            onUnbind = { onUnbind(shortcutKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetBindingRow(
    label: String,
    videoName: String,
    onBind: () -> Unit,
    onUnbind: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBind)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = videoName.ifEmpty { stringResource(R.string.shortcut_unbound) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (videoName.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (videoName.isNotEmpty()) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.preset_binding_actions)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shortcut_unbind)) },
                        onClick = {
                            menuExpanded = false
                            onUnbind()
                        }
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun shortcutLabel(shortcutKey: String): String = when (shortcutKey) {
    ConfigManager.PRESET_SHORTCUT_DOT -> "点"
    ConfigManager.PRESET_SHORTCUT_LEFT -> "左"
    ConfigManager.PRESET_SHORTCUT_RIGHT -> "右"
    ConfigManager.PRESET_SHORTCUT_OPEN -> "张"
    ConfigManager.PRESET_SHORTCUT_BLINK -> "眨"
    else -> shortcutKey
}
