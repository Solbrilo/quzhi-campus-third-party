const DEVICE_QR_PREFIX: string = 'KLCXKJ-Water'
const DEVICE_CODE_PATTERN: RegExp = /^[0-9A-F]{12}$/

export class QrCodeParser {
  static normalizeSnCode(rawValue: string): string | null {
    const snCode = rawValue
      .trim()
      .replace(/:/g, '')
      .toUpperCase()
    return DEVICE_CODE_PATTERN.test(snCode) ? snCode : null
  }

  static parseSnCode(rawValue: string): string | null {
    const fields: string[] = rawValue
      .trim()
      .replace(/^\uFEFF/, '')
      .split(',')
      .map((field: string) => field.trim())
    if (fields.length < 3 ||
      fields[0].toLowerCase() !== DEVICE_QR_PREFIX.toLowerCase() ||
      fields[1].length === 0) {
      return null
    }
    return QrCodeParser.normalizeSnCode(fields[fields.length - 1])
  }
}
