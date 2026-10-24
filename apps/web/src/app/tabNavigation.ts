import type { KeyboardEvent } from 'react'

/**
 * Arrow-key navigation for a [role="tablist"] of [role="tab"] buttons.
 * Pairs with roving tabIndex (active tab tabIndex=0, others -1) and panels
 * that carry role="tabpanel" + aria-labelledby. Left/Right/Up/Down move,
 * Home/End jump; activation focuses and clicks (controlled tabs re-render).
 */
export function handleTabArrowKeys(event: KeyboardEvent<HTMLElement>) {
  if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft' && event.key !== 'ArrowUp' && event.key !== 'ArrowDown' && event.key !== 'Home' && event.key !== 'End') return
  const target = event.currentTarget as HTMLElement
  const container = target.closest<HTMLElement>('[role="tablist"]')
  if (!container) return
  const tabs = Array.from(container.querySelectorAll<HTMLElement>('[role="tab"]:not([disabled])'))
  const index = tabs.indexOf(target)
  if (index === -1) return
  event.preventDefault()
  const lastIndex = tabs.length - 1
  let next: HTMLElement | undefined
  switch (event.key) {
    case 'ArrowRight':
    case 'ArrowDown':
      next = tabs[(index + 1) % tabs.length]
      break
    case 'ArrowLeft':
    case 'ArrowUp':
      next = tabs[(index - 1 + tabs.length) % tabs.length]
      break
    case 'Home':
      next = tabs[0]
      break
    case 'End':
      next = tabs[lastIndex]
      break
  }
  if (next) {
    next.focus()
    next.click()
  }
}
