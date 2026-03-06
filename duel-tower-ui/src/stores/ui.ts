import { writable } from 'svelte/store'

type ConfirmPayload = {
  title: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
}

type ConfirmState = {
  open: boolean
  payload: ConfirmPayload | null
  resolver: ((result: boolean) => void) | null
}

type TooltipState = {
  open: boolean
  text: string
  x: number
  y: number
}

const initialConfirm: ConfirmState = {
  open: false,
  payload: null,
  resolver: null,
}

export const confirmAction = writable<ConfirmState>(initialConfirm)
export const ruleTooltip = writable<TooltipState>({ open: false, text: '', x: 0, y: 0 })

export function requestConfirm(payload: ConfirmPayload) {
  return new Promise<boolean>((resolve) => {
    confirmAction.set({ open: true, payload, resolver: resolve })
  })
}

export function resolveConfirm(result: boolean) {
  confirmAction.update((state) => {
    state.resolver?.(result)
    return initialConfirm
  })
}

export function showRuleTooltip(text: string, event: MouseEvent) {
  ruleTooltip.set({ open: true, text, x: event.clientX + 12, y: event.clientY + 12 })
}

export function hideRuleTooltip() {
  ruleTooltip.set({ open: false, text: '', x: 0, y: 0 })
}
