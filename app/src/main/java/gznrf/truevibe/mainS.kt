package gznrf.truevibe

import android.Manifest
import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

@Composable
fun MainScreen() {
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
            .background(Color(0xFF33A6B2))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = mainTitleText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(15.dp))
        ) {
            if (hasPermissions) {
                CameraPreview(imageCapture = imageCapture, lifecycleOwner = lifecycleOwner, cameraSelector = cameraSelector)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Analytics Button
            IconButton(
                onClick = {
                    mainTitleText = analyzeWeeklyEmotions(context)
                },
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Статистика за неделю",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }

            // Camera Button
            IconButton(
                onClick = {
                    if (hasPermissions) {
                        defineEmotion(context, imageCapture) { emotion, resultText ->
                            mainTitleText = resultText
                            EmotionRepository.saveEmotion(context, emotion)
                        }
                    }
                },
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = "Сделать фото",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            // Switch Camera Button
            IconButton(
                onClick = { 
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                },
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = "Переключить камеру",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }
}

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
    val mostFrequentEmotion = emotionCounts.maxByOrNull { it.value }?.key

    return when (mostFrequentEmotion) {
        Emotion.HAPPY -> "На прошлой неделе вы чаще всего радовались! Так держать!"
        Emotion.SAD -> "На прошлой неделе вы часто грустили. Не забывайте отдыхать."
        Emotion.NEUTRAL -> "Ваше настроение на прошлой неделе было в основном нейтральным."
        Emotion.WINKING -> "На прошлой неделе вы часто подмигивали. Отличное настроение!"
        Emotion.TIRED -> "Кажется, на прошлой неделе вы часто уставали. Пора отдохнуть!"
        null -> "Недостаточно данных для анализа."
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
private fun defineEmotion(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (Emotion, String) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    val highAccuracyOpts = FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build()

                    val detector = FaceDetection.getClient(highAccuracyOpts)

                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val face = faces.first()
                                val (emotion, text) = processFace(face)
                                onResult(emotion, text)
                            } else {
                                onResult(Emotion.NEUTRAL, errorTitle)
                            }
                            imageProxy.close()
                        }
                        .addOnFailureListener { e ->
                            Log.e("defineEmotion", "Face detection failed", e)
                            onResult(Emotion.NEUTRAL, "Камера не работает, проверьте ее")
                            imageProxy.close()
                        }
                } else {
                    onResult(Emotion.NEUTRAL, errorTitle)
                    imageProxy.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("defineEmotion", "Image capture error", exception)
                onResult(Emotion.NEUTRAL, "Не удалось сделать фото")
            }
        })
}

private fun processFace(face: Face): Pair<Emotion, String> {
    val smilingProb = face.smilingProbability ?: 0f
    val leftEyeOpenProb = face.leftEyeOpenProbability ?: 1f
    val rightEyeOpenProb = face.rightEyeOpenProbability ?: 1f

    return when {
        smilingProb > 0.7f -> {
            Pair(Emotion.HAPPY, happyTitle)
        }
        leftEyeOpenProb < 0.4 && rightEyeOpenProb < 0.4 -> {
            Pair(Emotion.TIRED, tiredTitle)
        }
        (leftEyeOpenProb > 0.6 && rightEyeOpenProb < 0.4) || (leftEyeOpenProb < 0.4 && rightEyeOpenProb > 0.6) -> {
            Pair(Emotion.WINKING, winkingTitle)
        }
        smilingProb < 0.3f -> {
            Pair(Emotion.SAD, sadTitle)
        }
        else -> {
            Pair(Emotion.NEUTRAL, neutralTitle)
        }
    }
}
