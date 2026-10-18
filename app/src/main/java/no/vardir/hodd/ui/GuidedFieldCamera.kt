package no.vardir.hodd.ui

import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/** CameraX preview and capture share a ViewPort so the visible center strip matches OCR crop. */
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
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val viewport = previewView.viewPort
                if (viewport == null) {
                    onError("Camera preview is not ready. Try again.")
                } else {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        UseCaseGroup.Builder().setViewPort(viewport).addUseCase(preview).addUseCase(photo).build()
                    )
                    capture = photo
                }
            } catch (error: Exception) { onError("Could not start the camera.") }
        }
        previewView.post { providerFuture.addListener(listener, executor) }
        onDispose {
            capture = null
            if (providerFuture.isDone) {
                try { providerFuture.get().unbindAll() } catch (_: Exception) { }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val top = size.height * 0.375f
            val height = size.height * 0.25f
            drawRect(Color.Black.copy(alpha = 0.55f), size = Size(size.width, top))
            drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, top + height),
                size = Size(size.width, size.height - top - height))
            val inset = size.width * 0.06f
            drawRoundRect(Color.White, topLeft = Offset(inset, top),
                size = Size(size.width - inset * 2, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f), style = Stroke(width = 3.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                Text("Scan " + field, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Keep the text inside the highlighted strip", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val camera = capture ?: return@Button
                        capturing = true
                        camera.takePicture(
                            ImageCapture.OutputFileOptions.Builder(output).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    capturing = false
                                    onCaptured(output)
                                }
                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    capturing = false
                                    onError("Could not capture text. Try again.")
                                }
                            }
                        )
                    },
                    enabled = capture != null && !capturing,
                    shape = RoundedCornerShape(40.dp)
                ) { Text(if (capturing) "Capturing…" else "Capture text") }
            }
        }
    }
}
