package com.safir.iptv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safir.iptv.domain.model.SourceType
import com.safir.iptv.domain.model.SyncState
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.login.SetupMode
import com.safir.iptv.ui.login.SetupViewModel


/**
 * Zugang einrichten: Xtream Codes oder M3U-Link. Dieselbe Logik wie am
 * Fernseher ([SetupViewModel]) — nur mit Tastatur statt Fernbedienung.
 */
@Composable
fun MobileSetupScreen(
    mode: SetupMode,
    onConnected: () -> Unit,
    onCancel: (() -> Unit)?
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: SetupViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(mode) { viewModel.setMode(mode) }
    if (onCancel != null) BackHandler(enabled = !state.isBusy) { onCancel() }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onCancel != null) {
                IconButton(onClick = onCancel, enabled = !state.isBusy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { BrandTitle() }
            Text(
                if (mode == SetupMode.ADD) t.sources.addTitle else m.setupTitle,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(t.loginNote, fontSize = 14.sp, color = Kc.TextSecondary)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(SourceType.XTREAM to m.xtream, SourceType.M3U to m.m3u).forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        enabled = !state.isBusy
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(t.sources.nameLabel) },
                singleLine = true,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.type == SourceType.XTREAM) {
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = viewModel::setServerUrl,
                    label = { Text(t.serverUrl) },
                    placeholder = { Text("http://server:8080") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text(t.username) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = { Text(t.password) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) m.hidePassword else m.showPassword
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = state.m3uUrl,
                    onValueChange = viewModel::setM3uUrl,
                    label = { Text(t.m3uUrlLabel) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = state.epgUrl,
                onValueChange = viewModel::setEpgUrl,
                label = { Text(t.epgUrlLabel) },
                singleLine = true,
                enabled = !state.isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            when (val sync = state.sync) {
                is SyncState.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sync.step, fontSize = 14.sp)
                    val fraction = sync.progress
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(t.longPlaylistHint, fontSize = 12.sp, color = Kc.TextSecondary)
                }

                is SyncState.Failed -> Text(sync.message, color = Kc.Danger, fontSize = 14.sp)
                else -> Unit
            }

            Button(
                onClick = { viewModel.connect(onConnected) },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(t.connectAndLoad, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
