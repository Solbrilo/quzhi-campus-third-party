export class HotWaterPayload {
  private static readonly MONEY_SCALE: number = 1000
  private static readonly MIN_CONSUMPTION_YUAN: number = 0.0005

  static extractBalanceYuan(text: string): number {
    const stringValue = HotWaterPayload.firstNumber(text, [
      'accountRealMoneyStr',
      'accountMoneyStr',
      'balanceMoneyStr',
      'realMoneyStr',
      'money'
    ])
    if (stringValue >= 0) {
      return stringValue
    }

    const rawValue = HotWaterPayload.firstNumber(text, [
      'accountRealMoney',
      'accountMoney'
    ])
    if (rawValue >= 0) {
      return rawValue / HotWaterPayload.MONEY_SCALE
    }
    throw new Error('余额响应缺少有效金额。')
  }

  static extractConsumptionYuan(text: string): number {
    const stringValue = HotWaterPayload.firstNumber(text, [
      'consumeMoneyStr',
      'consumptionMoneyStr',
      'actualConsumeMoneyStr',
      'actualMoneyStr',
      'useMoneyStr',
      'costMoneyStr'
    ])
    if (stringValue >= HotWaterPayload.MIN_CONSUMPTION_YUAN) {
      return HotWaterPayload.roundYuan(stringValue)
    }

    const rawValue = HotWaterPayload.firstNumber(text, [
      'consumeMoney',
      'consumptionMoney',
      'actualConsumeMoney',
      'actualMoney',
      'useMoney',
      'costMoney'
    ])
    const rawConsumptionYuan = rawValue / HotWaterPayload.MONEY_SCALE
    if (rawConsumptionYuan >= HotWaterPayload.MIN_CONSUMPTION_YUAN) {
      return HotWaterPayload.roundYuan(rawConsumptionYuan)
    }
    return -1
  }

  static extractConfirmedConsumptionYuan(text: string): number {
    const successMatch = text.match(/"success"\s*:\s*(true|false)/i)
    const errorCodeMatch = text.match(/"errorCode"\s*:\s*(-?\d+)/i)
    const succeeded = !!(successMatch && successMatch[1] && successMatch[1].toLowerCase() === 'true')
    const errorCode = errorCodeMatch && errorCodeMatch[1] ? Number(errorCodeMatch[1]) : -1
    if (!succeeded || errorCode !== 0) {
      return -1
    }
    return HotWaterPayload.extractConsumptionYuan(text)
  }

  private static firstNumber(text: string, keys: string[]): number {
    for (let index = 0; index < keys.length; index++) {
      const rawValue = HotWaterPayload.extractFirstOrEmpty(text, keys[index])
      if (rawValue.length === 0) {
        continue
      }
      const normalized = rawValue.replace('¥', '').replace(/,/g, '').trim()
      const parsed = Number(normalized)
      if (Number.isFinite(parsed) && parsed >= 0) {
        return parsed
      }
    }
    return -1
  }

  private static extractFirstOrEmpty(text: string, key: string): string {
    const pattern = new RegExp('"' + key + '"\\s*:\\s*(?:"([^"]*)"|([^",}\\]\\s]+))', 'i')
    const match = text.match(pattern)
    const value = match && match[1] !== undefined
      ? String(match[1])
      : (match && match[2] !== undefined ? String(match[2]) : '')
    return value === 'null' ? '' : value
  }

  private static roundYuan(value: number): number {
    return Math.round(value * HotWaterPayload.MONEY_SCALE) / HotWaterPayload.MONEY_SCALE
  }
}
