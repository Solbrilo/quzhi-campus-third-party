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
      .then(() => {
        this.updateWidgetState(
          formId,
          action === 'open' ? '已开启热水' : '已关闭热水',
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
