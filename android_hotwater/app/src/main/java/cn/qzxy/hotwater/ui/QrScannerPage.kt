package cn.qzxy.hotwater.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.qzxy.hotwater.QrCodeParser
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerPage(
    title: String = "扫描设备二维码",
    allowManualSnCodeEntry: Boolean = false,
    scanResetKey: Int = 0,
    navigationEnabled: Boolean = true,
    isProcessing: Boolean = false,
    bindingError: String = "",
    onBack: () -> Unit,
    onScanned: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tokens = LocalGlassTokens.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permissionDeniedPermanently by rememberSaveable { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var delivered by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    var cameraRetryKey by rememberSaveable { mutableStateOf(0) }
    var torchAvailable by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var manualSnCode by rememberSaveable { mutableStateOf("") }
    var manualSnCodeError by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            permissionDeniedPermanently = false
            cameraFailed = false
            scanError = null
            cameraRetryKey++
        } else {
            permissionDeniedPermanently = isCameraPermissionPermanentlyDenied(context)
            scanError = if (permissionDeniedPermanently) {
                "相机权限已被拒绝，请在系统设置中允许"
            } else {
                "需要相机权限才能扫描设备二维码"
            }
        }
    }

    LaunchedEffect(hasCameraPermission, permissionRequested) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> torchEnabled = false
                Lifecycle.Event.ON_RESUME -> {
                    val granted = isCameraPermissionGranted(context)
                    if (granted && !hasCameraPermission) {
                        hasCameraPermission = true
                        permissionDeniedPermanently = false
                        cameraFailed = false
                        scanError = null
                        cameraRetryKey++
                    } else if (!granted && permissionRequested) {
                        hasCameraPermission = false
                        torchEnabled = false
                        permissionDeniedPermanently = isCameraPermissionPermanentlyDenied(context)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openCameraSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun handlePayload(rawValue: String) {
        if (delivered) return
        val snCode = QrCodeParser.parseSnCode(rawValue)
        if (snCode == null) {
            scanError = "这不是趣智校园设备二维码"
        } else {
            delivered = true
            torchEnabled = false
            onScanned(snCode)
        }
    }

    fun submitManualSnCode() {
        if (!navigationEnabled || isProcessing || delivered) return
        val normalized = QrCodeParser.normalizeSnCode(manualSnCode)
        if (normalized == null) {
            manualSnCodeError = "请输入 12 位十六进制 snCode"
            return
        }
        focusManager.clearFocus()
        manualSnCodeError = null
        delivered = true
        torchEnabled = false
        onScanned(normalized)
    }

    LaunchedEffect(scanResetKey) {
        if (scanResetKey == 0) return@LaunchedEffect
        delivered = false
        scanError = "设备码保存失败，请重新扫描"
        manualSnCodeError = null
        cameraFailed = false
        cameraRetryKey++
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val compact = maxHeight < 600.dp
        val previewSize = minOf(maxWidth, 300.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPressable(
                    onClick = onBack,
                    enabled = navigationEnabled,
                    material = GlassMaterial.Control,
                    cornerRadius = 24.dp,
                    modifier = Modifier
                        .size(if (compact) 44.dp else 48.dp)
                        .semantics { contentDescription = "返回" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = tokens.textPrimary,
                    )
                }
                Text(
                    text = title,
                    color = tokens.textPrimary,
                    fontSize = if (compact) 21.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(previewSize)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, tokens.borderHi, RoundedCornerShape(24.dp))
                        .glassMaterial(GlassMaterial.Panel, 24.dp)
                        .semantics {
                            contentDescription = if (hasCameraPermission) {
                                "设备二维码相机实时取景"
                            } else {
                                "相机权限未开启"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasCameraPermission && !cameraFailed) {
                        key(cameraRetryKey) {
                            QrCameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                torchEnabled = torchEnabled,
                                onPayload = ::handlePayload,
                                onCameraError = {
                                    cameraFailed = true
                                    torchAvailable = false
                                    torchEnabled = false
                                    scanError = "相机暂时不可用，请点击下方按钮重试"
                                },
                                onTorchAvailabilityChanged = { available ->
                                    torchAvailable = available
                                    if (!available) torchEnabled = false
                                },
                                onTorchError = {
                                    torchEnabled = false
                                    scanError = "手电筒暂时不可用，请稍后重试"
                                },
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = tokens.textTertiary,
                                modifier = Modifier.size(42.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (cameraFailed) "相机暂时不可用" else "请允许使用相机",
                                color = tokens.textSecondary,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassMaterial(GlassMaterial.EmbeddedControl, 16.dp)
                    .heightIn(min = if (compact) 52.dp else 58.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = tokens.accentPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = when {
                        isProcessing -> "正在保存设备码…"
                        bindingError.isNotBlank() -> bindingError
                        scanError != null -> scanError.orEmpty()
                        else -> "将二维码放入取景框内，识别后会自动填入设备码"
                    },
                    color = if (scanError == null || isProcessing) {
                        tokens.textSecondary
                    } else {
                        tokens.accentError
                    },
                    fontSize = 13.sp,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                if (hasCameraPermission && !cameraFailed && torchAvailable) {
                    GlassPressable(
                        onClick = { torchEnabled = !torchEnabled },
                        enabled = !isProcessing && !delivered,
                        material = GlassMaterial.Control,
                        cornerRadius = 16.dp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .semantics {
                                contentDescription = if (torchEnabled) "关闭手电筒" else "打开手电筒"
                                stateDescription = if (torchEnabled) "已打开" else "已关闭"
                            },
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) {
                                Icons.Outlined.FlashlightOff
                            } else {
                                Icons.Outlined.FlashlightOn
                            },
                            contentDescription = null,
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (allowManualSnCodeEntry) {
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))
                GlassInput(
                    value = manualSnCode,
                    onValueChange = { value ->
                        if (!isProcessing && !delivered) {
                            manualSnCode = value.take(32)
                            manualSnCodeError = null
                        }
                    },
                    placeholder = "手动输入 12 位 snCode",
                    errorMessage = manualSnCodeError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitManualSnCode() }),
                    trailingContent = {
                        GlassPressable(
                            onClick = ::submitManualSnCode,
                            enabled = navigationEnabled && !isProcessing && !delivered && manualSnCode.isNotBlank(),
                            material = GlassMaterial.EmbeddedControl,
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = "确认设备码" },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = tokens.textPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!hasCameraPermission) {
                GlassButton(
                    text = if (permissionDeniedPermanently) "打开相机设置" else "允许相机权限",
                    onClick = {
                        if (permissionDeniedPermanently) {
                            openCameraSettings()
                        } else {
                            permissionRequested = true
                            requestPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (hasCameraPermission && cameraFailed) {
                GlassButton(
                    text = "重新启动相机",
                    onClick = {
                        cameraFailed = false
                        torchAvailable = false
                        torchEnabled = false
                        scanError = null
                        cameraRetryKey++
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (onSkip != null) {
                GlassPressable(
                    onClick = onSkip,
                    enabled = navigationEnabled,
                    material = GlassMaterial.EmbeddedControl,
                    cornerRadius = 20.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(if (compact) 40.dp else 44.dp)
                        .semantics { contentDescription = "跳过绑定" },
                ) {
                    Text(
                        text = "跳过绑定",
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun QrCameraPreview(
    modifier: Modifier,
    torchEnabled: Boolean,
    onPayload: (String) -> Unit,
    onCameraError: () -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onTorchError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPayloadHandler by rememberUpdatedState(onPayload)
    val currentErrorHandler by rememberUpdatedState(onCameraError)
    val currentTorchAvailabilityHandler by rememberUpdatedState(onTorchAvailabilityChanged)
    val currentTorchErrorHandler by rememberUpdatedState(onTorchError)
    val disposed = remember { AtomicBoolean(false) }
    val ownedUseCases = remember { OwnedCameraUseCases() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        QrImageAnalyzer(
            mainExecutor = ContextCompat.getMainExecutor(context),
            onPayload = { if (!disposed.get()) currentPayloadHandler(it) },
        )
    }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(boundCamera, torchEnabled) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (!camera.cameraInfo.hasFlashUnit()) {
            currentTorchAvailabilityHandler(false)
            return@LaunchedEffect
        }
        val future = runCatching {
            camera.cameraControl.enableTorch(torchEnabled)
        }.getOrElse {
            currentTorchErrorHandler()
            return@LaunchedEffect
        }
        future.addListener(
            {
                if (disposed.get()) return@addListener
                runCatching { future.get() }
                    .onFailure { currentTorchErrorHandler() }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                cameraProviderFuture.addListener(listener@{
                    var provider: ProcessCameraProvider? = null
                    var analysis: ImageAnalysis? = null
                    try {
                        provider = cameraProviderFuture.get()
                        if (disposed.get()) {
                            return@listener
                        }
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = surfaceProvider
                        }
                        analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor, analyzer)
                        if (disposed.get()) {
                            analysis.clearAnalyzer()
                            return@listener
                        }
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        if (!disposed.get()) {
                            ownedUseCases.provider = provider
                            ownedUseCases.preview = preview
                            ownedUseCases.analysis = analysis
                            boundCamera = camera
                            currentTorchAvailabilityHandler(camera.cameraInfo.hasFlashUnit())
                        } else {
                            analysis.clearAnalyzer()
                            provider.unbind(preview, analysis)
                        }
                    } catch (_: Exception) {
                        analysis?.clearAnalyzer()
                        if (!disposed.get()) {
                            boundCamera = null
                            currentTorchAvailabilityHandler(false)
                            currentErrorHandler()
                        }
                    }
                }, ContextCompat.getMainExecutor(viewContext))
            }
        },
    )

    DisposableEffect(cameraProviderFuture, analyzer, executor) {
        onDispose {
            disposed.set(true)
            boundCamera?.let { camera ->
                if (camera.cameraInfo.hasFlashUnit()) {
                    runCatching { camera.cameraControl.enableTorch(false) }
                }
            }
            ownedUseCases.analysis?.clearAnalyzer()
            ownedUseCases.provider?.let { provider ->
                val preview = ownedUseCases.preview
                val analysis = ownedUseCases.analysis
                if (preview != null && analysis != null) {
                    runCatching { provider.unbind(preview, analysis) }
                }
            }
            ownedUseCases.clear()
            analyzer.close()
            executor.shutdownNow()
        }
    }
}

private class OwnedCameraUseCases {
    var provider: ProcessCameraProvider? = null
    var preview: Preview? = null
    var analysis: ImageAnalysis? = null

    fun clear() {
        provider = null
        preview = null
        analysis = null
    }
}

private class QrImageAnalyzer(
    private val mainExecutor: Executor,
    private val onPayload: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (closed.get()) {
            imageProxy.close()
            return
        }
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                if (closed.get()) return@addOnSuccessListener
                val preferredValue = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.takeIf { QrCodeParser.parseSnCode(it) != null }
                } ?: barcodes.firstNotNullOfOrNull { it.rawValue }
                preferredValue?.let { rawValue ->
                    mainExecutor.execute {
                        if (!closed.get()) onPayload(rawValue)
                    }
                }
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) scanner.close()
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun isCameraPermissionPermanentlyDenied(context: Context): Boolean {
    if (isCameraPermissionGranted(context)) return false
    val activity = context.findActivity() ?: return true
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
