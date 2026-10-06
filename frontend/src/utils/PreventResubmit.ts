import type { DirectiveBinding } from 'vue'

/**
 * 防重复提交指令.
 * 使用方式：`v-prevent-resubmit`
 */
interface ExtendedHTMLElement extends HTMLElement {
  _preventResubmitTimer?: number
  _preventResubmitDisabled?: boolean
}

type DelayBinding = DirectiveBinding<number | undefined>

const resolveDelay = (binding: DelayBinding, fallback: number): number => {
  if (typeof binding.value === 'number' && binding.value > 0) {
    return binding.value
  }
  return fallback
}

export const preventResubmit = {
  mounted(el: ExtendedHTMLElement, binding: DelayBinding) {
    const delay = resolveDelay(binding, 2000)

    el.addEventListener('click', () => {
      if (el._preventResubmitDisabled) {
        return
      }

      el._preventResubmitDisabled = true
      el.classList.add('is-disabled')
      el.setAttribute('disabled', 'disabled')

      const originalText = el.textContent ?? ''
      if (el.tagName === 'BUTTON') {
        el.textContent = '提交中...'
      }

      el._preventResubmitTimer = window.setTimeout(() => {
        el._preventResubmitDisabled = false
        el.classList.remove('is-disabled')
        el.removeAttribute('disabled')

        if (el.tagName === 'BUTTON') {
          el.textContent = originalText
        }
      }, delay)
    })
  },

  unmounted(el: ExtendedHTMLElement) {
    if (el._preventResubmitTimer) {
      clearTimeout(el._preventResubmitTimer)
    }
  },
}

/**
 * 防重复点击指令（轻量版）.
 * 使用方式：`v-debounce-click`
 */
export const debounceClick = {
  mounted(el: ExtendedHTMLElement, binding: DelayBinding) {
    const delay = resolveDelay(binding, 500)
    let timer: number | null = null

    el.addEventListener('click', (event: Event) => {
      if (timer) {
        event.stopImmediatePropagation()
        return
      }

      timer = window.setTimeout(() => {
        timer = null
      }, delay)
    })
  },
}
