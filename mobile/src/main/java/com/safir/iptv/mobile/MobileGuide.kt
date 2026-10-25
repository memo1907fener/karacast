package com.safir.iptv.mobile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.Program
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.guide.GuideViewModel
import com.safir.iptv.ui.i18n.LocalStrings

import kotlinx.coroutines.delay

/** Wie breit eine Minute im Raster ist: eine halbe Stunde ≈ ein lesbarer Titel. */
private val MINUTE: Dp = 4.dp
private val ROW_HEIGHT: Dp = 60.dp
private val CHANNEL_COLUMN: Dp = 76.dp

/**
 * Das Programm: Sender untereinander, Sendungen als Blöcke nebeneinander, eine
 * orange Linie für „jetzt“. Alle Zeilen teilen sich eine waagrechte Scroll-
 * Position — wer in einer Zeile wischt, schiebt das ganze Raster.
 *
 * Ein Tipp auf eine Sendung öffnet ein Blatt: laufende einschalten, vergangene
 * aus dem Archiv von vorn (wenn der Sender eins hat), künftige vormerken.
 */
@Composable
fun MobileGuideScreen(
    onPlayLive: (Channel, String) -> Unit,
    onPlayArchive: (Channel, Program) -> Unit
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: GuideViewModel = viewModel(factory = AppViewModelFactory)
    val allChannels by viewModel.channels.collectAsStateWithLifecycle()
    // Trennzeilen des Anbieters haben kein Programm — im Raster wären sie leere Zeilen.
    val channels = remember(allChannels) { allChannels.filterNot { isSeparator(it.name) } }
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    val windowStart = viewModel.windowStartMs
    val windowEnd = viewModel.windowEndMs
    val totalMinutes = ((windowEnd - windowStart) / 60_000L).toInt()
    val gridWidth = MINUTE * totalMinutes

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Beim Öffnen steht „jetzt“ ein Stück vom linken Rand entfernt.
    LaunchedEffect(scroll.maxValue > 0) {
        if (scroll.maxValue <= 0) return@LaunchedEffect
        val minutes = ((System.currentTimeMillis() - windowStart) / 60_000L - 30).coerceAtLeast(0)
        val px = with(density) { (MINUTE * minutes.toInt()).roundToPx() }
        scroll.scrollTo(px.coerceAtMost(scroll.maxValue))
    }

    var opened by remember { mutableStateOf<Pair<Channel, Program>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t.guide, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text(clockOf(now), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))

        // Zeitleiste
        Row(Modifier.fillMaxWidth().height(22.dp).clipToBounds()) {
            Spacer(Modifier.width(CHANNEL_COLUMN))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(gridWidth).fillMaxHeight()) {
                    var minute = 0
                    while (minute < totalMinutes) {
                        val at = windowStart + minute * 60_000L
                        Text(
                            clockOf(at),
                            fontSize = 12.sp,
                            color = Kc.TextSecondary,
                            modifier = Modifier.offset(x = MINUTE * minute + 4.dp)
                        )
                        minute += 30
                    }
                    NowMarker(windowStart, now, label = true)
                }
            }
        }

        when {
            channels.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            !loading && programs.isEmpty() -> EmptyHint(m.noGuide, m.noGuideHint)

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    GuideRow(
                        channel = channel,
                        programs = channel.epgId?.let { programs[it] }.orEmpty(),
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        gridWidth = gridWidth,
                        now = now,
                        scroll = scroll,
                        onOpen = { program -> opened = channel to program }
                    )
                }
            }
        }
    }

    opened?.let { (channel, program) ->
        val key = "${channel.id}@${program.startMs}"
        ProgramSheet(
            channel = channel,
            program = program,
            now = now,
            reminded = key in reminders,
            onWatch = {
                opened = null
                onPlayLive(channel, viewModel.categoryId)
            },
            onArchive = {
                opened = null
                onPlayArchive(channel, program)
            },
            onRemind = { viewModel.toggleReminder(channel, program) },
            onDismiss = { opened = null }
        )
    }
}

@Composable
private fun NowMarker(windowStart: Long, now: Long, label: Boolean = false) {
    val minutes = ((now - windowStart) / 60_000f)
    if (label) {
        Box(
            Modifier
                .offset(x = MINUTE * minutes - 2.dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(Kc.Accent)
        )
    } else {
        Box(
            Modifier
                .offset(x = MINUTE * minutes)
                .width(2.dp)
                .fillMaxHeight()
                .background(Kc.Accent)
        )
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programs: List<Program>,
    windowStart: Long,
    windowEnd: Long,
    gridWidth: Dp,
    now: Long,
    scroll: ScrollState,
    onOpen: (Program) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT + 4.dp).clipToBounds()) {
        Column(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Kc.Surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ChannelBadge(channel.logoUrl, channel.name, Modifier.size(width = 44.dp, height = 26.dp), corner = 6.dp)
            Spacer(Modifier.height(3.dp))
            Text(
                channel.number.takeIf { it > 0 }?.toString() ?: channel.name,
                fontSize = 10.sp,
                color = Kc.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(gridWidth).fillMaxHeight()) {
                programs.forEach { program ->
                    val start = program.startMs.coerceAtLeast(windowStart)
                    val end = program.endMs.coerceAtMost(windowEnd)
                    if (end <= start) return@forEach
                    val left = MINUTE * ((start - windowStart) / 60_000f)
                    val width = (MINUTE * ((end - start) / 60_000f) - 4.dp).coerceAtLeast(2.dp)
                    val live = program.isLiveAt(now)
                    val past = program.endMs <= now
                    Column(
                        Modifier
                            .offset(x = left)
                            .padding(vertical = 2.dp)
                            .width(width)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    // Was jetzt läuft, sticht heraus; Vergangenes tritt zurück.
                                    live -> Color(0xFF4A2E2A)
                                    past -> Color(0xFF151A30)
                                    else -> Kc.Surface
                                }
                            )
                            .clickable { onOpen(program) }
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            program.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (past) Kc.TextSecondary else Kc.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${clockOf(program.startMs)}–${clockOf(program.endMs)}",
                            fontSize = 11.sp,
                            color = Kc.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
                NowMarker(windowStart, now)
            }
        }
    }
}

@Composable
private fun ProgramSheet(
    channel: Channel,
    program: Program,
    now: Long,
    reminded: Boolean,
    onWatch: () -> Unit,
    onArchive: () -> Unit,
    onRemind: () -> Unit,
    onDismiss: () -> Unit
) {
    val m = LocalMobileStrings.current
    val live = program.isLiveAt(now)
    val past = program.endMs <= now
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Kc.Surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelBadge(channel.logoUrl, channel.name, Modifier.size(width = 48.dp, height = 32.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${channel.name} · ${clockOf(program.startMs)} – ${clockOf(program.endMs)}",
                    fontSize = 13.sp,
                    color = Kc.TextSecondary
                )
            }
            Text(program.title, style = MaterialTheme.typography.titleLarge)
            if (live) {
                ThinProgress(program.progressAt(now), Modifier.fillMaxWidth(), height = 4.dp)
            }
            if (program.description.isNotBlank()) {
                Text(program.description, fontSize = 14.sp, color = Color(0xFFC9CFDC), lineHeight = 20.sp, maxLines = 8)
            }
            Spacer(Modifier.height(4.dp))
            when {
                live -> Button(onClick = onWatch, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(m.watchLive, fontWeight = FontWeight.Bold)
                }

                past -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (channel.hasArchive) {
                        Button(onClick = onArchive, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(Icons.Filled.Replay, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(m.watchFromStart, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(onClick = onWatch, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(m.watchLive)
                    }
                }

                else -> OutlinedButton(
                    onClick = onRemind,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(if (reminded) Modifier.border(1.dp, Kc.Accent, RoundedCornerShape(24.dp)) else Modifier)
                ) {
                    Icon(
                        if (reminded) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = if (reminded) Kc.Accent else Kc.TextPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (reminded) m.reminderOn else m.remind)
                }
            }
        }
    }
}
