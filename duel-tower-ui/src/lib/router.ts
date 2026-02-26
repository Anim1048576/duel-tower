import { writable } from 'svelte/store'

const base = import.meta.env.PROD ? '/ui' : ''

function stripBase(pathname: string) {
  if (base && pathname.startsWith(base)) {
    const rest = pathname.slice(base.length)
    return rest.startsWith('/') ? rest : '/' + rest
  }
  return pathname || '/'
}

function withBase(path: string) {
  if (!path.startsWith('/')) path = '/' + path
  return base ? base + path : path
}

export const route = writable(stripBase(window.location.pathname))

export function startRouter() {
  const onPop = () => route.set(stripBase(window.location.pathname))
  window.addEventListener('popstate', onPop)
  return () => window.removeEventListener('popstate', onPop)
}

export function navigate(path: string) {
  const to = withBase(path)
  history.pushState({}, '', to)
  route.set(stripBase(window.location.pathname))
}

export function isActive(path: string) {
  let cur: string
  route.subscribe(v => (cur = v))()
  return cur === path
}