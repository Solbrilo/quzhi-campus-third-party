package cn.qzxy.hotwater

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Discovery identifies a heater; the service's device-info response authorizes its binding. */
data class NearbyDevice(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val binding: NearbyDeviceBinding? = null,
) {
    val normalizedMac: String get() = QrCodeParser.normalizeSnCode(macAddress).orEmpty()
    val displayName: String get() = binding?.deviceName ?: name.takeUnless { it.equals("KLCXKJ-Water", true) } ?: "校园热水器"
}

data class NearbyDeviceBinding(val snCode: String, val deviceName: String, val locationLabel: String)

class NearbyDeviceScanner(
    context: Context,
    private val onDeviceFound: (NearbyDevice) -> Unit,
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ScanCallback? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var timeout: Runnable? = null

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        try {
            val adapter = manager?.adapter
            if (adapter == null) { onError("此设备不支持蓝牙，可使用扫码配对"); return }
            if (!adapter.isEnabled) { onError("请开启手机蓝牙后重新扫描，或使用扫码配对"); return }
            val ble = adapter.bluetoothLeScanner
            if (ble == null) { onError("蓝牙扫描暂不可用，请重试或使用扫码配对"); return }
            scanner = ble
            val sessionCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handler.post {
                        if (callback !== this) return@post
                        try {
                            val name = result.scanRecord?.deviceName ?: result.device.name ?: return@post
                            val address = result.device.address ?: return@post
                            if (name.contains("KLCXKJ-Water", true) && QrCodeParser.normalizeSnCode(address) != null) {
                                onDeviceFound(NearbyDevice(name, address, result.rssi))
                            }
                        } catch (_: SecurityException) {
                            stop()
                            onError("附近设备权限已关闭，请授权后重试")
                        }
                    }
                }
                override fun onScanFailed(errorCode: Int) {
                    handler.post {
                        if (callback !== this) return@post
                        stop()
                        onError("扫描失败（$errorCode），请稍后重试或使用扫码配对")
                    }
                }
            }
            callback = sessionCallback
            ble.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), sessionCallback)
            timeout = Runnable {
                if (callback !== sessionCallback) return@Runnable
                stop()
                onFinished()
            }.also { handler.postDelayed(it, 8_000L) }
        } catch (_: SecurityException) {
            stop()
            onError("请允许附近设备权限后重试")
        } catch (_: IllegalStateException) {
            stop()
            onError("蓝牙状态发生变化，请开启蓝牙后重试")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val previous = callback
        callback = null // Invalidate queued results before releasing the radio.
        if (previous != null) runCatching { scanner?.stopScan(previous) }
        scanner = null
    }
}
