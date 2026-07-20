import { HotWaterConfig } from './HotWaterModels'

function extractLatest(text: string, patterns: Array<RegExp>, fieldName: string): string {
  let latestIndex = -1
  let latestValue = ''
  for (let index = 0; index < patterns.length; index++) {
    const expression = new RegExp(patterns[index].source, 'g')
    let match = expression.exec(text)
    while (match !== null) {
      if (match[1] && match.index >= latestIndex) {
        latestIndex = match.index
        latestValue = match[1]
      }
      match = expression.exec(text)
    }
  }
  if (latestIndex >= 0) {
    return latestValue
  }
  throw new Error(`未能从抓包文件中提取 ${fieldName}`)
}

function optionalLatest(text: string, pattern: RegExp): string {
  const expression = new RegExp(pattern.source, 'g')
  let latestValue = ''
  let match = expression.exec(text)
  while (match !== null) {
    if (match[1]) {
      latestValue = match[1]
    }
    match = expression.exec(text)
  }
  return latestValue
}

export class CaptureParser {
  static parse(text: string): HotWaterConfig {
    const config = new HotWaterConfig()
    config.projectId = Number(extractLatest(text, [/projectId=(\d+)/], 'projectId'))
    config.userId = Number(extractLatest(text, [/userId=(\d+)/], 'userId'))
    config.accountId = Number(extractLatest(text, [/accountId=(\d+)/], 'accountId'))
    config.loginCode = extractLatest(text, [/loginCode=([0-9a-fA-F]{32})/], 'loginCode')
    config.telephone = extractLatest(text, [/telephone=(\d+)/, /telPhone=(\d+)/], 'telephone')
    config.snCode = extractLatest(text, [/snCode=([0-9A-Fa-f]+)/, /macAddress=([0-9A-Fa-f:]+)/], 'snCode')
      .replace(/:/g, '')
      .toUpperCase()
    config.macAddress = optionalLatest(text, /macAddress=([0-9A-Fa-f:]+)/).toUpperCase()
    config.phoneSystem = optionalLatest(text, /phoneSystem=([A-Za-z0-9_]+)/) || 'Harmony'
    config.version = optionalLatest(text, /version=([0-9.]+)/) || '0.0.1'
    return config
  }
}
