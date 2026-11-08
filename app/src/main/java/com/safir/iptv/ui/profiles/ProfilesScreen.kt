package com.safir.iptv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.Profile
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvFieldKind
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Die Farben der Profilbilder. Der Index steht im [Profile]. */
val ProfileColors = listOf(
    Color(0xFFFF8A1F), Color(0xFF3D8BFF), Color(0xFF3DDC84), Color(0xFFE5484D),
    Color(0xFFB66DFF), Color(0xFF00B8C4), Color(0xFFFFC53D), Color(0xFFFF6FB0)
)

fun profileColor(profile: Profile): Color = ProfileColors[profile.color.mod(ProfileColors.size)]

/**
 * „Wer schaut?“ — die Profilauswahl.
 *
 * Erscheint beim Start, sobald es mehr als ein Profil gibt, und jederzeit über
 * das Profilbild auf der Startseite. Mit „Profile verwalten“ öffnet ein Klick
 * auf ein Profil den Editor statt es zu wählen.
 */
@Composable
fun ProfilesScreen(onDone: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val container = (context.applicationContext as IptvApp).container
    val settings = container.settings
    val profiles by settings.profiles.collectAsStateWithLifecycle()
    val activeId by settings.activeProfileId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    val activeRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { activeRequester.requestFocus() }
    }

    fun pick(profile: Profile) {
        val current = settings.activeProfile
        // Aus dem Kinderprofil heraus nur mit dem Code der Kindersicherung.
        if (current.kids && !profile.kids && settings.parentalPin.isNotEmpty() && profile.id != current.id) {
            pinFor = profile
            return
        }
        scope.launch {
            container.profiles.switchTo(profile.id)
            onDone()
        }
    }

    BackHandler(enabled = editing != null || pinFor != null || managing) {
        when {
            pinFor != null -> pinFor = null
            editing != null -> editing = null
            else -> managing = false
        }
    }

    Box(Modifier.fillMaxSize().background(Brand.Background)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (managing) t.profiles.manage else t.profiles.whoIsWatching,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            if (managing) {
                Spacer(Modifier.height(6.dp))
                Text(t.profiles.manageHint, fontSize = 14.sp, color = Brand.TextSecondary)
            }
            Spacer(Modifier.height(32.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileTile(
                        profile = profile,
                        name = displayName(profile, t.profiles.defaultName),
                        badge = if (profile.kids) t.profiles.kidsBadge else null,
                        editing = managing,
                        onClick = {
                            if (managing) editing = profile else pick(profile)
                        },
                        modifier = if (profile.id == activeId) Modifier.focusRequester(activeRequester) else Modifier
                    )
                }
                if (profiles.size < MAX_PROFILES && !settings.activeProfile.kids) {
                    item(key = "add") {
                        AddTile(label = t.profiles.add) {
                            editing = Profile(id = 0, name = "", color = profiles.size % ProfileColors.size)
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
            // Das Kinderprofil kann keine Profile verwalten — sonst wäre die Sperre
            // mit zwei Klicks umgangen.
            if (!settings.activeProfile.kids) {
                TvButton(onClick = { managing = !managing }) {
                    Text(if (managing) t.profiles.done else t.profiles.manage)
                }
            }
        }

        editing?.let { profile ->
            ProfileEditor(
                profile = profile,
                canDelete = profile.id > 1,
                onSave = { saved ->
                    settings.saveProfile(saved)
                    editing = null
                },
                onDelete = {
                    scope.launch {
                        container.profiles.delete(profile.id)
                        editing = null
                    }
                },
                onCancel = { editing = null }
            )
        }

        pinFor?.let { target ->
            PinDialog(
                expected = settings.parentalPin,
                onOk = {
                    pinFor = null
                    scope.launch {
                        container.profiles.switchTo(target.id)
                        onDone()
                    }
                },
                onCancel = { pinFor = null }
            )
        }
    }
}

fun displayName(profile: Profile, fallback: String): String = profile.name.ifBlank { fallback }

/** Das runde Profilbild mit dem Anfangsbuchstaben. */
@Composable
fun ProfileAvatar(profile: Profile, name: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(profileColor(profile)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    name: String,
    badge: String?,
    editing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Brand.SurfaceHigh,
            pressedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextSecondary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = shape)
        ),
        modifier = modifier.width(150.dp).alsoTappable(onClick)
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(profile, name, 96.dp)
                if (editing) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Brand.Background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Brand.TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            badge?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand.Background,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brand.Live)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AddTile(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Brand.SurfaceHigh,
            pressedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextSecondary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = shape)
        ),
        modifier = Modifier.width(150.dp).alsoTappable(onClick)
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Brand.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(text = label, fontSize = 14.sp, maxLines = 2)
        }
    }
}

/** Name, Farbe, Kinderprofil — und Löschen, außer beim ersten Profil. */
@Composable
private fun ProfileEditor(
    profile: Profile,
    canDelete: Boolean,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val t = LocalStrings.current
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var color by remember(profile.id) { mutableStateOf(profile.color) }
    var kids by remember(profile.id) { mutableStateOf(profile.kids) }
    val preview = profile.copy(name = name, color = color, kids = kids)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.Surface)
                .padding(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(preview, displayName(preview, t.profiles.defaultName), 56.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (profile.id == 0) t.profiles.newProfile else t.profiles.editProfile,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary
                )
            }
            Spacer(Modifier.height(18.dp))
            TvTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = t.profiles.name,
                placeholder = t.profiles.defaultName
            )
            Spacer(Modifier.height(16.dp))
            Text(t.profiles.color, fontSize = 13.sp, color = Brand.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileColors.forEachIndexed { index, swatch ->
                    ColorDot(swatch, selected = index == color) { color = index }
                }
            }
            Spacer(Modifier.height(16.dp))
            TvButton(onClick = { kids = !kids }) {
                Text((if (kids) "✓ " else "") + t.profiles.kids)
            }
            Spacer(Modifier.height(6.dp))
            Text(t.profiles.kidsHint, fontSize = 12.sp, color = Brand.TextSecondary)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = { onSave(preview.copy(name = name.trim())) }) { Text(t.profiles.save) }
                if (canDelete) TvButton(onClick = onDelete) { Text(t.profiles.delete) }
                TvButton(onClick = onCancel) { Text(t.profiles.cancel) }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            focusedContainerColor = color,
            pressedContainerColor = color
        ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) {
                Border(border = BorderStroke(3.dp, Color.White), shape = CircleShape)
            } else {
                Border.None
            },
            focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = CircleShape)
        ),
        modifier = Modifier.size(36.dp).alsoTappable(onClick)
    ) {}
}

/** Der Code der Kindersicherung, bevor das Kinderprofil verlassen wird. */
@Composable
private fun PinDialog(expected: String, onOk: () -> Unit, onCancel: () -> Unit) {
    val t = LocalStrings.current
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.Surface)
                .padding(28.dp)
        ) {
            Text(t.profiles.pinToLeave, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.TextPrimary)
            Spacer(Modifier.height(16.dp))
            TvTextField(
                value = entered,
                onValueChange = {
                    entered = it.filter(Char::isDigit).take(4)
                    wrong = false
                },
                label = t.parental.pinTitle,
                kind = TvFieldKind.PASSWORD
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text(t.profiles.wrongPin, fontSize = 13.sp, color = Brand.Danger)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = {
                    if (entered == expected) onOk() else {
                        wrong = true
                        entered = ""
                    }
                }) { Text(t.profiles.confirm) }
                TvButton(onClick = onCancel) { Text(t.profiles.cancel) }
            }
        }
    }
}

private const val MAX_PROFILES = 6
