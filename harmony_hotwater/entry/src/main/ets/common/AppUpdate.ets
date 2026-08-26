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

export class HarmonyReleaseLog {
  versionName: string = ''
  releaseNotes: string = ''
  releasedAt: string = ''
}

export class ReleaseLogFetchResult {
  success: boolean = false
  message: string = ''
  releases: HarmonyReleaseLog[] = []
}

export class UpdateCheckResult {
  state: UpdateCheckState = UpdateCheckState.IDLE
  message: string = ''
  update: HarmonyUpdateInfo | null = null
}

class UpdateManifestFetchResult {
  success: boolean = false
  responseCode: number = 0
  message: string = ''
  manifest: Record<string, Object> | null = null
}

export class GiteeUpdateRepository {
  async check(currentVersion: string): Promise<UpdateCheckResult> {
    const result = new UpdateCheckResult()
    const manifestResult = await this.fetchUpdateManifest(`QZXY-HotWater-Harmony/${currentVersion}`)
    if (!manifestResult.success || !manifestResult.manifest) {
      if (manifestResult.responseCode === 404) {
        result.state = UpdateCheckState.NO_RELEASE
        result.message = '暂无已发布版本。'
      } else {
        result.state = UpdateCheckState.FAILED
        result.message = manifestResult.message
      }
      return result
    }

    const platform = this.asRecord(manifestResult.manifest['harmony'])
    if (!platform) {
      result.state = UpdateCheckState.NO_RELEASE
      result.message = '暂无已发布的 HarmonyOS 版本。'
      return result
    }
    const latest = this.asRecord(platform['latest'])
    if (!latest) {
      result.state = UpdateCheckState.NO_RELEASE
      result.message = '暂无已发布的 HarmonyOS 版本。'
      return result
    }

    const versionName = this.parseReleaseVersion(String(latest['tagName'] ?? ''))
    if (versionName.length === 0) {
      result.state = UpdateCheckState.INVALID_RELEASE
      result.message = '更新清单中的 HarmonyOS 标签必须使用 vX.Y.Z 格式。'
      return result
    }
    if (this.compareVersions(versionName, currentVersion) <= 0) {
      result.state = UpdateCheckState.UP_TO_DATE
      result.message = '当前已是最新版本。'
      return result
    }
    const packageUrl = String(latest['url'] ?? '').trim()
    if (!this.isHarmonyPackageUrl(packageUrl, versionName)) {
      result.state = UpdateCheckState.INVALID_RELEASE
      result.message = '当前 HarmonyOS 更新包尚未发布，已忽略。'
      return result
    }

    const update = new HarmonyUpdateInfo()
    update.versionName = versionName
    update.releaseNotes = String(latest['body'] ?? '').trim()
    result.state = UpdateCheckState.UPDATE_AVAILABLE
    result.update = update
    result.message = `已发布 v${versionName}，请下载 HarmonyOS HAP 手动更新。`
    return result
  }

  async listReleaseLogs(currentVersion: string = ''): Promise<ReleaseLogFetchResult> {
    const result = new ReleaseLogFetchResult()
    const manifestResult = await this.fetchUpdateManifest('QZXY-HotWater-Harmony/ReleaseLogs')
    if (!manifestResult.success || !manifestResult.manifest) {
      if (manifestResult.responseCode === 404) {
        result.releases = this.parseReleaseLogs([], undefined, currentVersion)
        result.success = result.releases.length > 0
        result.message = result.success ? '' : '暂时没有可显示的更新日志。'
      } else {
        // The update manifest is published separately from the app. Show the installed package's
        // own release note when that service is stale or temporarily unavailable.
        result.releases = this.parseReleaseLogs([], undefined, currentVersion)
        result.success = result.releases.length > 0
        result.message = result.success
          ? ''
          : manifestResult.message.replace('检查更新', '加载更新日志')
      }
      return result
    }

    const releases = manifestResult.manifest['releases']
    const releaseEntries: Object[] = Array.isArray(releases) ? releases as Object[] : []
    const harmony = this.asRecord(manifestResult.manifest['harmony'])
    const latest = harmony ? harmony['latest'] : undefined
    if (releaseEntries.length === 0 && !this.asRecord(latest)) {
      result.releases = this.parseReleaseLogs([], undefined, currentVersion)
      result.success = result.releases.length > 0
      result.message = result.success ? '' : '更新清单中的更新日志格式无效。'
      return result
    }
    result.releases = this.parseReleaseLogs(releaseEntries, latest, currentVersion)
    result.success = true
    result.message = result.releases.length > 0 ? '' : '暂时没有可显示的更新日志。'
    return result
  }

  private async fetchUpdateManifest(userAgent: string): Promise<UpdateManifestFetchResult> {
    const result = new UpdateManifestFetchResult()
    const client = http.createHttp()
    try {
      const response = await client.request(UPDATE_MANIFEST_URL, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 10000,
        readTimeout: 30000,
        usingProtocol: http.HttpProtocol.HTTP1_1,
        header: {
          Accept: 'application/json',
          'User-Agent': userAgent
        }
      })
      result.responseCode = response.responseCode
      if (response.responseCode < 200 || response.responseCode >= 300) {
        result.message = `检查更新失败：HTTP ${response.responseCode}`
        return result
      }

      const manifest = this.asRecord(JSON.parse(String(response.result)) as Object)
      if (!manifest || Number(manifest['schemaVersion'] ?? 0) !== UPDATE_MANIFEST_SCHEMA_VERSION) {
        result.message = '更新清单格式无效。'
        return result
      }
      result.success = true
      result.manifest = manifest
      return result
    } catch (error) {
      result.message = '检查更新失败，请检查网络后重试。'
      return result
    } finally {
      client.destroy()
    }
  }

  private parseReleaseLogs(value: Object[], latest?: Object, currentVersion: string = ''): HarmonyReleaseLog[] {
    const releases: HarmonyReleaseLog[] = []
    const values: Object[] = value.slice()
    if (latest !== undefined) {
      values.push(latest)
    }
    for (let index = 0; index < values.length; index++) {
      const source = this.asRecord(values[index])
      if (!source) {
        continue
      }
      const versionName = this.parseReleaseVersion(String(source['tagName'] ?? ''))
      if (versionName.length === 0 || this.containsVersion(releases, versionName)) {
        continue
      }
      const release = new HarmonyReleaseLog()
      release.versionName = versionName
      release.releaseNotes = String(source['body'] ?? '').trim()
      release.releasedAt = String(source['publishedAt'] ?? '').trim()
      releases.push(release)
    }
    this.mergeLocalRelease(releases, this.buildLocalCurrentRelease(LOCAL_CURRENT_RELEASE_VERSION))

    // A package installed before the latest release should still show that release in its
    // history. A future package also gets a small local placeholder until the remote body lands.
    const installedVersion = currentVersion.trim()
    if (installedVersion.length > 0 && installedVersion !== LOCAL_CURRENT_RELEASE_VERSION &&
      /^\d+\.\d+\.\d+$/.test(installedVersion)) {
      this.mergeLocalRelease(releases, this.buildLocalCurrentRelease(installedVersion))
    }
    releases.sort((left: HarmonyReleaseLog, right: HarmonyReleaseLog): number =>
      this.compareVersions(right.versionName, left.versionName)
    )
    return releases
  }

  private buildLocalCurrentRelease(versionName: string): HarmonyReleaseLog | null {
    const normalized = versionName.trim()
    if (!/^\d+\.\d+\.\d+$/.test(normalized)) {
      return null
    }
    const release = new HarmonyReleaseLog()
    release.versionName = normalized
    release.releaseNotes = normalized === LOCAL_CURRENT_RELEASE_VERSION
      ? LOCAL_CURRENT_RELEASE_BODY
      : '本版本更新说明暂未同步到远端更新清单。'
    release.releasedAt = ''
    return release
  }

  private mergeLocalRelease(releases: HarmonyReleaseLog[], fallback: HarmonyReleaseLog | null): void {
    if (!fallback) {
      return
    }
    let currentIndex = -1
    for (let index = 0; index < releases.length; index++) {
      if (releases[index].versionName === fallback.versionName) {
        currentIndex = index
        break
      }
    }
    if (currentIndex < 0) {
      releases.push(fallback)
      return
    }
    if (releases[currentIndex].releaseNotes.trim().length === 0) {
      releases[currentIndex].releaseNotes = fallback.releaseNotes
    }
  }

  private containsVersion(releases: HarmonyReleaseLog[], versionName: string): boolean {
    for (let index = 0; index < releases.length; index++) {
      if (releases[index].versionName === versionName) {
        return true
      }
    }
    return false
  }

  private asRecord(value: Object | undefined): Record<string, Object> | null {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
      return null
    }
    return value as Record<string, Object>
  }

  private isHarmonyPackageUrl(value: string, versionName: string): boolean {
    const path = value.split(/[?#]/)[0]
    return /^https:\/\/.+/.test(value) &&
      path.endsWith(`hotwater-harmony-v${versionName}.hap`)
  }

  private parseReleaseVersion(tag: string): string {
    const match = tag.trim().match(/^v(\d+)\.(\d+)\.(\d+)$/)
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

const UPDATE_MANIFEST_URL: string =
  'https://gitee.com/Solbrilo/quzhi-campus-third-party/raw/main/update-manifest.json'
const UPDATE_MANIFEST_SCHEMA_VERSION: number = 1
const LOCAL_CURRENT_RELEASE_VERSION: string = '2.2.1'
const LOCAL_CURRENT_RELEASE_BODY: string =
  '# v2.2.1\n\n' +
  '- 修复从非配置页清空所有信息后，配置表单仍可能恢复旧字段的问题。\n' +
  '- 清空操作会同步失效旧页面状态和在途配置写入，确保账号、设备、账单、反馈和设置数据不再回写。'
