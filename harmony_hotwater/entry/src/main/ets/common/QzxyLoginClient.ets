import http from '@ohos.net.http'
import { buffer } from '@kit.ArkTS'
import { cryptoFramework } from '@kit.CryptoArchitectureKit'
import { HotWaterConfig } from './HotWaterModels'

const BASE_URL: string = 'https://v3-api.china-qzxy.cn'
export const QZXY_LOGIN_CLIENT_VERSION: string = '1.1.11'
const CONNECT_TIMEOUT_MS: number = 10000
const READ_TIMEOUT_MS: number = 10000

interface LoginHeaders {
  Accept: string
  'Accept-Encoding': string
  'Content-Type': string
  'User-Agent': string
}

class LoginHttpResponse {
  statusCode: number = 0
  body: string = ''
}

export class QzxyLoginResult {
  auth: QzxyAuthResult = new QzxyAuthResult()
  phoneSystem: string = ''
  version: string = ''

  mergeInto(existing: HotWaterConfig): HotWaterConfig {
    const merged = this.auth.mergeInto(existing)
    const sameAccount = existing.telephone.length === 0 || existing.telephone === merged.telephone
    merged.snCode = sameAccount ? existing.snCode : ''
    merged.macAddress = sameAccount ? existing.macAddress : ''
    merged.phoneSystem = this.phoneSystem.length > 0 ? this.phoneSystem : existing.phoneSystem
    merged.version = this.version.length > 0 ? this.version : existing.version
    return merged
  }
}

export class QzxyAuthResult {
  projectId: number = 0
  userId: number = 0
  accountId: number = 0
  loginCode: string = ''
  telephone: string = ''

  mergeInto(existing: HotWaterConfig): HotWaterConfig {
    const merged = new HotWaterConfig()
    merged.projectId = this.projectId > 0 ? this.projectId : existing.projectId
    merged.userId = this.userId > 0 ? this.userId : existing.userId
    merged.accountId = this.accountId > 0 ? this.accountId : existing.accountId
    merged.loginCode = this.loginCode.trim().length > 0 ? this.loginCode.trim() : existing.loginCode
    merged.telephone = this.telephone.trim().length > 0 ? this.telephone.trim() : existing.telephone
    merged.snCode = existing.snCode
    merged.macAddress = existing.macAddress
    merged.phoneSystem = existing.phoneSystem
    merged.version = existing.version
    return merged
  }
}

export class QzxyLoginClient {
  async login(
    telephone: string,
    password: string,
    phoneSystem: string,
    version: string
  ): Promise<QzxyLoginResult> {
    const normalizedTelephone = telephone.trim()
    if (normalizedTelephone.length === 0 || password.length === 0) {
      throw new Error('请输入趣智校园手机号和密码。')
    }

    const body = QzxyLoginClient.buildRequestBody(normalizedTelephone, password, phoneSystem, version)
    const response = await this.request(body)
    return QzxyLoginClient.parseResponse(response.statusCode, response.body)
  }

  static encodePassword(password: string): string {
    // Charles capture 3.chls shows the official client sends the uppercase MD5 tail.
    const md = cryptoFramework.createMd('MD5')
    const encoded = buffer.from(password, 'utf-8')
    const bytes = new Uint8Array(encoded.buffer, encoded.byteOffset, encoded.length)
    md.updateSync({ data: bytes })
    const digest = md.digestSync().data
    return QzxyLoginClient.formatPasswordDigest(digest)
  }

  static formatPasswordDigest(digest: Uint8Array): string {
    if (digest.length !== 16) {
      throw new Error('无法生成趣智校园登录密码摘要。')
    }
    const alphabet = '0123456789ABCDEF'
    let hex = ''
    for (let index = 0; index < digest.length; index++) {
      const value = digest[index]
      hex += alphabet.charAt(value >> 4) + alphabet.charAt(value & 0x0F)
    }
    return hex.substring(hex.length - 10)
  }

  static buildRequestBody(
    telephone: string,
    password: string,
    phoneSystem: string,
    version: string
  ): string {
    return QzxyLoginClient.buildRequestBodyWithPasswordToken(
      telephone,
      QzxyLoginClient.encodePassword(password),
      phoneSystem,
      version
    )
  }

  static buildRequestBodyWithPasswordToken(
    telephone: string,
    passwordToken: string,
    phoneSystem: string,
    version: string
  ): string {
    return 'version=' + encodeURIComponent(version.length > 0 ? version : QZXY_LOGIN_CLIENT_VERSION) +
      '&phoneSystem=' + encodeURIComponent(phoneSystem.length > 0 ? phoneSystem : 'harmonyos') +
      '&telephone=' + encodeURIComponent(telephone.trim()) +
      '&password=' + encodeURIComponent(passwordToken) +
      '&type=0'
  }

  static parseResponse(statusCode: number, responseBody: string): QzxyLoginResult {
    const responseError = statusCode < 200 || statusCode >= 300
      ? `趣智校园登录请求失败 (HTTP ${statusCode})。`
      : '趣智校园返回了无效的登录响应。'
    const payload = QzxyLoginClient.parseObject(responseBody, responseError)
    if (statusCode < 200 || statusCode >= 300) {
      throw new Error(QzxyLoginClient.failureMessage(payload, `趣智校园登录请求失败 (HTTP ${statusCode})。`))
    }
    const success = payload['success'] === true
    const rawErrorCode = payload['errorCode']
    const hasErrorCode = rawErrorCode !== undefined && rawErrorCode !== null
    const errorCode = Number(rawErrorCode)
    if (!success || (hasErrorCode && (!Number.isFinite(errorCode) || errorCode !== 0))) {
      throw new Error(QzxyLoginClient.failureMessage(payload, '趣智校园账号或密码错误，或登录已被拒绝。'))
    }

    const data = QzxyLoginClient.readObject(payload, 'data')
    const userAccount = QzxyLoginClient.readObject(data, 'userAccount')
    const result = new QzxyLoginResult()
    result.auth.projectId = QzxyLoginClient.readPositiveNumber(userAccount, 'projectId')
    result.auth.userId = QzxyLoginClient.readPositiveNumber(data, 'userId')
    result.auth.accountId = QzxyLoginClient.readPositiveNumber(userAccount, 'accountId')
    result.auth.loginCode = QzxyLoginClient.readText(data, 'loginCode')
    result.auth.telephone = QzxyLoginClient.readText(data, 'telephone')
    result.phoneSystem = QzxyLoginClient.readText(data, 'phoneSystem')
    result.version = QzxyLoginClient.readText(data, 'version')
    if (result.auth.projectId <= 0 || result.auth.userId <= 0 || result.auth.accountId <= 0 ||
      result.auth.loginCode.length === 0 || result.auth.telephone.length === 0) {
      throw new Error('趣智校园登录响应缺少项目、账户或登录凭据。')
    }
    return result
  }

  private async request(body: string): Promise<LoginHttpResponse> {
    const client = http.createHttp()
    try {
      const headers: LoginHeaders = {
        Accept: 'application/json, text/plain, */*',
        'Accept-Encoding': 'deflate, gzip, br',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'User-Agent': 'libcurl-agent/1.0'
      }
      const response = await client.request(`${BASE_URL}/user/login`, {
        method: http.RequestMethod.POST,
        extraData: body,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: CONNECT_TIMEOUT_MS,
        readTimeout: READ_TIMEOUT_MS,
        header: headers
      })
      const result = new LoginHttpResponse()
      result.statusCode = response.responseCode
      result.body = String(response.result)
      return result
    } catch (error) {
      const detail = QzxyLoginClient.displayError(error)
      throw new Error(detail.length > 0 ? `无法连接趣智校园：${detail}` : '无法连接趣智校园，请检查网络后重试。')
    } finally {
      client.destroy()
    }
  }

  private static parseObject(responseBody: string, errorMessage: string): Record<string, Object> {
    try {
      const value = JSON.parse(responseBody) as Object
      if (value && typeof value === 'object') {
        return value as Record<string, Object>
      }
    } catch (error) {
    }
    throw new Error(errorMessage)
  }

  private static readObject(parent: Record<string, Object>, key: string): Record<string, Object> {
    const value = parent[key]
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      return value as Record<string, Object>
    }
    throw new Error(`趣智校园登录响应缺少 ${key}。`)
  }

  private static readText(parent: Record<string, Object>, key: string): string {
    const value = parent[key]
    return value === undefined || value === null ? '' : String(value).trim()
  }

  private static readPositiveNumber(parent: Record<string, Object>, key: string): number {
    const value = Number(parent[key] ?? 0)
    return Number.isFinite(value) && value > 0 ? value : 0
  }

  static displayError(error: Object): string {
    let detail = ''
    if (error && typeof error === 'object') {
      const message = (error as Record<string, Object>)['message']
      if (message !== undefined && message !== null) {
        detail = String(message)
      }
    }
    if (detail.length === 0) {
      detail = String(error)
    }
    detail = detail.replace(/^Error:\s*/, '').trim()
    return detail === '[object Object]' ? '' : detail
  }

  private static failureMessage(payload: Record<string, Object>, fallback: string): string {
    const message = QzxyLoginClient.readText(payload, 'errorMessage') ||
      QzxyLoginClient.readText(payload, 'message') ||
      QzxyLoginClient.readText(payload, 'msg') || fallback
    const code = Number(payload['errorCode'] ?? payload['code'])
    return Number.isFinite(code) && code !== 0 ? `${message}（错误码 ${code}）` : message
  }
}
