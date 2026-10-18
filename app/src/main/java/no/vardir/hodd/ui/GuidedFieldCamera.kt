package no.vardir.hodd.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import no.vardir.hodd.core.FieldScanRegion
import java.io.File

/** Shared viewport + EXIF-aware crop keep the scan frame aligned with OCR. */
@Composable
internal fun GuidedFieldCamera(
    field: String,
    output: File,
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val capturedCallback by rememberUpdatedState(onCaptured)
    val errorCallback by rememberUpdatedState(onError)
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val active = remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner, previewView) {
        active.value = true
        var disposed = false
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var preview: Preview? = null
        var photo: ImageCapture? = null
        previewView.doOnAttach { view ->
            view.doOnLayout {
                providerFuture.addListener({
                    if (!disposed) {
                        try {
                            val provider = providerFuture.get()
                            val viewport = previewView.viewPort ?: error("Camera preview is not ready")
                            val rotation = previewView.display.rotation
                            val previewCase = Preview.Builder().setTargetRotation(rotation).build()
                            previewCase.surfaceProvider = previewView.surfaceProvider
                            val captureCase = ImageCapture.Builder().setTargetRotation(rotation)
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                            preview = previewCase; photo = captureCase
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                UseCaseGroup.Builder().setViewPort(viewport).addUseCase(previewCase).addUseCase(captureCase).build())
                            capture = captureCase
                        } catch (_: Exception) { errorCallback("Could not start the camera. Check camera access and try again.") }
                    }
                }, executor)
            }
        }
        onDispose {
            disposed = true
            active.value = false
            capture = null
            if (providerFuture.isDone) {
                try {
                    val cases = listOfNotNull(preview, photo).toTypedArray()
                    providerFuture.get().unbind(*cases)
                } catch (_: Exception) { }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().testTag("field-scan-frame")) {
            val left = size.width * FieldScanRegion.LEFT
            val top = size.height * FieldScanRegion.TOP
            val width = size.width * (FieldScanRegion.RIGHT - FieldScanRegion.LEFT)
            val height = size.height * (FieldScanRegion.BOTTOM - FieldScanRegion.TOP)
            val shade = Color.Black.copy(alpha = 0.55f)
            drawRect(shade, size = Size(size.width, top))
            drawRect(shade, topLeft = Offset(0f, top + height), size = Size(size.width, size.height - top - height))
            drawRect(shade, topLeft = Offset(0f, top), size = Size(left, height))
            drawRect(shade, topLeft = Offset(left + width, top), size = Size(size.width - left - width, height))
            drawRect(Color.White, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(width = 3.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !capturing) { Text("Cancel", color = Color.White) }
                Text("Scan $field", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Only text inside the frame will be read", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    val camera = capture ?: return@Button
                    capturing = true
                    camera.takePicture(ImageCapture.OutputFileOptions.Builder(output).build(),
                        ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                if (active.value) { capturing = false; capturedCallback(output) }
                                else output.delete()
                            }
                            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                output.delete()
                                if (active.value) { capturing = false; errorCallback("Could not capture text. Try again.") }
                            }
                        })
                }, enabled = capture != null && !capturing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field-capture-button"),
                    shape = RoundedCornerShape(40.dp)) {
                    Text(if (capturing) "Capturing…" else "Capture text")
                }
            }
        }
    }
}
