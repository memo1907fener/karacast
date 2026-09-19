package com.safir.iptv.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.CategoryGroup
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand

/**
 * Builds the user's own category bundles: pick a group on the left, tick the
 * provider categories that belong to it on the right. Every change is written
 * immediately — there is no save button to hunt for with a remote.
 */
@Composable
fun GroupsScreen(onBack: () -> Unit, onEditChannels: (String) -> Unit) {
    val t = LocalStrings.current
    val viewModel: GroupsViewModel = viewModel(factory = AppViewModelFactory)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()

    LaunchedEffect(groups) {
        if (editingId == null) groups.firstOrNull()?.let { viewModel.select(it.id) }
    }

    val editing = groups.firstOrNull { it.id == editingId }

    Row(Modifier.fillMaxSize().background(Brand.Background)) {

        // ------------------------------------------------------------- groups
        Column(
            Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(Brand.Surface)
                .padding(vertical = 28.dp)
        ) {
            Text(
                text = t.ownGroups,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))

            if (groups.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = t.noGroupYet,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupRow(
                            group = group,
                            selected = group.id == editingId,
                            onSelect = { viewModel.select(group.id) },
                            onUp = { viewModel.move(group.id, -1) },
                            onDown = { viewModel.move(group.id, +1) },
                            categoriesLabel = t.nCategories(group.categoryIds.size),
                            hiddenLabel = t.nHidden(group.hiddenChannelIds.size)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TvButton(
                    onClick = viewModel::createGroup,
                    colors = ButtonDefaults.colors(
                        containerColor = Brand.Accent,
                        contentColor = Brand.Background,
                        focusedContainerColor = Brand.AccentSoft,
                        focusedContentColor = Brand.Background
                    )
                ) {
                    Text(t.newGroup, fontWeight = FontWeight.SemiBold)
                }
                TvButton(
                    onClick = { editingId?.let(onEditChannels) },
                    colors = ButtonDefaults.colors(
                        containerColor = Brand.SurfaceHigh,
                        contentColor = Brand.TextPrimary,
                        focusedContainerColor = Brand.Accent,
                        focusedContentColor = Brand.Background
                    )
                ) {
                    Text(t.editChannels)
                }
                Text(
                    text = t.editChannelsHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand.TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                TvButton(onClick = onBack) { Text(t.back) }
            }
        }

        // --------------------------------------------------------- the editor
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 36.dp, vertical = 28.dp)
        ) {
            if (editing == null) {
                EmptyState(
                    title = t.noGroupSelected,
                    message = t.noGroupSelectedHint
                )
                return@Column
            }

            TvTextField(
                value = editing.name,
                onValueChange = viewModel::rename,
                label = t.groupName
            )

            Spacer(Modifier.height(18.dp))
            // The button is measured first and the heading takes what is left, so a
            // long label can never squeeze it into a three-line blob.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = t.categoriesInGroup,
                        style = MaterialTheme.typography.titleSmall,
                        color = Brand.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = t.nSelected(editing.categoryIds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.Accent
                    )
                }
                TvButton(
                    onClick = viewModel::deleteEditing,
                    colors = ButtonDefaults.colors(
                        containerColor = Brand.Danger.copy(alpha = 0.18f),
                        contentColor = Brand.Danger
                    )
                ) {
                    Text(t.delete, maxLines = 1)
                }
            }

            Spacer(Modifier.height(14.dp))

            if (categories.isEmpty()) {
                EmptyState(
                    title = t.noCategories,
                    message = t.noCategoriesHint
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        val checked = category.id in editing.categoryIds
                        CategoryCheckRow(
                            title = category.name,
                            detail = t.nChannels(category.channelCount),
                            checked = checked,
                            onToggle = { viewModel.toggleMember(category.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: CategoryGroup,
    selected: Boolean,
    onSelect: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    categoriesLabel: String,
    hiddenLabel: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onSelect,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (selected) Brand.SurfaceHigh else Color.Transparent,
                focusedContainerColor = Brand.Accent,
                pressedContainerColor = Brand.Accent,
                contentColor = if (selected) Brand.TextPrimary else Brand.TextSecondary,
                focusedContentColor = Brand.Background,
                pressedContentColor = Brand.Background
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.weight(1f).alsoTappable(onSelect)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(categoriesLabel)
                        if (group.hiddenChannelIds.isNotEmpty()) {
                            append(" · ")
                            append(hiddenLabel)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        SmallSquareButton("▲", onUp)
        Spacer(Modifier.width(4.dp))
        SmallSquareButton("▼", onDown)
    }
}

@Composable
private fun SmallSquareButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.SurfaceHigh,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(32.dp).alsoTappable(onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CategoryCheckRow(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (checked) Brand.SurfaceHigh else Brand.Surface,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().alsoTappable(onToggle)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (checked) Brand.Accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (checked) "✓" else "·",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (checked) Brand.Background else Brand.TextSecondary
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(text = detail, style = MaterialTheme.typography.labelSmall)
        }
    }
}
