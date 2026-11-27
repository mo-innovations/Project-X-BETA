package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier
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
