package io.legado.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VideoSpeedRows = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f).chunked(4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = videoContentColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 480.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = colors.surface,
        contentColor = colors.content,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets.safeDrawing },
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(32.dp, 4.dp)
                .background(colors.content.copy(alpha = .2f), RoundedCornerShape(2.dp)))
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
            Text("倍速", Modifier.padding(top = 8.dp, bottom = 18.dp),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoSpeedRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { speed ->
                            val selected = currentSpeed == speed
                            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.accent else colors.cell)
                                .selectable(selected, role = Role.RadioButton, onClick = { onSelect(speed) })
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center) {
                                Text("${speed}×", color = if (selected) colors.onAccent else colors.content,
                                    fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
