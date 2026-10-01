package cn.qzxy.hotwater.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qzxy.hotwater.MainViewModel
import cn.qzxy.hotwater.NearbyDevice
import cn.qzxy.hotwater.NearbyPairState
import cn.qzxy.hotwater.NearbyScanState
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial

/** Own the radio only while this surface is visible and the Activity is resumed. */
@Composable
private fun NearbyDiscovery(
    viewModel: MainViewModel,
    active: Boolean,
    content: @Composable (List<NearbyDevice>, NearbyScanState, NearbyPairState, Boolean, () -> Unit, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val owner = remember { Any() }
    val devices by viewModel.nearbyDevices.collectAsStateWithLifecycle()
    val scan by viewModel.nearbyScanState.collectAsStateWithLifecycle()
    val pair by viewModel.nearbyPairState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var accessMessage by remember { mutableStateOf("") }
    val activeNow by rememberUpdatedState(active)
    val permissions = remember { if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) }
    fun startGranted() {
        if (!activeNow || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (Build.VERSION.SDK_INT < 31) {
            val location = context.getSystemService(LocationManager::class.java)
            val enabled = runCatching { location?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                location?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true }.getOrDefault(false)
            if (!enabled) { accessMessage = "此 Android 版本扫描需要开启系统位置信息，也可以扫码配对"; return }
        }
        accessMessage = ""
        viewModel.startNearbyScan(owner)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (permissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            startGranted()
        } else accessMessage = "请允许附近设备权限；也可以扫码配对，或在系统设置中授权"
    }
    val scanAction: () -> Unit = {
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) startGranted()
        else launcher.launch(permissions)
    }
    LaunchedEffect(active) {
        if (active) scanAction() else viewModel.stopNearbyScan(owner)
    }
    DisposableEffect(lifecycle, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.stopNearbyScan(owner)
            if (event == Lifecycle.Event.ON_RESUME && activeNow &&
                permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) startGranted()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.stopNearbyScan(owner) }
    }
    content(devices, if (accessMessage.isBlank()) scan else NearbyScanState(message = accessMessage), pair, busy, scanAction) {
        val intent = when {
            accessMessage.contains("位置信息") -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            scan.message.contains("开启手机蓝牙") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        }
        runCatching { context.startActivity(intent) }
    }
}

@Composable
fun NearbyDevicesPage(
    viewModel: MainViewModel,
    active: Boolean,
    currentSnCode: String,
    onBack: () -> Unit,
    onScanQrCode: () -> Unit,
    onPaired: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val tokens = LocalGlassTokens.current
    NearbyDiscovery(viewModel, active) { devices, scan, pair, busy, refresh, settings ->
        LaunchedEffect(active, pair.success) { if (active && pair.success) onPaired() }
        fun leave(action: () -> Unit) { viewModel.cancelNearbyPairing(); viewModel.stopNearbyScan(); action() }
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPressable(onClick = { leave(onBack) }, material = GlassMaterial.Control,
                    cornerRadius = 24.dp, modifier = Modifier.size(48.dp).semantics { contentDescription = "返回" }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = tokens.textPrimary)
                }
                Text("绑定你的热水器", color = tokens.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 14.dp))
            }
            Text("靠近热水器，选择附近设备完成绑定。", color = tokens.textSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                NearbyDeviceListCard(devices, scan, pair, currentSnCode, !busy, refresh, settings, viewModel::pairNearbyDevice)
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            GlassButton(text = "扫码配对", onClick = { leave(onScanQrCode) }, modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Outlined.QrCodeScanner, null, tint = tokens.textPrimary) })
            if (onSkip != null) {
                Spacer(Modifier.height(8.dp))
                GlassPressable(onClick = { leave(onSkip) }, material = GlassMaterial.EmbeddedControl,
                    cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("暂时跳过", color = tokens.textSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun NearbyDevicePickerDialog(viewModel: MainViewModel, currentSnCode: String, onDismiss: () -> Unit) {
    fun dismiss() { viewModel.cancelNearbyPairing(); viewModel.stopNearbyScan(); onDismiss() }
    Dialog(onDismissRequest = ::dismiss) {
        NearbyDiscovery(viewModel, active = true) { devices, scan, pair, busy, refresh, settings ->
            LaunchedEffect(pair.success) { if (pair.success) dismiss() }
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).glassMaterial(GlassMaterial.Overlay, 28.dp).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("切换设备", color = LocalGlassTokens.current.textPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    GlassPressable(onClick = ::dismiss, material = GlassMaterial.EmbeddedControl,
                        modifier = Modifier.size(48.dp), cornerRadius = 24.dp) {
                        Icon(Icons.Outlined.Close, "关闭设备列表", tint = LocalGlassTokens.current.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    NearbyDeviceListCard(devices, scan, pair, currentSnCode, !busy, refresh, settings, viewModel::pairNearbyDevice)
                }
            }
        }
    }
}

@Composable
internal fun NearbyDeviceListCard(
    devices: List<NearbyDevice>, scanState: NearbyScanState, pairState: NearbyPairState,
    currentSnCode: String, canSelect: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit,
    onPair: (NearbyDevice) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 24.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, null, tint = tokens.accentPrimary, modifier = Modifier.size(22.dp))
            Text("附近设备", color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            GlassPressable(onClick = onRefresh, enabled = !scanState.scanning && !pairState.loading,
                material = GlassMaterial.EmbeddedControl, cornerRadius = 20.dp, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Refresh, "重新扫描附近设备", tint = tokens.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Text(when {
            scanState.scanning -> "正在扫描附近热水器…"
            scanState.message.isNotBlank() -> scanState.message
            devices.isEmpty() -> "暂未发现设备，请靠近热水器后重新扫描。"
            else -> "选择设备后将核对设备信息并绑定。"
        }, color = tokens.textSecondary, fontSize = 13.sp)
        if (scanState.message.isNotBlank()) {
            GlassPressable(onClick = onSettings, cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("打开系统设置", color = tokens.accentPrimary, fontSize = 13.sp)
            }
        }
        if (scanState.scanning) CircularProgressIndicator(color = tokens.accentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        devices.forEach { device ->
            val selected = currentSnCode == (device.binding?.snCode ?: device.normalizedMac)
            GlassPressable(onClick = { onPair(device) }, enabled = canSelect && !pairState.loading,
                material = GlassMaterial.EmbeddedControl, cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.displayName, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        device.binding?.locationLabel?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = tokens.textSecondary, fontSize = 12.sp)
                        }
                        Text(device.macAddress, color = tokens.textTertiary, fontSize = 12.sp)
                        Text(if (selected) "当前设备" else "信号 ${device.rssi} dBm", color = tokens.textSecondary, fontSize = 12.sp)
                    }
                    if (selected) Icon(Icons.Outlined.Check, null, tint = tokens.accentPrimary)
                    else Text("连接", color = tokens.accentPrimary, fontSize = 13.sp)
                }
            }
        }
        if (pairState.loading) Text("正在核对设备信息…", color = tokens.accentPrimary, fontSize = 13.sp)
        else if (pairState.message.isNotBlank() && !pairState.success) Text(pairState.message, color = tokens.accentError, fontSize = 13.sp)
    }
}
