import { describe, expect, it } from '@ohos/hypium'
import { HotWaterConfig } from '../main/ets/common/HotWaterModels'
import { QzxyLoginClient, QZXY_LOGIN_CLIENT_VERSION } from '../main/ets/common/QzxyLoginClient'

export default function qzxyLoginClientTest() {
  describe('qzxyLoginClientTest', () => {
    it('formatsKnownMd5DigestLikeOfficialClient', 0, () => {
      const abcMd5 = new Uint8Array([
        0x90, 0x01, 0x50, 0x98, 0x3C, 0xD2, 0x4F, 0xB0,
        0xD6, 0x96, 0x3F, 0x7D, 0x28, 0xE1, 0x7F, 0x72
      ])
      expect(QzxyLoginClient.formatPasswordDigest(abcMd5)).assertEqual('7D28E17F72')
    })

    it('buildsRequestBodyLikeOfficialClient', 0, () => {
      expect(QzxyLoginClient.buildRequestBodyWithPasswordToken(
        ' 13800138000 ',
        '7D28E17F72',
        'harmonyos',
        QZXY_LOGIN_CLIENT_VERSION
      )).assertEqual(
        'version=1.1.11&phoneSystem=harmonyos&telephone=13800138000&password=7D28E17F72&type=0'
      )
    })

    it('rejectsNonzeroErrorCodeEvenWhenSuccessIsTrue', 0, () => {
      expect(() => {
        QzxyLoginClient.parseResponse(
          200,
          '{"success":true,"errorCode":401,"errorMessage":"用户名或密码不正确"}'
        )
      }).assertThrowError('用户名或密码不正确（错误码 401）')
    })

    it('parsesLoginResponseAndKeepsSameAccountDeviceCode', 0, () => {
      const result = QzxyLoginClient.parseResponse(
        200,
        '{"success":true,"data":{"userId":2,"telephone":"13800138000",' +
          '"loginCode":"new-code","phoneSystem":"harmonyos","version":"1.2.3",' +
          '"userAccount":{"projectId":3,"accountId":4}}}'
      )
      const existing = new HotWaterConfig()
      existing.telephone = '13800138000'
      existing.snCode = 'AABBCC'

      const merged = result.mergeInto(existing)

      expect(merged.projectId).assertEqual(3)
      expect(merged.userId).assertEqual(2)
      expect(merged.accountId).assertEqual(4)
      expect(merged.loginCode).assertEqual('new-code')
      expect(merged.snCode).assertEqual('AABBCC')
    })

    it('clearsDeviceCodeForDifferentAccount', 0, () => {
      const result = QzxyLoginClient.parseResponse(
        200,
        '{"success":true,"data":{"userId":2,"telephone":"13900139000",' +
          '"loginCode":"new-code","userAccount":{"projectId":3,"accountId":4}}}'
      )
      const existing = new HotWaterConfig()
      existing.telephone = '13800138000'
      existing.snCode = 'AABBCC'

      expect(result.mergeInto(existing).snCode).assertEqual('')
    })

    it('includesServiceErrorCodeInLoginFailureMessage', 0, () => {
      expect(() => {
        QzxyLoginClient.parseResponse(
          200,
          '{"success":false,"errorCode":401,"errorMessage":"用户名或密码不正确"}'
        )
      }).assertThrowError('用户名或密码不正确（错误码 401）')
    })

    it('keepsAlternativeServiceAndNetworkErrorMessagesReadable', 0, () => {
      expect(() => {
        QzxyLoginClient.parseResponse(
          200,
          '{"success":false,"code":403,"msg":"账号已被锁定"}'
        )
      }).assertThrowError('账号已被锁定（错误码 403）')
      expect(QzxyLoginClient.displayError(new Error('连接超时'))).assertEqual('连接超时')
    })

    it('reportsHttpStatusWhenTheErrorBodyIsNotJson', 0, () => {
      expect(() => {
        QzxyLoginClient.parseResponse(502, '<html>gateway error</html>')
      }).assertThrowError('趣智校园登录请求失败 (HTTP 502)。')
    })
  })
}
