export class ScannerSessionCoordinator {
  private operationQueue: Promise<void> = Promise.resolve()
  private activeToken: number = 0
  private nextToken: number = 0
  private activeCleanup: (() => Promise<void>) | null = null

  createToken(): number {
    this.nextToken++
    return this.nextToken
  }

  owns(token: number): boolean {
    return token > 0 && this.activeToken === token
  }

  async acquire(
    token: number,
    startAction: () => boolean,
    cleanupAction: () => Promise<void>
  ): Promise<boolean> {
    let acquired = false
    await this.enqueue(async () => {
      if (token <= 0) {
        return
      }
      if (this.activeToken !== 0 && this.activeToken !== token) {
        await this.cleanupActive()
      }
      this.activeToken = token
      this.activeCleanup = cleanupAction
      try {
        acquired = startAction()
        if (!acquired && this.activeToken === token) {
          this.activeToken = 0
          this.activeCleanup = null
        }
      } catch (error) {
        await this.cleanupActive()
        throw new Error(String(error))
      }
    })
    return acquired
  }

  async release(token: number): Promise<boolean> {
    let released = false
    await this.enqueue(async () => {
      if (!this.owns(token)) {
        return
      }
      released = true
      await this.cleanupActive()
    })
    return released
  }

  private async cleanupActive(): Promise<void> {
    const cleanupAction = this.activeCleanup
    this.activeToken = 0
    this.activeCleanup = null
    if (cleanupAction) {
      await cleanupAction()
    }
  }

  private enqueue(operation: () => Promise<void>): Promise<void> {
    const scheduled = this.operationQueue.then(() => operation())
    this.operationQueue = this.recover(scheduled)
    return scheduled
  }

  private async recover(operation: Promise<void>): Promise<void> {
    try {
      await operation
    } catch (error) {
    }
  }
}
