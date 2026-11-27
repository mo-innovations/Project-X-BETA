package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.model.ConnectionMode
import com.example.model.LogSeverity
import com.example.model.TelemetrySnapshot
import com.example.model.TerminalLogEntry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.RedDanger
import com.example.ui.theme.SidebarSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XboxTileSurface
import com.example.update.AppUpdater
import com.example.update.ReleaseInfo
import com.example.update.UpdateCheckResult
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LiveTelemetryDrawer(
    expanded: Boolean,
    connectionMode: ConnectionMode,
    telemetry: TelemetrySnapshot,
    logs: List<TerminalLogEntry>,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SidebarSurface)
                .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("telemetry_drawer_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = when (connectionMode) {
                ConnectionMode.IDLE -> TextMuted
                ConnectionMode.SIGNALING -> AmberWarning
                ConnectionMode.STREAMING_XHOME,
                ConnectionMode.STREAMING_LOOPBACK -> EmeraldPrimary
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Diagnostics & Log (${logs.size})",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${telemetry.fps} FPS • ${telemetry.bitrateKbps / 1000f} Mbps",
                color = TextSecondary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = "Expand telemetry terminal",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBase)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("telemetry_drawer_content")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryStatChip("FPS", "${telemetry.fps}", Modifier.weight(1f))
                    TelemetryStatChip("BITRATE", "${telemetry.bitrateKbps}k", Modifier.weight(1f))
                    TelemetryStatChip("JITTER", "${"%.1f".format(telemetry.jitterMs)}ms", Modifier.weight(1f))
                    TelemetryStatChip("LOSS", "${"%.1f".format(telemetry.packetLossPct)}%", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SidebarSurface)
                        .border(1.dp, BentoBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true
                ) {
                    items(logs.asReversed()) { entry ->
                        val tagColor = when (entry.severity) {
                            LogSeverity.SUCCESS -> EmeraldPrimary
                            LogSeverity.WARN -> AmberWarning
                            LogSeverity.ERROR -> RedDanger
                            LogSeverity.INFO -> TextSecondary
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "[${entry.timestamp}] ",
                                color = TextMuted,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${entry.tag}: ",
                                color = tagColor,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp
                            )
                            Text(
                                text = entry.message,
                                color = TextPrimary,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        }

        AppUpdatesRow()
    }
}

private enum class UpdateUiPhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

@Composable
private fun AppUpdatesRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdateUiPhase.IDLE) }
    var statusText by remember { mutableStateOf("v${BuildConfig.VERSION_NAME} • GitHub Releases") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var availableRelease by remember { mutableStateOf<ReleaseInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(XboxTileSurface)
            .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("app_updates_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "App Updates",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusText,
                    color = if (phase == UpdateUiPhase.ERROR) RedDanger else TextSecondary,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (phase == UpdateUiPhase.READY_TO_INSTALL && downloadedApk != null) {
                Button(
                    onClick = {
                        val file = downloadedApk ?: return@Button
                        AppUpdater.installUpdate(context, file).onFailure { err ->
                            phase = UpdateUiPhase.ERROR
                            statusText = err.message ?: "Installer could not start"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = ObsidianBase
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("install_update_button")
                ) {
                    Text("Install Update", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (phase == UpdateUiPhase.CHECKING || phase == UpdateUiPhase.DOWNLOADING) return@OutlinedButton
                        phase = UpdateUiPhase.CHECKING
                        statusText = "Checking mo-innovations/Project-X-BETA..."
                        downloadProgress = 0f
                        downloadedApk = null
                        scope.launch {
                            when (val result = AppUpdater.checkForUpdates(BuildConfig.VERSION_NAME)) {
                                is UpdateCheckResult.UpToDate -> {
                                    phase = UpdateUiPhase.UP_TO_DATE
                                    statusText = "v${BuildConfig.VERSION_NAME} is up to date"
                                }
                                is UpdateCheckResult.UpdateAvailable -> {
                                    availableRelease = result.release
                                    phase = UpdateUiPhase.DOWNLOADING
                                    statusText = "Downloading ${result.release.tagName}..."
                                    AppUpdater.downloadApk(
                                        context = context,
                                        release = result.release,
                                        onProgress = { p -> downloadProgress = p }
                                    ).onSuccess { file ->
                                        downloadedApk = file
                                        phase = UpdateUiPhase.READY_TO_INSTALL
                                        statusText = "${result.release.tagName} ready to install"
                                    }.onFailure { err ->
                                        phase = UpdateUiPhase.ERROR
                                        statusText = err.message ?: "Download failed"
                                    }
                                }
                                is UpdateCheckResult.Error -> {
                                    phase = UpdateUiPhase.ERROR
                                    statusText = result.message
                                }
                            }
                        }
                    },
                    enabled = phase != UpdateUiPhase.CHECKING && phase != UpdateUiPhase.DOWNLOADING,
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("check_updates_button")
                ) {
                    if (phase == UpdateUiPhase.CHECKING || phase == UpdateUiPhase.DOWNLOADING) {
                        CircularProgressIndicator(
                            color = EmeraldPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when (phase) {
                            UpdateUiPhase.CHECKING -> "Checking..."
                            UpdateUiPhase.DOWNLOADING -> "${(downloadProgress * 100).toInt()}%"
                            else -> "Check for Updates"
                        },
                        color = EmeraldPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (phase == UpdateUiPhase.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { downloadProgress.coerceIn(0f, 1f) },
                color = EmeraldPrimary,
                trackColor = BentoBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .testTag("update_download_progress")
            )
        } else if (phase == UpdateUiPhase.READY_TO_INSTALL && availableRelease != null) {
            Text(
                text = "Current: v${BuildConfig.VERSION_NAME} → New: ${availableRelease?.tagName}",
                color = EmeraldPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TelemetryStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SidebarSurface)
            .border(1.dp, BentoBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp
        )
        Text(
            text = value,
            color = EmeraldPrimary,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
