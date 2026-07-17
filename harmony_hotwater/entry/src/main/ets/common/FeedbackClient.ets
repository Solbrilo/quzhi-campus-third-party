import http from '@ohos.net.http'

export class FeedbackCategory {
  static readonly BUG: string = 'bug'
  static readonly SUGGESTION: string = 'suggestion'
  static readonly EXPERIENCE: string = 'experience'
  static readonly OTHER: string = 'other'
}

export enum FeedbackSubmitState {
  SUCCESS = 0,
  RATE_LIMITED = 1,
  REJECTED = 2,
  FAILED = 3
}

export class FeedbackSubmitResult {
  state: FeedbackSubmitState = FeedbackSubmitState.FAILED
  message: string = ''
  feedbackId: string = ''
  retryAfterSeconds: number = -1
}

class FeedbackPayload {
  schemaVersion: number = 1
  platform: string = 'harmonyos'
  appVersion: string = ''
  category: string = ''
  message: string = ''
}

export class FeedbackClient {
  private activeClient?: http.HttpRequest

  cancelActiveRequest(): void {
    if (this.activeClient) {
      this.activeClient.destroy()
      this.activeClient = undefined
    }
  }

  async submit(appVersion: string, category: string, message: string): Promise<FeedbackSubmitResult> {
    const normalizedMessage = message.trim()
    const validationMessage = FeedbackClient.validationError(normalizedMessage)
    if (validationMessage.length > 0) {
      const rejected = new FeedbackSubmitResult()
      rejected.state = FeedbackSubmitState.REJECTED
      rejected.message = validationMessage
      return rejected
    }

    const payload = new FeedbackPayload()
    payload.appVersion = appVersion
    payload.category = category
    payload.message = normalizedMessage

    const client = http.createHttp()
    this.cancelActiveRequest()
    this.activeClient = client
    try {
      const response = await client.request(FEEDBACK_ENDPOINT, {
        method: http.RequestMethod.POST,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 5000,
        readTimeout: 10000,
        header: {
          Accept: 'application/json',
          'Content-Type': 'application/json; charset=utf-8'
        },
        extraData: JSON.stringify(payload)
      })
      return FeedbackClient.mapResponse(
        response.responseCode,
        String(response.result),
        FeedbackClient.readRetryAfter(response.header)
      )
    } catch (error) {
      const failed = new FeedbackSubmitResult()
      failed.state = FeedbackSubmitState.FAILED
      failed.message = '提交失败，请检查网络后手动重试。反馈内容已保留。'
      return failed
    } finally {
      if (this.activeClient === client) {
        client.destroy()
        this.activeClient = undefined
      }
    }
  }

  static countUnicodeCharacters(value: string): number {
    let count = 0
    let index = 0
    while (index < value.length) {
      const current = value.charCodeAt(index)
      if (current >= 0xD800 && current <= 0xDBFF && index + 1 < value.length) {
        const next = value.charCodeAt(index + 1)
        if (next >= 0xDC00 && next <= 0xDFFF) {
          index += 2
          count++
          continue
        }
      }
      index++
      count++
    }
    return count
  }

  static validationError(message: string): string {
    const length = FeedbackClient.countUnicodeCharacters(message)
    if (length < 2) {
      return '请至少填写 2 个字。'
    }
    if (length > 2000) {
      return '反馈内容不能超过 2000 个字。'
    }
    return ''
  }

  static buildPayloadForTest(appVersion: string, category: string, message: string): string {
    const payload = new FeedbackPayload()
    payload.appVersion = appVersion
    payload.category = category
    payload.message = message
    return JSON.stringify(payload)
  }

  static mapResponse(statusCode: number, responseBody: string, retryAfter: string,
    nowMillis: number = Date.now()): FeedbackSubmitResult {
    const result = new FeedbackSubmitResult()
    if (statusCode === 201) {
      try {
        const response = JSON.parse(responseBody) as Record<string, Object>
        const feedbackId = String(response['feedbackId'] ?? '').trim()
        if (feedbackId.length > 0) {
          result.state = FeedbackSubmitState.SUCCESS
          result.feedbackId = feedbackId
          result.message = '提交成功。'
          return result
        }
      } catch (error) {
      }
      result.message = '服务器未返回有效反馈编号，反馈内容已保留。'
      return result
    }
    if (statusCode === 429) {
      result.state = FeedbackSubmitState.RATE_LIMITED
      result.retryAfterSeconds = FeedbackClient.parseRetryAfter(retryAfter, nowMillis)
      result.message = '提交过于频繁。'
      return result
    }
    switch (statusCode) {
      case 400:
      case 413:
      case 415:
      case 422:
        result.message = '反馈内容未通过校验，请检查后手动重试。'
        break
      case 403:
        result.message = '当前请求无法提交，请稍后手动重试。'
        break
      case 500:
      case 503:
        result.message = '反馈服务暂时不可用，内容已保留，请稍后手动重试。'
        break
      default:
        result.message = `提交失败（HTTP ${statusCode}），内容已保留，请稍后手动重试。`
    }
    return result
  }

  static parseRetryAfter(value: string, nowMillis: number = Date.now()): number {
    const normalized = value.trim()
    if (normalized.length === 0) {
      return -1
    }
    if (/^\d+$/.test(normalized)) {
      return Math.max(0, Number(normalized))
    }
    const retryAt = Date.parse(normalized)
    if (Number.isNaN(retryAt)) {
      return -1
    }
    return Math.ceil(Math.max(0, retryAt - nowMillis) / 1000)
  }

  private static readRetryAfter(headers: Object): string {
    const values = headers as Record<string, Object>
    const keys = Object.keys(values)
    for (let index = 0; index < keys.length; index++) {
      if (keys[index].toLowerCase() === 'retry-after') {
        return String(values[keys[index]] ?? '')
      }
    }
    return ''
  }
}

const FEEDBACK_ENDPOINT: string = 'https://nebularive.top/api/hotwater-feedback.php'
