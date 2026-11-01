package app.henachti.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.verify.TefillinDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Live camera only. There is deliberately no gallery picker anywhere in this screen:
 * the frame that is verified is the frame that was just captured.
 */
class VerifyActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private var imageCapture: ImageCapture? = null
    private var detector: TefillinDetector? = null
    private val lensFacing = mutableIntStateOf(CameraSelector.LENS_FACING_BACK)

    /** Random liveness challenge, re-rolled for every attempt, so a saved photo never matches. */
    private val challenge = mutableIntStateOf(newChallenge())

    private fun newChallenge(): Int = (1..4).random()

    private fun fingersWord(n: Int): String = when (n) {
        1 -> "אצבע אחת"
        2 -> "שתי אצבעות"
        3 -> "שלוש אצבעות"
        else -> "ארבע אצבעות"
    }

    private sealed class Phase {
        data object Camera : Phase()
        data object Checking : Phase()
        data class Rejected(val reason: String) : Phase()
        data class Failed(val message: String) : Phase()
        data class Verified(val note: String) : Phase()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(this)

        setContent {
            HenachtiTheme {
                var phase by remember { mutableStateOf<Phase>(Phase.Camera) }
                var hasPermission by remember { mutableStateOf(cameraGranted()) }
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasPermission = granted }

                LaunchedEffect(Unit) {
                    if (!hasPermission) permission.launch(Manifest.permission.CAMERA)
                }

                VerifyScreen(
                    phase = phase,
                    hasPermission = hasPermission,
                    onCapture = {
                        phase = Phase.Checking
                        takePhoto { result -> phase = result }
                    },
                    onRetry = {
                        challenge.intValue = newChallenge()
                        phase = Phase.Camera
                    },
                    onClose = { finish() },
                    onRequestPermission = { permission.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        detector?.close()
        detector = null
        super.onDestroy()
    }

    private fun takePhoto(onResult: (Phase) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onResult(Phase.Failed("המצלמה עדיין לא מוכנה. נסה שוב."))
            return
        }
        val fingers = challenge.intValue
        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = try {
                        image.toUprightBitmap()
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (bitmap == null) {
                        onResult(Phase.Failed("לא הצלחתי לעבד את התמונה. נסה שוב."))
                        return
                    }
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.Default) {
                            val d = detector ?: TefillinDetector(this@VerifyActivity).also { detector = it }
                            d.analyze(bitmap, fingers)
                        }
                        onResult(
                            when (result) {
                                is TefillinDetector.Result.Verified -> {
                                    prefs.markVerified(policy.currentDay())
                                    prefs.verifiedCount = prefs.verifiedCount + 1
                                    Phase.Verified(result.note)
                                }
                                is TefillinDetector.Result.Rejected -> Phase.Rejected(result.reason)
                                is TefillinDetector.Result.Error -> Phase.Failed(result.message)
                            }
                        )
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Phase.Failed("הצילום נכשל: ${exception.message}"))
                }
            }
        )
    }

    @Composable
    private fun VerifyScreen(
        phase: Phase,
        hasPermission: Boolean,
        onCapture: () -> Unit,
        onRetry: () -> Unit,
        onClose: () -> Unit,
        onRequestPermission: () -> Unit,
    ) {
        var facing by lensFacing

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(44.dp))
            Eyebrow("אימות")
            Spacer(Modifier.height(8.dp))
            val fingers = challenge.intValue
            Text(
                "הרם ${fingersWord(fingers)} ביד עם התפילין.",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "בלי האגודל. הבית על הזרוע, הרצועות על היד והאצבעות המורמות צריכים להיראות בבירור. " +
                    "צילום חי בלבד, המספר מתחלף בכל ניסיון.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            Spacer(Modifier.height(18.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Palette.Ink)
            ) {
                if (hasPermission) {
                    CameraPreview(facing)
                } else {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("צריך גישה למצלמה כדי לאמת.", color = Palette.Paper)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRequestPermission) {
                            Text("אשר גישה", color = Palette.Paper)
                        }
                    }
                }

                when (phase) {
                    Phase.Checking -> Overlay(title = "בודק...", body = "", spinner = true)
                    is Phase.Verified -> Overlay(title = "הנחת. יום טוב.", body = phase.note)
                    is Phase.Rejected -> Overlay(title = "לא אושר.", body = phase.reason)
                    is Phase.Failed -> Overlay(title = "משהו השתבש.", body = phase.message)
                    Phase.Camera -> Unit
                }
            }

            Spacer(Modifier.height(16.dp))

            when (phase) {
                Phase.Camera -> {
                    InkButton("צלם ואמת", onCapture, Modifier.fillMaxWidth(), enabled = hasPermission)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            facing = if (facing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        }) {
                            Text("החלף מצלמה", color = Palette.Muted)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClose) {
                            Text("חזרה", color = Palette.Muted)
                        }
                    }
                }
                Phase.Checking -> Spacer(Modifier.height(56.dp))
                is Phase.Verified -> InkButton("המשך", onClose, Modifier.fillMaxWidth())
                is Phase.Rejected, is Phase.Failed -> {
                    InkButton("נסה שוב", onRetry, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("חזרה", color = Palette.Muted)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun Overlay(title: String, body: String, spinner: Boolean = false) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Ink.copy(alpha = 0.78f))
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (spinner) {
                    CircularProgressIndicator(color = Palette.Paper)
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Paper,
                    textAlign = TextAlign.Center,
                )
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.PaperDeep,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    @Composable
    private fun CameraPreview(facing: Int) {
        val context = LocalContext.current
        val previewView = remember {
            PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        DisposableEffect(facing) {
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                    p.unbindAll()
                    p.bindToLifecycle(this@VerifyActivity, selector, preview, capture)
                    imageCapture = capture
                } catch (e: Exception) {
                    imageCapture = null
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                provider?.unbindAll()
                imageCapture = null
            }
        }
    }
}

/** Bitmap from the JPEG capture, rotated upright and shrunk so the longer side is at most 1280 px. */
private fun ImageProxy.toUprightBitmap(maxSide: Int = 1280): Bitmap {
    val buffer = planes[0].buffer
    val raw = ByteArray(buffer.remaining())
    buffer.get(raw)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

    val decoded = BitmapFactory.decodeByteArray(
        raw, 0, raw.size,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    ) ?: throw IllegalStateException("decode failed")

    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation != 0) {
        Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
    } else decoded

    return if (upright.config == Bitmap.Config.ARGB_8888) upright
    else upright.copy(Bitmap.Config.ARGB_8888, false)
}
