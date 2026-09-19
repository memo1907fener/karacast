package com.safir.iptv.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.R
import com.safir.iptv.domain.model.SourceType
import com.safir.iptv.domain.model.SyncState
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.FullScreenLoading
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvFieldKind
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import androidx.compose.foundation.Image

@Composable
fun LoginScreen(
    onConnected: () -> Unit,
    prefillExisting: Boolean = false
) {
    val t = LocalStrings.current
    val viewModel: SetupViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(prefillExisting) {
        if (prefillExisting) viewModel.loadExisting()
    }

    val running = state.sync as? SyncState.Running
    if (running != null) {
        Box(Modifier.fillMaxSize().background(Brand.Background)) {
            FullScreenLoading(
                message = running.step,
                detail = t.longPlaylistHint
            )
        }
        return
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(listOf(Brand.Background, Brand.Surface))
            )
    ) {
        BrandPanel(t, Modifier.weight(1f).fillMaxHeight())

        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = t.connectProvider,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = t.connectProviderHint,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextSecondary
            )

            Spacer(Modifier.height(24.dp))
            SourceTypeSelector(
                m3uLabel = t.m3uPlaylist,
                selected = state.type,
                onSelect = viewModel::setType
            )

            Spacer(Modifier.height(20.dp))

            when (state.type) {
                SourceType.XTREAM -> {
                    TvTextField(
                        value = state.serverUrl,
                        onValueChange = viewModel::setServerUrl,
                        label = t.serverUrl,
                        placeholder = "http://server.beispiel.tv:8080",
                        kind = TvFieldKind.URL,
                        autoFocus = true
                    )
                    Spacer(Modifier.height(12.dp))
                    TvTextField(
                        value = state.username,
                        onValueChange = viewModel::setUsername,
                        label = t.username
                    )
                    Spacer(Modifier.height(12.dp))
                    TvTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = t.password,
                        kind = TvFieldKind.PASSWORD
                    )
                }

                SourceType.M3U -> {
                    TvTextField(
                        value = state.m3uUrl,
                        onValueChange = viewModel::setM3uUrl,
                        label = t.m3uUrlLabel,
                        placeholder = "http://server.beispiel.tv/get.php?username=…",
                        kind = TvFieldKind.URL,
                        autoFocus = true
                    )
                    Spacer(Modifier.height(12.dp))
                    TvTextField(
                        value = state.epgUrl,
                        onValueChange = viewModel::setEpgUrl,
                        label = t.epgUrlLabel,
                        placeholder = "http://server.beispiel.tv/xmltv.php?…",
                        kind = TvFieldKind.URL
                    )
                }
            }

            val failure = state.sync as? SyncState.Failed
            if (failure != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brand.Danger.copy(alpha = 0.12f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = failure.message,
                        color = Brand.Danger,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            TvButton(
                onClick = { viewModel.connect(onConnected) },
                // Never disabled. A disabled Button on TV cannot take focus at all,
                // so the D-pad silently skips it and an unfilled form looks like a
                // broken remote. Missing fields are reported on press instead.
                colors = ButtonDefaults.colors(
                    containerColor = Brand.Accent,
                    contentColor = Brand.Background,
                    focusedContainerColor = Brand.AccentSoft,
                    focusedContentColor = Brand.Background,
                    pressedContainerColor = Brand.AccentSoft,
                    pressedContentColor = Brand.Background
                )
            ) {
                Text(t.connectAndLoad, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = t.loginNote,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
        }
    }
}

@Composable
private fun BrandPanel(
    t: com.safir.iptv.ui.i18n.Strings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(56.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.app_banner),
            contentDescription = null,
            modifier = Modifier.width(260.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = t.brandHeadline,
            style = MaterialTheme.typography.headlineSmall,
            color = Brand.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = t.brandText,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.TextSecondary
        )
    }
}

@Composable
private fun SourceTypeSelector(
    m3uLabel: String,
    selected: SourceType,
    onSelect: (SourceType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceType.entries.forEach { type ->
            val isSelected = type == selected
            TvButton(
                onClick = { onSelect(type) },
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) Brand.Accent else Brand.SurfaceHigh,
                    contentColor = if (isSelected) Brand.Background else Brand.TextSecondary
                )
            ) {
                Text(
                    text = when (type) {
                        SourceType.XTREAM -> "Xtream Codes"
                        SourceType.M3U -> m3uLabel
                    },
                    fontSize = 14.sp
                )
            }
        }
    }
}
