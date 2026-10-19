package no.vardir.hodd.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.hodd.core.PhotoCrop
import no.vardir.hodd.data.PhotoEdits
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoEditor(source: File, onAccepted: (String) -> Unit, onCancel: () -> Unit, message: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var left by rememberSaveable(source.path) { mutableFloatStateOf(0f) }
    var top by rememberSaveable(source.path) { mutableFloatStateOf(0f) }
    var right by rememberSaveable(source.path) { mutableFloatStateOf(1f) }
    var bottom by rememberSaveable(source.path) { mutableFloatStateOf(1f) }
    var brightness by rememberSaveable(source.path) { mutableFloatStateOf(0f) }
    var contrast by rememberSaveable(source.path) { mutableFloatStateOf(1f) }
    var turns by rememberSaveable(source.path) { mutableIntStateOf(0) }
    var precise by rememberSaveable(source.path) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember(source.path) { mutableStateOf<String?>(null) }
    var bitmap by remember(source.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source.path) {
        try {
            bitmap = withContext(Dispatchers.IO) { PhotoEdits.decode(source, 1280) }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            bitmap = null
            error = "This photo could not be opened. Choose another photo or try taking it again."
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    val preview = remember(bitmap, turns) { bitmap?.let { PhotoEdits.rotate(it, turns) } }
    DisposableEffect(preview) { onDispose { if (preview !== bitmap) preview?.recycle() } }
    val crop = PhotoCrop(left, top, right, bottom)
    fun setCrop(next: PhotoCrop) { left = next.left; top = next.top; right = next.right; bottom = next.bottom }
    fun resetCrop() = setCrop(PhotoCrop())
    BackHandler { if (!saving) onCancel() }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit photo") }, navigationIcon = {
            IconButton(onClick = onCancel, enabled = !saving) { Icon(Icons.Outlined.Close, "Cancel photo editing") }
        }, actions = {
            IconButton(onClick = { turns = (turns + 1) % 4; resetCrop() }, enabled = preview != null && !saving) {
                Icon(Icons.Outlined.RotateRight, "Rotate photo clockwise")
            }
            TextButton(onClick = { turns = 0; resetCrop(); brightness = 0f; contrast = 1f }, enabled = !saving) { Text("Reset") }
        }) },
        bottomBar = { Surface(shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
            Button(onClick = {
                if (!saving) {
                    saving = true
                    val target = File(source.parentFile, "${UUID.randomUUID()}.jpg")
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { PhotoEdits.export(source, target, crop, brightness, contrast, turns) }
                            onAccepted(target.name)
                        } catch (failure: Exception) {
                            target.delete()
                            if (failure is CancellationException) throw failure
                            message("Could not save the edited photo. Your current cover is unchanged; try again.")
                        } finally { saving = false }
                    }
                }
            }, enabled = preview != null && !saving && error == null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).heightIn(min = 52.dp)) {
                if (saving) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                Text(if (saving) "Saving photo…" else "Use photo")
            }
        } },
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val controlsHeight = minOf(280.dp, maxHeight * 0.48f)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (preview != null) CropPreview(preview, crop, brightness, contrast, !saving, ::setCrop)
                    else if (error != null) Text(error!!, Modifier.padding(20.dp))
                    else CircularProgressIndicator(Modifier.padding(20.dp))
                }
                Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)) {
                    Text("Drag the corners to crop. Drag inside the frame to move it.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text("Brightness · ${(brightness * 100).roundToInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(brightness, { brightness = it }, valueRange = -0.5f..0.5f, enabled = !saving,
                        modifier = Modifier.testTag("photo-brightness").semantics { contentDescription = "Brightness" })
                    Text("Contrast · ${(contrast * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(contrast, { contrast = it }, valueRange = 0.5f..2f, enabled = !saving,
                        modifier = Modifier.testTag("photo-contrast").semantics { contentDescription = "Contrast" })
                    TextButton(onClick = { precise = !precise }, enabled = !saving, contentPadding = PaddingValues(0.dp)) {
                        Text(if (precise) "Hide crop controls" else "Precise crop controls")
                    }
                    if (precise) {
                        Text("Horizontal crop", style = MaterialTheme.typography.labelLarge)
                        RangeSlider(left..right, { range ->
                            if (range.endInclusive - range.start >= PhotoCrop.MIN_SIZE) setCrop(PhotoCrop(range.start, top, range.endInclusive, bottom))
                        }, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Horizontal crop" })
                        Text("Vertical crop", style = MaterialTheme.typography.labelLarge)
                        RangeSlider(top..bottom, { range ->
                            if (range.endInclusive - range.start >= PhotoCrop.MIN_SIZE) setCrop(PhotoCrop(left, range.start, right, range.endInclusive))
                        }, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Vertical crop" })
                    }
                }
            }
        }
    }
}

@Composable
private fun CropPreview(bitmap: Bitmap, crop: PhotoCrop, brightness: Float, contrast: Float, enabled: Boolean, change: (PhotoCrop) -> Unit) {
    val currentCrop by rememberUpdatedState(crop)
    val currentChange by rememberUpdatedState(change)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val filter = remember(brightness, contrast) { ColorFilter.colorMatrix(ColorMatrix(PhotoEdits.colorMatrix(brightness, contrast))) }
    Canvas(Modifier.fillMaxSize().testTag("photo-crop-preview").semantics { contentDescription = "Photo crop preview. Precise crop controls are available below." }
        .pointerInput(bitmap, enabled) {
            if (!enabled) return@pointerInput
            var handle = ""
            var width = 1f; var height = 1f
            val hit = 32.dp.toPx()
            detectDragGestures(onDragStart = { position ->
                val scale = min(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                width = bitmap.width * scale; height = bitmap.height * scale
                val x = (size.width - width) / 2; val y = (size.height - height) / 2
                val c = currentCrop
                val px = position.x - x; val py = position.y - y
                val nearL = abs(px - c.left * width) <= hit; val nearR = abs(px - c.right * width) <= hit
                val nearT = abs(py - c.top * height) <= hit; val nearB = abs(py - c.bottom * height) <= hit
                handle = if (px >= c.left * width - hit && px <= c.right * width + hit && py >= c.top * height - hit && py <= c.bottom * height + hit) {
                    (if (nearL) "l" else if (nearR) "r" else "") + (if (nearT) "t" else if (nearB) "b" else "")
                } else "outside"
            }, onDrag = { pointer, delta ->
                pointer.consume()
                if (handle != "outside") currentChange(if (handle.isEmpty()) currentCrop.move(delta.x / width, delta.y / height) else currentCrop.resize(handle, delta.x / width, delta.y / height))
            })
        }) {
        drawRect(Color(0xFF17171C))
        val scale = min(size.width / bitmap.width, size.height / bitmap.height)
        val width = bitmap.width * scale; val height = bitmap.height * scale
        val x = (size.width - width) / 2; val y = (size.height - height) / 2
        drawRect(Color.White, Offset(x, y), Size(width, height))
        drawImage(image, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)), colorFilter = filter)
        val l = x + crop.left * width; val t = y + crop.top * height
        val r = x + crop.right * width; val b = y + crop.bottom * height
        val shade = Color.Black.copy(alpha = 0.6f)
        drawRect(shade, Offset(x, y), Size(width, t - y))
        drawRect(shade, Offset(x, b), Size(width, y + height - b))
        drawRect(shade, Offset(x, t), Size(l - x, b - t))
        drawRect(shade, Offset(r, t), Size(x + width - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        for (third in 1..2) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(l + (r - l) * third / 3, t), Offset(l + (r - l) * third / 3, b), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.45f), Offset(l, t + (b - t) * third / 3), Offset(r, t + (b - t) * third / 3), 1.dp.toPx())
        }
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
            drawCircle(Violet, 7.dp.toPx(), it); drawCircle(Color.White, 7.dp.toPx(), it, style = Stroke(2.dp.toPx()))
        }
    }
}
