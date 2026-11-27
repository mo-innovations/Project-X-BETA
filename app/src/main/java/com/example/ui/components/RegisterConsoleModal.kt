package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.ConsoleModel
import com.example.network.WakeOnLanSender
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardSurface
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSubtleFill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.RedDanger
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterConsoleModal(
    onRegister: (name: String, model: ConsoleModel, liveId: String, lanIp: String) -> Unit,
    onSyncFromAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(ConsoleModel.SERIES_X) }
    var liveId by remember { mutableStateOf("") }
    var lanIp by remember { mutableStateOf("192.168.1.") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = EmeraldPrimary,
        unfocusedBorderColor = BentoBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = EmeraldPrimary,
        unfocusedLabelColor = TextSecondary,
        focusedContainerColor = ObsidianBase,
        unfocusedContainerColor = ObsidianBase
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BentoCardSurface)
                .border(1.dp, BentoBorder, RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
                .testTag("register_console_modal")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = XboxIcons.Console,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Register Console",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                ConsoleModel.entries.forEach { option ->
                    val selected = option == model
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) EmeraldSubtleFill else ObsidianBase)
                            .border(
                                1.5.dp,
                                if (selected) EmeraldPrimary else BentoBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { model = option }
                            .padding(8.dp)
                            .testTag("register_model_${option.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConsoleModelThumbnail(
                            model = option,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                        )
                        Text(
                            text = option.displayName,
                            color = if (selected) EmeraldPrimary else TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = { Text("Console Name (e.g. Living Room)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_name_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = liveId,
                onValueChange = { liveId = it.uppercase(); errorMsg = null },
                label = { Text("Xbox Live ID (e.g. FD00C24A91E73B10)") },
                singleLine = true,
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_liveid_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = lanIp,
                onValueChange = { lanIp = it; errorMsg = null },
                label = { Text("Local LAN IPv4") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_ip_input")
            )

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMsg.orEmpty(),
                    color = RedDanger,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSyncFromAccount()
                    },
                    border = BorderStroke(1.dp, BentoBorder),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Sync Account", color = TextPrimary)
                }

                Button(
                    onClick = {
                        val cleanLiveId = liveId.trim().uppercase()
                        val cleanIp = lanIp.trim()
                        when {
                            cleanLiveId.length < 8 -> errorMsg = "Enter a valid 8–32 character Xbox Live ID."
                            !WakeOnLanSender.isValidLanIp(cleanIp) -> errorMsg = "Enter a valid IPv4 address (e.g. 192.168.1.50)."
                            else -> onRegister(name, model, cleanLiveId, cleanIp)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = ObsidianBase
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("register_save_button")
                ) {
                    Text("Save Console", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
