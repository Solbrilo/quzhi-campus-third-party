import preferences from '@ohos.data.preferences'
import type common from '@ohos.app.ability.common'
import { HotWaterConfig } from './HotWaterModels'

export class HotWaterStore {
  private static readonly STORE_NAME: string = 'hotwater_store'
  private static readonly ORDER_RECOVERY_GRACE_MS: number = 12000
  private context: common.Context

  constructor(context: common.Context) {
    this.context = context
  }

  private async getStore(): Promise<preferences.Preferences> {
    return await preferences.getPreferences(this.context, HotWaterStore.STORE_NAME)
  }

  async loadConfig(): Promise<HotWaterConfig> {
    const store = await this.getStore()
    const config = new HotWaterConfig()
    config.projectId = Number(await store.get('projectId', 0))
    config.userId = Number(await store.get('userId', 0))
    config.accountId = Number(await store.get('accountId', 0))
    config.loginCode = String(await store.get('loginCode', ''))
    config.telephone = String(await store.get('telephone', ''))
    config.snCode = String(await store.get('snCode', ''))
    config.macAddress = String(await store.get('macAddress', ''))
    config.phoneSystem = String(await store.get('phoneSystem', ''))
    config.version = String(await store.get('version', ''))
    return config
  }

  async saveConfig(config: HotWaterConfig): Promise<void> {
    const store = await this.getStore()
    await store.put('projectId', config.projectId)
    await store.put('userId', config.userId)
    await store.put('accountId', config.accountId)
    await store.put('loginCode', config.loginCode)
    await store.put('telephone', config.telephone)
    await store.put('snCode', config.snCode)
    await store.put('macAddress', config.macAddress)
    await store.put('phoneSystem', config.phoneSystem)
    await store.put('version', config.version)
    await store.flush()
  }

  async loadOrderNo(): Promise<string> {
    const store = await this.getStore()
    return String(await store.get('orderNo', ''))
  }

  async saveOrderNo(orderNo: string): Promise<void> {
    const store = await this.getStore()
    await store.put('orderNo', orderNo)
    await store.put('orderClearedAt', 0)
    await store.flush()
  }

  async clearOrderNo(): Promise<void> {
    const store = await this.getStore()
    await store.put('orderNo', '')
    await store.put('orderClearedAt', Date.now())
    await store.flush()
  }

  async shouldIgnoreRecoveredOrder(): Promise<boolean> {
    const store = await this.getStore()
    const clearedAt = Number(await store.get('orderClearedAt', 0))
    return clearedAt > 0 && Date.now() - clearedAt < HotWaterStore.ORDER_RECOVERY_GRACE_MS
  }
}
