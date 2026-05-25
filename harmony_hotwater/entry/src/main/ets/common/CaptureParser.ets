import { HotWaterConfig } from './HotWaterModels'

function extractFirst(text: string, patterns: Array<RegExp>, fieldName: string): string {
  for (let index = 0; index < patterns.length; index++) {
    const match = text.match(patterns[index])
    if (match && match[1]) {
      return match[1]
    }
  }
  throw new Error(`未能从抓包文件中提取 ${fieldName}`)
}

function optionalMatch(text: string, pattern: RegExp): string {
  const match = text.match(pattern)
  return match && match[1] ? match[1] : ''
}

export class CaptureParser {
  static parse(text: string): HotWaterConfig {
    const config = new HotWaterConfig()
    config.projectId = Number(extractFirst(text, [/projectId=(\d+)/], 'projectId'))
    config.userId = Number(extractFirst(text, [/userId=(\d+)/], 'userId'))
    config.accountId = Number(extractFirst(text, [/accountId=(\d+)/], 'accountId'))
    config.loginCode = extractFirst(text, [/loginCode=([0-9a-f]{32})/], 'loginCode')
    config.telephone = extractFirst(text, [/telephone=(\d+)/, /telPhone=(\d+)/], 'telephone')
    config.snCode = extractFirst(text, [/snCode=([0-9A-Fa-f]+)/, /macAddress=([0-9A-Fa-f:]+)/], 'snCode')
      .replace(/:/g, '')
      .toUpperCase()
    config.macAddress = optionalMatch(text, /macAddress=([0-9A-Fa-f:]+)/).toUpperCase()
    config.phoneSystem = optionalMatch(text, /phoneSystem=([A-Za-z0-9_]+)/) || 'Harmony'
    config.version = optionalMatch(text, /version=([0-9.]+)/) || '0.0.1'
    return config
  }
}
