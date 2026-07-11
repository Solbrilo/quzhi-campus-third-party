import preferences from '@ohos.data.preferences'
import type common from '@ohos.app.ability.common'
import { ConsumptionRecord, HotWaterConfig, UsageSession } from './HotWaterModels'

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

  async loadUsageSession(): Promise<UsageSession | null> {
    const store = await this.getStore()
    const startedAtMillis = Number(await store.get('usageStartedAtMillis', 0))
    if (startedAtMillis <= 0) {
      return null
    }
    const session = new UsageSession()
    session.startedAtMillis = startedAtMillis
    session.startBalanceYuan = Number(await store.get('usageStartBalanceYuan', 0))
    return session
  }

  async startUsageSessionIfAbsent(startBalanceYuan: number): Promise<UsageSession> {
    const existing = await this.loadUsageSession()
    if (existing) {
      return existing
    }
    const session = new UsageSession()
    session.startedAtMillis = Date.now()
    session.startBalanceYuan = startBalanceYuan
    const store = await this.getStore()
    await store.put('usageStartedAtMillis', session.startedAtMillis)
    await store.put('usageStartBalanceYuan', session.startBalanceYuan)
    await store.flush()
    return session
  }

  async clearUsageSession(): Promise<void> {
    const store = await this.getStore()
    await store.delete('usageStartedAtMillis')
    await store.delete('usageStartBalanceYuan')
    await store.flush()
  }

  async saveConsumptionRecord(record: ConsumptionRecord): Promise<void> {
    const records = await this.loadConsumptionRecords()
    records.unshift(record)
    if (records.length > 20) {
      records.splice(20)
    }
    const store = await this.getStore()
    await store.put('consumptionRecords', JSON.stringify(records))
    await store.flush()
  }

  async loadConsumptionRecords(): Promise<ConsumptionRecord[]> {
    const store = await this.getStore()
    const rawValue = String(await store.get('consumptionRecords', ''))
    if (rawValue.length === 0) {
      return []
    }
    try {
      const parsed = JSON.parse(rawValue) as Array<Record<string, Object>>
      const records: ConsumptionRecord[] = []
      parsed.forEach((item) => {
        const record = new ConsumptionRecord()
        record.id = Number(item['id'])
        record.startedAtMillis = Number(item['startedAtMillis'])
        record.endedAtMillis = Number(item['endedAtMillis'])
        record.consumptionYuan = Number(item['consumptionYuan'])
        record.usageDurationMillis = Number(item['usageDurationMillis'])
        if (record.id > 0) {
          records.push(record)
        }
      })
      return records
    } catch (error) {
      return []
    }
  }

  async shouldIgnoreRecoveredOrder(): Promise<boolean> {
    const store = await this.getStore()
    const clearedAt = Number(await store.get('orderClearedAt', 0))
    return clearedAt > 0 && Date.now() - clearedAt < HotWaterStore.ORDER_RECOVERY_GRACE_MS
  }
}
