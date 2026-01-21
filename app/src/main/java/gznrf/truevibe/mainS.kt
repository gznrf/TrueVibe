package gznrf.truevibe

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import gznrf.truevibe.R
import java.util.Calendar

// Emotion data classes and repository
enum class Emotion(val displayName: String) {
    HAPPY("Радость"),
    SAD("Грусть"),
    NEUTRAL("Нейтральное"),
    WINKING("Подмигивание"),
    TIRED("Усталость")
}

data class EmotionRecord(val timestamp: Long, val emotion: Emotion)

object EmotionRepository {
    private const val PREFS_NAME = "EmotionHistory"
    private const val KEY_EMOTIONS = "emotions"

    fun saveEmotion(context: Context, emotion: Emotion) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEmotions = prefs.getStringSet(KEY_EMOTIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existingEmotions.add("${System.currentTimeMillis()}:${emotion.name}")
        prefs.edit().putStringSet(KEY_EMOTIONS, existingEmotions).apply()
    }

    fun getEmotions(context: Context): List<EmotionRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val emotionStrings = prefs.getStringSet(KEY_EMOTIONS, emptySet()) ?: emptySet()
        return emotionStrings.mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) {
                try {
                    EmotionRecord(parts[0].toLong(), Emotion.valueOf(parts[1]))
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
}

// Titles
var greetTitle = "Узнай свое настроение"
var happyTitle = "Вы выглядите радостным!\nЖелаю вам хорошего дня!"
var sadTitle = "Вы выглядите грустным.\nТучи скоро рассеются!"
var neutralTitle = "Вы сейчас спокойны и сосредоточены.\nОтличный момент для продуктивной работы!"
var winkingTitle = "Вы подмигиваете!\nОтличный знак!"
var tiredTitle = "Кажется, вы устали.\nНе забывайте отдыхать."
var errorTitle = "Вашего лица не видно\nПопробуйте еще раз."


// --- Main App Navigation ---
@Composable
fun MainScreen() {
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<Pair<Emotion, String>?>(null) }
    val context = LocalContext.current

    val currentBitmap = imageBitmap
    val currentAnalysis = analysisResult

    if (currentBitmap != null && currentAnalysis != null) {
        PreviewScreen(
            bitmap = currentBitmap,
            resultText = currentAnalysis.second,
            onSave = {
                saveBitmapToGallery(context, currentBitmap)
                imageBitmap = null
                analysisResult = null
            },
            onClose = {
                imageBitmap = null
                analysisResult = null
            }
        )
    } else {
        CameraScreen(
            onPhotoTaken = { bitmap, emotion, text ->
                imageBitmap = bitmap
                analysisResult = Pair(emotion, text)
                EmotionRepository.saveEmotion(context, emotion)
            }
        )
    }
}

// --- Screens ---
@Composable
fun CameraScreen(onPhotoTaken: (Bitmap, Emotion, String) -> Unit) {
    var mainTitleText by remember { mutableStateOf(greetTitle) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }

    val requiredPermissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

    var hasPermissions by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            hasPermissions = permissionsMap.values.all { it }
        }
    )

    LaunchedEffect(key1 = true) {
        launcher.launch(requiredPermissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF33A6B2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = mainTitleText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 48.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(15.dp))
        ) {
            if (hasPermissions) {
                CameraPreview(imageCapture = imageCapture, lifecycleOwner = lifecycleOwner, cameraSelector = cameraSelector)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { mainTitleText = analyzeWeeklyEmotions(context) },
                modifier = Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(imageVector = Icons.Default.List, contentDescription = "Статистика", tint = Color.White, modifier = Modifier.size(40.dp))
            }

            IconButton(
                onClick = {
                    if (hasPermissions) {
                        takeAndAnalyzePhoto(context, imageCapture, onPhotoTaken)
                    }
                },
                modifier = Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_camera), contentDescription = "Сделать фото", tint = Color.White, modifier = Modifier.size(56.dp))
            }

            IconButton(
                onClick = {
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                },
                modifier = Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_refresh), contentDescription = "Переключить камеру", tint = Color.White, modifier = Modifier.size(50.dp))
            }
        }
    }
}

@Composable
fun PreviewScreen(bitmap: Bitmap, resultText: String, onSave: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = resultText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(50.dp))
                }

                IconButton(
                    onClick = onSave,
                    modifier = Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Сохранить", tint = Color.White, modifier = Modifier.size(50.dp))
                }
            }
        }
    }
}

// --- Logic and Helpers ---

private fun analyzeWeeklyEmotions(context: Context): String {
    val allRecords = EmotionRepository.getEmotions(context)
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val oneWeekAgo = calendar.timeInMillis

    val weeklyRecords = allRecords.filter { it.timestamp >= oneWeekAgo }

    if (weeklyRecords.isEmpty()) {
        return "За последнюю неделю нет данных. Сделайте несколько снимков!"
    }

    val emotionCounts = weeklyRecords.groupingBy { it.emotion }.eachCount()
    val maxCount = emotionCounts.values.maxOrNull() ?: 0
    if (maxCount == 0) {
        return "Недостаточно данных для анализа."
    }

    val mostFrequentEmotions = emotionCounts.filter { it.value == maxCount }.keys

    if (mostFrequentEmotions.size == 1) {
        return when (mostFrequentEmotions.first()) {
            Emotion.HAPPY -> "На прошлой неделе вы чаще всего радовались! Так держать!"
            Emotion.SAD -> "На прошлой неделе вы часто грустили. Не забывайте отдыхать."
            Emotion.NEUTRAL -> "Ваше настроение на прошлой неделе было в основном спокойным."
            Emotion.WINKING -> "На прошлой неделе вы часто подмигивали. Отличное настроение!"
            Emotion.TIRED -> "Кажется, на прошлой неделе вы часто уставали. Пора отдохнуть!"
        }
    } else {
        val emotionNames = mostFrequentEmotions.joinToString(separator = " и ") { it.displayName.lowercase() }
        return "На прошлой неделе у вас одинаково часто были: $emotionNames."
    }
}

@Composable
fun CameraPreview(imageCapture: ImageCapture, lifecycleOwner: LifecycleOwner, cameraSelector: CameraSelector) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lifecycleOwner, cameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Не удалось привязать камеру", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun takeAndAnalyzePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoTaken: (Bitmap, Emotion, String) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val bitmap = imageProxy.toBitmap().rotate(rotationDegrees.toFloat())
                imageProxy.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build())

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        val result = if (faces.isNotEmpty()) {
                            processFace(faces.first())
                        } else {
                            Pair(Emotion.NEUTRAL, errorTitle)
                        }
                        onPhotoTaken(bitmap, result.first, result.second)
                    }
                    .addOnFailureListener { e ->
                        Log.e("takeAndAnalyzePhoto", "Face detection failed", e)
                        onPhotoTaken(bitmap, Emotion.NEUTRAL, "Ошибка анализа.")
                    }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("takeAndAnalyzePhoto", "Image capture error", exception)
            }
        })
}

private fun processFace(face: Face): Pair<Emotion, String> {
    val smilingProb = face.smilingProbability ?: 0f
    val leftEyeOpenProb = face.leftEyeOpenProbability ?: 1f
    val rightEyeOpenProb = face.rightEyeOpenProbability ?: 1f

    return when {
        leftEyeOpenProb < 0.4f && rightEyeOpenProb < 0.4f -> Pair(Emotion.TIRED, tiredTitle)
        (leftEyeOpenProb < 0.4f && rightEyeOpenProb > 0.8f) || (leftEyeOpenProb > 0.8f && rightEyeOpenProb < 0.4f) -> Pair(Emotion.WINKING, winkingTitle)
        smilingProb > 0.75f -> Pair(Emotion.HAPPY, happyTitle)
        smilingProb < 0.25f -> Pair(Emotion.SAD, sadTitle)
        else -> Pair(Emotion.NEUTRAL, neutralTitle)
    }
}

private fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val name = "TrueVibe_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TrueVibe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        resolver.openOutputStream(it)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
        }
    }
}
