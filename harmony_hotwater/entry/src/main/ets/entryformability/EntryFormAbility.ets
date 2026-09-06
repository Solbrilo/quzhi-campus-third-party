import { formBindingData, FormExtensionAbility, formInfo } from '@kit.FormKit'
import { Want } from '@kit.AbilityKit'
import formProvider from '@ohos.app.form.formProvider'
import { HotWaterStore } from '../common/HotWaterStore'
import { HotWaterRepository } from '../common/HotWaterRepository'
import { RuntimeDeviceInfoResolver } from '../common/RuntimeDeviceInfoResolver'
import { LaunchActionBridge } from '../common/LaunchActionBridge'

class WidgetFormState {
  statusText: string = '待命'
  statusTone: string = 'idle'
}

export default class EntryFormAbility extends FormExtensionAbility {
  onAddForm(want: Want) {
    const state = new WidgetFormState()
    state.statusText = '准备就绪'
    state.statusTone = 'idle'
    return formBindingData.createFormBindingData(state)
  }

  onCastToNormalForm(formId: string) {
  }

  onUpdateForm(formId: string) {
    this.refreshWidgetState(formId)
  }

  onChangeFormVisibility(newStatus: Record<string, number>) {
  }

  onFormEvent(formId: string, message: string) {
    this.handleWidgetAction(formId, message)
  }

  onRemoveForm(formId: string) {
  }

  onAcquireFormState(want: Want) {
    return formInfo.FormState.READY
  }

  private handleWidgetAction(formId: string, message: string): void {
    try {
      let action: string = ''
      try {
        const parsed = JSON.parse(message) as Record<string, Object>
        action = parsed['widgetAction'] ? String(parsed['widgetAction']) : ''
      } catch (error) {
        action = message
      }
      if (action.length === 0) {
        return
      }

      const store = new HotWaterStore(this.context)
      const repository = new HotWaterRepository(store)
      this.executeAction(formId, store, repository, action)
    } catch (error) {
      LaunchActionBridge.setPendingAction('config')
    }
  }

  private executeAction(formId: string, store: HotWaterStore, repository: HotWaterRepository, action: string): void {
    this.updateWidgetState(formId, action === 'open' ? '正在开启…' : '正在关闭…', action)

    store.loadConfig()
      .then((config) => {
        const prepared = RuntimeDeviceInfoResolver.enrichConfig(config)
        if (!prepared.isComplete()) {
          throw new Error('config_incomplete')
        }
        if (action === 'open') {
          return repository.open(prepared)
        }
        if (action === 'close') {
          return repository.close(prepared)
        }
        throw new Error('unknown_action')
      })
      .then((result) => {
        if (result.snapshot && result.snapshot.isDeviceOnline === false) {
          this.updateWidgetState(
            formId,
            '热水器不在线，请确认寝室供电后重试。',
            'error'
          )
          return
        }
        if (result.snapshot && result.snapshot.isDeviceBusyByOther) {
          this.updateWidgetState(
            formId,
            '设备正在使用中',
            'error'
          )
          return
        }
        this.updateWidgetState(
          formId,
          action === 'open'
            ? '已开启热水'
            : (result.automaticClose ? '检测到热水已自动关闭' : '已关闭热水'),
          action === 'open' ? 'open_success' : 'close_success'
        )
      })
      .catch((error: Error) => {
        this.updateWidgetState(
          formId,
          action === 'open' ? '开启失败' : '关闭失败',
          'error'
        )
        if (action === 'open' || action === 'close') {
          LaunchActionBridge.setPendingAction(action)
        } else {
          LaunchActionBridge.setPendingAction('config')
        }
      })
  }

  private refreshWidgetState(formId: string): void {
    const store = new HotWaterStore(this.context)
    const repository = new HotWaterRepository(store)
    store.loadConfig().then((config) => {
      if (!config.isComplete()) {
        this.updateWidgetState(formId, '请先打开应用完成配置', 'error')
        return null
      }
      return repository.check(RuntimeDeviceInfoResolver.enrichConfig(config))
    }).then((result) => {
      if (!result) return
      const snapshot = result.snapshot
      if (snapshot.isDeviceOnline === false) this.updateWidgetState(formId, '热水器不在线', 'error')
      else if (snapshot.isDeviceBusyByOther) this.updateWidgetState(formId, '设备正在使用中', 'error')
      else if (snapshot.hasCachedOrder) this.updateWidgetState(formId, '用水中', 'close_success')
      else if (result.automaticClose) this.updateWidgetState(formId, '热水已自动关闭', 'close_success')
      else this.updateWidgetState(formId, '已就绪', 'idle')
    }).catch(() => {
      this.updateWidgetState(formId, '状态待确认，请打开应用刷新', 'error')
    })
  }

  private updateWidgetState(formId: string, statusText: string, statusTone: string): void {
    const state = new WidgetFormState()
    state.statusText = statusText
    state.statusTone = statusTone
    formProvider.updateForm(formId, formBindingData.createFormBindingData(state))
      .then(() => {
      })
      .catch(() => {
      })
  }
}
