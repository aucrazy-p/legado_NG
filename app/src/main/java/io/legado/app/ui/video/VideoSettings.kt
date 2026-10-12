package io.legado.app.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.model.VideoPlay
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun VideoSettings(onDismiss: () -> Unit) {
    var auto by remember { mutableStateOf(VideoPlay.autoPlay) }
    var full by remember { mutableStateOf(VideoPlay.startFull) }
    var progress by remember { mutableStateOf(VideoPlay.fullBottomProgressBar) }
    var speed by remember { mutableIntStateOf(VideoPlay.longPressSpeed) }
    var picker by remember { mutableStateOf(false) }
    Dialog(onDismiss) {
        NgDialog(stringResource(R.string.config_settings), titleFontSize = 18.sp,
            titleFontWeight = FontWeight.Normal, actions = {}) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VideoSettingCheck(stringResource(R.string.auto_play), auto) { auto = it; VideoPlay.autoPlay = it }
                if (auto) VideoSettingCheck(stringResource(R.string.start_full), full) { full = it; VideoPlay.startFull = it }
                VideoSettingCheck(stringResource(R.string.full_bottom_progress), progress) { progress = it; VideoPlay.fullBottomProgressBar = it }
                Text(stringResource(R.string.press_speed_summary, speed / 10f),
                    Modifier.fillMaxWidth().clickable { picker = true }.padding(vertical = 12.dp),
                    color = Color(NgTheme.colors.onSurface), fontSize = 16.sp)
            }
        }
    }
    if (picker) Dialog({ picker = false }) {
        val list = rememberLazyListState((speed - 5).coerceIn(0, 55))
        val selected by remember { derivedStateOf { (list.firstVisibleItemIndex + 5).coerceIn(5, 60) } }
        var input by remember { mutableStateOf("${speed / 10f}") }
        LaunchedEffect(selected) { input = "${selected / 10f}" }
        val entered = input.toFloatOrNull()?.takeIf { it.isFinite() && it in .5f..6f }?.times(10)?.roundToInt()
        NgDialog(stringResource(R.string.press_speed), actions = {
            NgDialogTextActionButton(stringResource(R.string.btn_default_s), {
                speed = 30; VideoPlay.longPressSpeed = 30; picker = false
            })
            NgDialogTextActionButton(stringResource(R.string.cancel), { picker = false })
            NgDialogTextActionButton(stringResource(R.string.ok), {
                entered?.let { speed = it; VideoPlay.longPressSpeed = it }; picker = false
            }, enabled = entered != null)
        }) {
            Box(Modifier.fillMaxWidth().height(144.dp)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(144.dp),
                contentPadding = PaddingValues(vertical = 48.dp),
                flingBehavior = rememberSnapFlingBehavior(list), horizontalAlignment = Alignment.CenterHorizontally) {
                items(56, key = { it + 5 }) { index ->
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        Text("${(index + 5) / 10f}", color = if (selected == index + 5) Color.Transparent else Color(NgTheme.colors.onSurfaceVariant), fontSize = 20.sp)
                    }
                }
            }
            BasicTextField(input, { input = it }, Modifier.align(Alignment.Center).width(80.dp),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(color = Color(NgTheme.colors.primary), fontSize = 20.sp, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun VideoSettingCheck(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color(NgTheme.colors.onSurface), fontSize = 16.sp)
        Checkbox(checked, onChecked)
    }
}
