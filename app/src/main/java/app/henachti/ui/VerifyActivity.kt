package app.henachti.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.verify.WindingDetector
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live camera only. The preview is analysed a few times a second; the forearm has to sit
 * inside the guide with the strap windings crossing it. There is no gallery, no capture
 * button and no network: when enough windings are seen on several consecutive frames,
 * the day is verified.
 */
class VerifyActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val lensFacing = mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
    private val liveBands = mutableIntStateOf(0)
    private val phase = mutableStateOf<Phase>(Phase.Scanning)
    private val recent = ArrayDeque<Boolean>()
    private var lastAnalysedAt = 0L
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private sealed class Phase {
        data object Scanning : Phase()
        data class Verified(val note: String) : Phase()
        data class Failed(val message: String) : Phase()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(this)

        setContent {
            HenachtiTheme {
                var hasPermission by remember { mutableStateOf(cameraGranted()) }
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasPermission = granted }

                LaunchedEffect(Unit) {
                    if (!hasPermission) permission.launch(Manifest.permission.CAMERA)
                }

                VerifyScreen(
                    hasPermission = hasPermission,
                    onClose = { finish() },
                    onRequestPermission = { permission.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** Runs on the analysis thread, a few times per second. */
    private fun onFrame(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysedAt < 180L || phase.value !is Phase.Scanning) return
            lastAnalysedAt = now
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation != 0) {
                Bitmap.createBitmap(
                    raw, 0, 0, raw.width, raw.height,
                    Matrix().apply { postRotate(rotation.toFloat()) }, false
                )
            } else raw
            val reading = WindingDetector.read(upright)
            runOnUiThread { handleReading(reading.bands) }
        } catch (e: Exception) {
            // skip this frame
        } finally {
            image.close()
        }
    }

    private fun handleReading(bands: Int) {
        if (phase.value !is Phase.Scanning) return
        liveBands.intValue = bands
        recent.addLast(bands >= WindingDetector.REQUIRED)
        while (recent.size > 6) recent.removeFirst()
        if (recent.count { it } >= 4) {
            prefs.markVerified(policy.currentDay())
            prefs.verifiedCount = prefs.verifiedCount + 1
            phase.value = Phase.Verified("זוהו $bands כריכות על האמה.")
        }
    }

    @Composable
    private fun VerifyScreen(
        hasPermission: Boolean,
        onClose: () -> Unit,
        onRequestPermission: () -> Unit,
    ) {
        var facing by lensFacing
        val bands = liveBands.intValue
        val current = phase.value
        var showHint by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(15_000)
            showHint = true
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(44.dp))
            Eyebrow("אימות")
            Spacer(Modifier.height(8.dp))
            Text(
                "הכנס את האמה עם הכריכות למסגרת.",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "כל האמה, מהמרפק עד כף היד, לרוחב המסגרת, כך שהרצועות חוצות אותה. " +
                    "אור טוב, בלי שרוול. האימות קורה לבד.",
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
                    // the guide: where the detector looks
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.88f)
                            .fillMaxHeight(0.40f)
                            .border(
                                3.dp,
                                if (bands >= WindingDetector.REQUIRED) Palette.Success else Palette.Paper,
                                RoundedCornerShape(12.dp)
                            )
                    )
                    Text(
                        text = when {
                            bands >= WindingDetector.REQUIRED -> "רואה $bands כריכות"
                            bands > 0 -> "רואה $bands כריכות, צריך ${WindingDetector.REQUIRED}"
                            else -> "לא רואה כריכות עדיין"
                        },
                        color = Palette.Paper,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                    )
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

                when (current) {
                    is Phase.Verified -> Overlay(title = "הנחת. יום טוב.", body = current.note)
                    is Phase.Failed -> Overlay(title = "משהו השתבש.", body = current.message)
                    Phase.Scanning -> Unit
                }
            }

            Spacer(Modifier.height(16.dp))

            when (current) {
                Phase.Scanning -> {
                    if (showHint) {
                        Text(
                            "לא מזהה? קרב את הטלפון עד שהאמה ממלאת את המסגרת, סובב לאור, והרם את השרוול מעל המרפק.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Bronze,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
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
                is Phase.Verified -> InkButton("המשך", onClose, Modifier.fillMaxWidth())
                is Phase.Failed -> {
                    InkButton("חזרה", onClose, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun Overlay(title: String, body: String) {
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
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor) { image -> onFrame(image) } }
                    val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                    p.unbindAll()
                    p.bindToLifecycle(this@VerifyActivity, selector, preview, analysis)
                } catch (e: Exception) {
                    phase.value = Phase.Failed("המצלמה לא נפתחה: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                provider?.unbindAll()
            }
        }
    }
}
