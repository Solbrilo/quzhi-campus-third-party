import http from '@ohos.net.http'

export enum UpdateCheckState {
  IDLE = 0,
  UPDATE_AVAILABLE = 1,
  UP_TO_DATE = 2,
  NO_RELEASE = 3,
  INVALID_RELEASE = 4,
  FAILED = 5
}

export class HarmonyUpdateInfo {
  versionName: string = ''
  releaseNotes: string = ''
}

export class UpdateCheckResult {
  state: UpdateCheckState = UpdateCheckState.IDLE
  message: string = ''
  update: HarmonyUpdateInfo | null = null
}

export class GiteeUpdateRepository {
  async check(currentVersion: string): Promise<UpdateCheckResult> {
    const result = new UpdateCheckResult()
    const client = http.createHttp()
    try {
      const response = await client.request(LATEST_RELEASE_URL, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        header: {
          Accept: 'application/json',
          'User-Agent': `QZXY-HotWater-Harmony/${currentVersion}`
        }
      })
      if (response.responseCode === 404) {
        result.state = UpdateCheckState.NO_RELEASE
        result.message = '暂无已发布版本。'
        return result
      }
      if (response.responseCode < 200 || response.responseCode >= 300) {
        result.state = UpdateCheckState.FAILED
        result.message = `检查更新失败：HTTP ${response.responseCode}`
        return result
      }

      const release = JSON.parse(String(response.result)) as Record<string, Object>
      const versionName = this.parseReleaseVersion(String(release['tag_name'] ?? ''))
      if (versionName.length === 0) {
        result.state = UpdateCheckState.INVALID_RELEASE
        result.message = 'Release 标签必须使用 vX.Y.Z 格式。'
        return result
      }
      if (this.compareVersions(versionName, currentVersion) <= 0) {
        result.state = UpdateCheckState.UP_TO_DATE
        result.message = '当前已是最新版本。'
        return result
      }
      if (!this.hasHarmonyPackage(release['assets'], versionName)) {
        result.state = UpdateCheckState.INVALID_RELEASE
        result.message = 'Release 中未找到 Harmony HAP。'
        return result
      }

      const update = new HarmonyUpdateInfo()
      update.versionName = versionName
      update.releaseNotes = String(release['body'] ?? '').trim()
      result.state = UpdateCheckState.UPDATE_AVAILABLE
      result.update = update
      result.message = `发现 v${versionName}，请查看发布说明完成更新。`
      return result
    } catch (error) {
      result.state = UpdateCheckState.FAILED
      result.message = '检查更新失败，请检查网络后重试。'
      return result
    } finally {
      client.destroy()
    }
  }

  private hasHarmonyPackage(value: Object | undefined, versionName: string): boolean {
    if (!Array.isArray(value)) {
      return false
    }
    const expectedName = `hotwater-harmony-v${versionName}.hap`
    const assets = value as Array<Record<string, Object>>
    for (let index = 0; index < assets.length; index++) {
      const name = String(assets[index]['name'] ?? '')
      if (name === expectedName) {
        return true
      }
    }
    return false
  }

  private parseReleaseVersion(tag: string): string {
    const match = tag.trim().match(/^v?(\d+)\.(\d+)\.(\d+)$/)
    if (!match || match.length < 4) {
      return ''
    }
    return `${match[1]}.${match[2]}.${match[3]}`
  }

  private compareVersions(left: string, right: string): number {
    const leftParts = left.split('.')
    const rightParts = right.split('.')
    for (let index = 0; index < 3; index++) {
      const difference = Number(leftParts[index]) - Number(rightParts[index])
      if (difference !== 0) {
        return difference
      }
    }
    return 0
  }
}

const LATEST_RELEASE_URL: string =
  'https://gitee.com/api/v5/repos/Solbrilo/quzhi-campus-third-party/releases/latest'
