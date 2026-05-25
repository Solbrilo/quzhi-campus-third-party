import deviceInfo from '@ohos.deviceInfo'
import wifiManager from '@ohos.wifiManager'
import { HotWaterConfig, RuntimeDeviceInfo } from './HotWaterModels'

export class RuntimeDeviceInfoResolver {
  static resolve(): RuntimeDeviceInfo {
    const runtimeInfo = new RuntimeDeviceInfo()
    try {
      const macs = wifiManager.getDeviceMacAddress()
      if (macs.length > 0) {
        runtimeInfo.macAddressLabel = macs[0]
      }
    } catch (error) {
      runtimeInfo.macAddressLabel = '系统已隐藏'
    }

    const marketName = deviceInfo.marketName || deviceInfo.productModel || deviceInfo.brand || 'HarmonyOS 设备'
    const versionText = deviceInfo.osFullName || ('Harmony API ' + String(deviceInfo.sdkApiVersion))
    runtimeInfo.systemLabel = `${marketName} · ${versionText}`
    runtimeInfo.phoneSystem = deviceInfo.manufacture || 'Harmony'
    runtimeInfo.version = String(deviceInfo.sdkApiVersion || '0.0.1')
    return runtimeInfo
  }

  static enrichConfig(config: HotWaterConfig): HotWaterConfig {
    const runtime = RuntimeDeviceInfoResolver.resolve()
    const enriched = new HotWaterConfig()
    enriched.projectId = config.projectId
    enriched.userId = config.userId
    enriched.accountId = config.accountId
    enriched.loginCode = config.loginCode
    enriched.telephone = config.telephone
    enriched.snCode = config.snCode
    enriched.macAddress = runtime.macAddressLabel === '系统已隐藏' ? config.macAddress : runtime.macAddressLabel
    enriched.phoneSystem = config.phoneSystem.length > 0 ? config.phoneSystem : runtime.phoneSystem
    enriched.version = config.version.length > 0 ? config.version : runtime.version
    return enriched
  }
}
