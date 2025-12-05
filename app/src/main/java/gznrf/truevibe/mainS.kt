package gznrf.truevibe

import android.Manifest
import android.R.attr.bitmap
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.*
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Locale

var greetTitle = "Узнай свое настроение"
var happyTitle = "Вы улыбаетесь\nУлыбка продлевает жизнь!"
var notHappyTitle = "Вы не веселый\nУлыбнитесь)"
var errorTitle = "Вашего лица не видно\nУлыбнитесь и нажмите кнопку)"

/*val sadTitle = "Вы грустный\nвсе будет \nхорошо!\n"
val angryTitle = "Вы злой\nпопробуйте успокоиться!"*/
@Composable
fun MainScreen() {


    var mainTitleText by remember { mutableStateOf(greetTitle) }
    val context = LocalContext.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val lifecycleOwner = LocalLifecycleOwner.current

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
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF33A6B2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.size(322.dp, 72.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = mainTitleText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        Box(
            modifier = Modifier
                .size(322.dp, 582.dp)
                .clip(RoundedCornerShape(15.dp))
        ) {
            if (hasPermissions) {
                CameraPreview(imageCapture = imageCapture, lifecycleOwner = lifecycleOwner)
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.size(322.dp, 84.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF33A6B2))
            ) {
            }

            Spacer(modifier = Modifier.width(20.dp))

            IconButton(
                onClick = {
                    if(hasPermissions) {
                        defineEmotion(context, imageCapture) { resultText ->
                            mainTitleText = resultText
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

            Spacer(modifier = Modifier.width(20.dp))

            // Кнопка — обновить (сброс)
            IconButton(
                onClick = {
                 },
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = "Обновить фото",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }
}

/**
 * @param imageCapture Объект, управляющий процессом съемки фото.
 * @param lifecycleOwner Владелец жизненного цикла, к которому привязывается камера.
 */
@Composable
fun CameraPreview(imageCapture: ImageCapture, lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    // `remember` создает и запоминает `PreviewView` между перерисовками
    val previewView = remember { PreviewView(context) }

    // `LaunchedEffect` запускает код привязки камеры, когда `lifecycleOwner` становится доступен.
    // Это гарантирует, что камера не будет инициализироваться на каждом кадре.
    LaunchedEffect(lifecycleOwner) {
        // Получаем экземпляр провайдера камеры
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Создаем `Preview` (область предпросмотра) и связываем его с `PreviewView`
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Выбираем переднюю камеру по умолчанию
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Отвязываем все предыдущие использования, чтобы избежать конфликтов
                cameraProvider.unbindAll()
                // Привязываем камеру к жизненному циклу
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Не удалось привязать камеру", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Встраиваем `PreviewView` в иерархию Compose
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * Функция для съемки и сохранения фотографии.
 * @param context Контекст приложения.
 * @param imageCapture Объект, управляющий процессом съемки.
 */
private fun defineEmotion(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (String) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaColumns.DISPLAY_NAME, name)
        put(MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("takePhoto", "Ошибка сохранения фото: ", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: return onResult("URI пустой")

                val bitmap = MediaStore.Images.Media.getBitmap(
                    context.contentResolver,
                    savedUri
                )

                val opts = FaceDetectorOptions.Builder()
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()

                val detector = FaceDetection.getClient(opts)
                val image = InputImage.fromBitmap(bitmap, 0)

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces.first()
                            if (face.smilingProbability != null && face.smilingProbability!! > 0.5f) {
                                onResult(happyTitle)
                            } else {
                                onResult(notHappyTitle)
                            }
                        } else {
                            onResult(errorTitle)
                        }
                    }
                    .addOnFailureListener {
                        onResult("Камера не работает, проверьте ее")
                    }
            }
        }
    )
}
