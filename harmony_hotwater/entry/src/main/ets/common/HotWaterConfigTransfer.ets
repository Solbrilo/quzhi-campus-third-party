import { HotWaterConfig } from './HotWaterModels'

class ConfigExportPayload {
  projectId: number = 0
  userId: number = 0
  accountId: number = 0
  loginCode: string = ''
  telephone: string = ''
  snCode: string = ''
  macAddress: string = ''
  phoneSystem: string = ''
  version: string = ''
  exportedAt: number = 0
  appVersion: string = ''
}

export class HotWaterConfigTransfer {
  static serialize(
    config: HotWaterConfig,
    exportedAt: number = Date.now(),
    appVersion: string = '2.0.1'
  ): string {
    const validationError = HotWaterConfigTransfer.validate(config)
    if (validationError.length > 0) {
      throw new Error(validationError)
    }
    const payload = new ConfigExportPayload()
    payload.projectId = config.projectId
    payload.userId = config.userId
    payload.accountId = config.accountId
    payload.loginCode = config.loginCode
    payload.telephone = config.telephone
    payload.snCode = config.snCode
    payload.macAddress = config.macAddress
    payload.phoneSystem = config.phoneSystem
    payload.version = config.version
    payload.exportedAt = exportedAt
    payload.appVersion = appVersion
    return JSON.stringify(payload)
  }

  static parse(rawValue: string): HotWaterConfig {
    let payload: Record<string, Object>
    try {
      const parsed = JSON.parse(rawValue) as Object
      if (!parsed || typeof parsed !== 'object') {
        throw new Error('配置文件不是 JSON 对象')
      }
      payload = parsed as Record<string, Object>
    } catch (error) {
      throw new Error('配置文件格式错误')
    }

    const config = new HotWaterConfig()
    config.projectId = Number(payload['projectId'])
    config.userId = Number(payload['userId'])
    config.accountId = Number(payload['accountId'])
    config.loginCode = String(payload['loginCode'] ?? '').trim()
    config.telephone = String(payload['telephone'] ?? '').trim()
    config.snCode = String(payload['snCode'] ?? '').trim().replace(/:/g, '').toUpperCase()
    config.macAddress = String(payload['macAddress'] ?? '').trim()
    config.phoneSystem = String(payload['phoneSystem'] ?? '').trim()
    config.version = String(payload['version'] ?? '').trim()
    const validationError = HotWaterConfigTransfer.validate(config)
    if (validationError.length > 0) {
      throw new Error(validationError)
    }
    return config
  }

  static validate(config: HotWaterConfig): string {
    if (!Number.isInteger(config.projectId) || config.projectId <= 0) {
      return '配置缺少有效的 projectId'
    }
    if (!Number.isInteger(config.userId) || config.userId <= 0) {
      return '配置缺少有效的 userId'
    }
    if (!Number.isInteger(config.accountId) || config.accountId <= 0) {
      return '配置缺少有效的 accountId'
    }
    if (config.loginCode.trim().length === 0) {
      return '配置缺少 loginCode'
    }
    if (config.telephone.trim().length === 0) {
      return '配置缺少 telephone'
    }
    if (config.snCode.trim().length === 0) {
      return '配置缺少 snCode'
    }
    return ''
  }

  static generateFileName(): string {
    return 'hotwater_config.json'
  }
}
