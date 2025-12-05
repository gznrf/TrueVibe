package gznrf.truevibe

import android.Manifest
import android.R.attr.bitmap
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
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

@Composable
fun MainScreen() {
    // Заголовки для разных состояний настроения
    val greetTitle = "Узнай свое настроение"
    val sadTitle = "Вы грустный\nвсе будет \nхорошо!\n"
    val happyTitle = "Вы веселый\nПродолжайте в том же духе!"
    val angryTitle = "Вы злой\nпопробуйте успокоиться!"

    // Массив для циклического переключения заголовков
    var titlesArray = arrayOf(greetTitle, sadTitle, happyTitle, angryTitle)

    // Состояние для хранения текущего текста заголовка. `remember` сохраняет его между обновлениями экрана.
    var mainTitleText by remember { mutableStateOf(greetTitle) }
    // Состояние для хранения индекса текущего заголовка в массиве.
    var i by remember { mutableIntStateOf(1) }

    // Получаем текущий контекст и владельца жизненного цикла, необходимые для CameraX
    val context = LocalContext.current
    // Создаем и запоминаем объект для управления съемкой фото
    val imageCapture = remember { ImageCapture.Builder().build() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Определяем, какие разрешения запрашивать в зависимости от версии Android
    val requiredPermissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        // Для старых версий Android (до API 29) нужен доступ к хранилищу для сохранения фото
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        // Для новых версий достаточно только разрешения на камеру
        arrayOf(Manifest.permission.CAMERA)
    }

    // Состояние для отслеживания, предоставлены ли все необходимые разрешения
    var hasPermissions by remember { mutableStateOf(false) }
    // Создаем лаунчер для запроса разрешений. Результат (да/нет) обновляет `hasPermissions`.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            // Считаем, что разрешения есть, только если пользователь одобрил все из них
            hasPermissions = permissionsMap.values.all { it }
        }
    )

    // Запускаем запрос разрешений один раз при первом создании экрана
    LaunchedEffect(key1 = true) {
        launcher.launch(requiredPermissions)
    }

    // Основной контейнер экрана, располагающий элементы вертикально по центру
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF33A6B2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Строка для отображения заголовка
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

        // Контейнер для предпросмотра камеры
        Box(
            modifier = Modifier
                .size(322.dp, 582.dp)
                .clip(RoundedCornerShape(15.dp))
        ) {
            // Отображаем предпросмотр камеры, только если разрешения предоставлены
            if (hasPermissions) {
                CameraPreview(imageCapture = imageCapture, lifecycleOwner = lifecycleOwner)
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Строка с кнопками управления
        Row(
            modifier = Modifier.size(322.dp, 84.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Пустой элемент для выравнивания
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF33A6B2))
            ) {
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Кнопка — сделать фото
            IconButton(
                onClick = {
                    // Делаем фото, только если есть разрешение
                    if(hasPermissions) {
                        // Меняем заголовок на следующий из массива
                        mainTitleText = titlesArray[i]
                        // Обновляем индекс для следующего нажатия, обеспечивая цикличность
                        if (i == titlesArray.lastIndex){
                            i = 0
                        } else {
                            i++
                        }
                        val opts = FaceDetectorOptions.Builder()
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                            .build()
                        val detector = FaceDetection.getClient(opts);
                        val bitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.placeholder_face1)
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val result = detector.process(image)
                            .addOnSuccessListener { faces ->
                                for (face in faces) {
                                    // If classification was enabled:
                                    if (face.smilingProbability != null) {
                                        mainTitleText = "smile"
                                        val smileProb = face.smilingProbability
                                    }
                                }

                            }
                            .addOnFailureListener { e ->

                            }
                        takePhoto(context, imageCapture)
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
                    // Сбрасываем заголовок и индекс к начальным значениям
                    mainTitleText = greetTitle
                    i = 1
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
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture
) {
    // Создаем уникальное имя файла на основе текущего времени
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    // Подготавливаем метаданные для сохранения изображения
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        // Указываем папку для сохранения (работает на Android 10 и выше)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }

    // Создаем объект с опциями для сохранения файла
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    // Запускаем процесс съемки
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        // Колбэк, который будет вызван после завершения съемки
        object : ImageCapture.OnImageSavedCallback {
            // В случае ошибки выводим лог
            override fun onError(exc: ImageCaptureException) {
                Log.e("takePhoto", "Ошибка сохранения фото: ", exc)
            }

            // В случае успеха выводим лог с URI сохраненного файла
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d("takePhoto", "Фото успешно сохранено: ${output.savedUri}")
            }
        }
    )
}
