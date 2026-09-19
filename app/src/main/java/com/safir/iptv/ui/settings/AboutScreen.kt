package com.safir.iptv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.BuildConfig
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand

/**
 * Your own details — the three things on this page that are yours and not the
 * app's. Write between the quotes and the section appears on screen; leave one
 * empty and it stays a dimmed hint, so a half-filled page reads as a form rather
 * than as a bug.
 *
 * Everything else on this page — what Karacast is, the small print, the licences —
 * is translated like the rest of the app and lives in Strings.kt under `info`.
 * These three do not need translating: a name and an e-mail address read the same
 * in every language.
 *
 * Multi-line text works too:
 *
 *     const val CONTACT = """
 *         Erste Zeile.
 *         Zweite Zeile.
 *     """
 */
private object AboutText {

    /** Who made it: name, company, role. */
    const val DEVELOPER = """
        sternweb IT-Dienstleistungen
        Augasse 82, 8053 Graz, Österreich
    """

    /** E-Mail, phone, WhatsApp — whatever people should use to reach you. */
    const val CONTACT = """
        office@sternweb.at
        +43 660 3743933
        Mo–Fr 08:00–17:00 Uhr
    """

    /** Website, shop, social media. */
    const val WEBSITE = "karacast.de"
}

/**
 * The presentation page: who is behind the app, how to get in touch, the legal
 * small print. Deliberately a page of its own rather than a line in settings —
 * on a TV there is room, and it is the first thing a new user looks for.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val t = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {

        // --------------------------------------------------------------- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brand.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = Brand.Background,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Karacast IPTV",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    t.versionLabel(BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand.TextSecondary
                )
                if (t.info.tagline.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        t.info.tagline,
                        style = MaterialTheme.typography.titleSmall,
                        color = Brand.AccentSoft
                    )
                }
            }
            TvButton(onClick = onBack) { Text(t.back) }
        }

        Spacer(Modifier.height(28.dp))

        // ------------------------------------------------------------- sections
        Column(
            Modifier.widthIn(max = 1000.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section(t.aboutSection, t.info.body, t.aboutHint)
            Section(t.developer, AboutText.DEVELOPER, t.developerHint)
            Section(t.contact, AboutText.CONTACT, t.contactHint)
            Section(t.website, AboutText.WEBSITE, t.websiteHint)
            Section(t.legal, t.info.legal, t.legalHint)
            Section(t.info.licensesTitle, t.info.licenses, t.info.licensesTitle)
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = t.aboutDisclaimer,
            style = MaterialTheme.typography.labelSmall,
            color = Brand.TextSecondary,
            modifier = Modifier.widthIn(max = 1000.dp)
        )
    }
}

/**
 * @param text what the owner wrote — shown when it is there.
 * @param hint what belongs here — shown dimmed while [text] is still empty, so the
 *   page reads as a form waiting to be filled rather than as a bug.
 */
@Composable
private fun Section(title: String, text: String, hint: String) {
    // Each card can take the focus, which is the only reason the D-pad can walk
    // down this page at all: a screen of plain text has nothing for the remote to
    // move to, so ▼ did nothing and only a mouse could scroll. Focus moving into a
    // card also scrolls it into view, which is what makes the page work on a sofa.
    var focused by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Brand.SurfaceHigh else Brand.Surface)
            .border(
                BorderStroke(2.dp, if (focused) Brand.Accent else Color.Transparent),
                RoundedCornerShape(14.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary
        )
        Spacer(Modifier.height(10.dp))

        val body = text.trimIndent().trim()
        if (body.isEmpty()) {
            Text(
                text = "— $hint",
                fontSize = 15.sp,
                color = Brand.TextSecondary.copy(alpha = 0.6f)
            )
        } else {
            Text(
                text = body,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                color = Brand.TextPrimary
            )
        }
    }
}
