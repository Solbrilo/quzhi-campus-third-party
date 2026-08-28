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

class GiteeReleaseFetchResult {
  success: boolean = false
  responseCode: number = 0
  message: string = ''
  releases: Object[] = []
}

export class GiteeUpdateRepository {
  async check(currentVersion: string): Promise<UpdateCheckResult> {
    const result = new UpdateCheckResult()
    const releasesResult = await this.fetchPublishedReleases(`QZXY-HotWater-Harmony/${currentVersion}`)
    if (!releasesResult.success) {
      if (releasesResult.responseCode === 404) {
        result.state = UpdateCheckState.NO_RELEASE
        result.message = '暂无已发布版本。'
      } else {
        result.state = UpdateCheckState.FAILED
        result.message = releasesResult.message
      }
      return result
    }

    const latest = this.findLatestHarmonyRelease(releasesResult.releases)
    if (!latest) {
      result.state = UpdateCheckState.NO_RELEASE
      result.message = '暂无已发布的 HarmonyOS 版本。'
      return result
    }

    const versionName = this.parseReleaseVersion(String(latest['tag_name'] ?? ''))
    if (versionName.length === 0) {
      result.state = UpdateCheckState.INVALID_RELEASE
      result.message = '远端 HarmonyOS 发行版标签必须使用 vX.Y.Z 格式。'
      return result
    }
    if (this.compareVersions(versionName, currentVersion) <= 0) {
      result.state = UpdateCheckState.UP_TO_DATE
      result.message = '当前已是最新版本。'
      return result
    }
    const packageUrl = this.findHarmonyPackageUrl(latest, versionName)
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

  async listReleaseLogs(): Promise<ReleaseLogFetchResult> {
    const result = new ReleaseLogFetchResult()
    const releasesResult = await this.fetchPublishedReleases('QZXY-HotWater-Harmony/ReleaseLogs')
    if (!releasesResult.success) {
      if (releasesResult.responseCode === 404) {
        result.message = '远端发行版列表不存在。'
      } else {
        result.message = releasesResult.message.replace('检查更新', '加载更新日志')
      }
      return result
    }

    if (releasesResult.releases.length === 0) {
      result.message = '远端暂无可显示的更新日志。'
      return result
    }
    result.releases = this.parseReleaseLogs(releasesResult.releases)
    result.success = result.releases.length > 0
    result.message = result.success ? '' : '远端发行版中的更新日志格式无效。'
    return result
  }

  private async fetchPublishedReleases(userAgent: string): Promise<GiteeReleaseFetchResult> {
    const result = new GiteeReleaseFetchResult()
    const client = http.createHttp()
    try {
      const response = await client.request(GITEE_RELEASES_URL, {
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

      const parsed = JSON.parse(String(response.result)) as Object
      if (!Array.isArray(parsed)) {
        result.message = '远端发行版列表格式无效。'
        return result
      }
      result.success = true
      result.releases = parsed as Object[]
      return result
    } catch (error) {
      result.message = '检查更新失败，请检查网络后重试。'
      return result
    } finally {
      client.destroy()
    }
  }

  /** Builds the changelog only from Gitee's published release entries. */
  private parseReleaseLogs(value: Object[]): HarmonyReleaseLog[] {
    const releases: HarmonyReleaseLog[] = []
    for (let index = 0; index < value.length; index++) {
      const source = this.asRecord(value[index])
      if (!source) {
        continue
      }
      const versionName = this.parseReleaseVersion(String(source['tag_name'] ?? ''))
      if (versionName.length === 0) {
        continue
      }
      const existingIndex = this.findVersionIndex(releases, versionName)
      if (existingIndex >= 0) {
        this.mergeRemoteRelease(releases[existingIndex], source)
        continue
      }
      const release = new HarmonyReleaseLog()
      release.versionName = versionName
      release.releaseNotes = String(source['body'] ?? '').trim()
      release.releasedAt = String(source['created_at'] ?? '').trim()
      releases.push(release)
    }
    releases.sort((left: HarmonyReleaseLog, right: HarmonyReleaseLog): number =>
      this.compareVersions(right.versionName, left.versionName)
    )
    return releases
  }

  private mergeRemoteRelease(target: HarmonyReleaseLog, source: Record<string, Object>): void {
    const body = String(source['body'] ?? '').trim()
    const publishedAt = String(source['created_at'] ?? '').trim()
    if (target.releaseNotes.trim().length === 0 && body.length > 0) {
      target.releaseNotes = body
    }
    if (target.releasedAt.trim().length === 0 && publishedAt.length > 0) {
      target.releasedAt = publishedAt
    }
  }

  private findVersionIndex(releases: HarmonyReleaseLog[], versionName: string): number {
    for (let index = 0; index < releases.length; index++) {
      if (releases[index].versionName === versionName) {
        return index
      }
    }
    return -1
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

  private findLatestHarmonyRelease(releases: Object[]): Record<string, Object> | null {
    let latest: Record<string, Object> | null = null
    let latestVersion = ''
    for (let index = 0; index < releases.length; index++) {
      const release = this.asRecord(releases[index])
      if (!release) {
        continue
      }
      const versionName = this.parseReleaseVersion(String(release['tag_name'] ?? ''))
      if (versionName.length === 0 ||
        !this.isHarmonyPackageUrl(this.findHarmonyPackageUrl(release, versionName), versionName)) {
        continue
      }
      if (latest === null || this.compareVersions(versionName, latestVersion) > 0) {
        latest = release
        latestVersion = versionName
      }
    }
    return latest
  }

  private findHarmonyPackageUrl(release: Record<string, Object>, versionName: string): string {
    const rawAssets = release['assets']
    if (!Array.isArray(rawAssets)) {
      return ''
    }
    const assets = rawAssets as Object[]
    for (let index = 0; index < assets.length; index++) {
      const asset = this.asRecord(assets[index])
      if (!asset) {
        continue
      }
      const name = String(asset['name'] ?? '').trim()
      const url = String(asset['browser_download_url'] ?? '').trim()
      if (this.isHarmonyPackageName(name, versionName) && this.isHarmonyPackageUrl(url, versionName)) {
        return url
      }
    }
    return ''
  }

  private isHarmonyPackageName(value: string, versionName: string): boolean {
    return value === `hotwater-harmony-v${versionName}.hap`
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

const GITEE_RELEASES_URL: string =
  'https://gitee.com/api/v5/repos/Solbrilo/quzhi-campus-third-party/releases?per_page=100&page=1'
