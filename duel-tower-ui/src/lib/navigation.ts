export type PageArea = 'public' | 'app'

export type PageKey =
  | 'login'
  | 'hub'
  | 'home'
  | 'lobby'
  | 'decks'
  | 'card-detail'
  | 'character'
  | 'character-create'
  | 'character-detail'
  | 'inventory'
  | 'combat'
  | 'logs'
  | 'deck-editor'
  | 'player-lobby'
  | 'gm-lobby'

export type PageTag = {
  label: string
  tone?: 'accent' | 'muted' | 'success' | 'warning'
}

export type PageDefinition = {
  key: PageKey
  label: string
  path: string
  area?: PageArea
  title: string
  description: string
  eyebrow?: string
  tags?: PageTag[]
}

export type RouteParams = Record<string, string>

export type RouteMatch = {
  page: PageDefinition
  params: RouteParams
}

export const routePaths = {
  home: '/',
  login: '/',
  hub: '/hub',
  characterList: '/characters',
  characterCreate: '/characters/new',
  characterDetail: '/characters/detail',
  deckList: '/decks',
  cardDetail: '/cards',
  inventory: '/inventory',
  deckEditor: '/decks/editor',
  sessionEntry: '/lobby',
  sessionLobbyPlayer: '/lobby/player',
  sessionLobbyGm: '/lobby/gm',
  combat: '/combat',
} as const

export const routePatterns = {
  characterDetail: '/characters/:id',
  cardDetail: '/cards/:id',
  deckEditor: '/decks/:id/editor',
  sessionLobbyPlayer: '/sessions/:code/player',
  sessionLobbyGm: '/sessions/:code/gm',
  combat: '/sessions/:code/combat',
} as const

function hasRouteParam(value?: string): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function encodeRouteSegment(value: string) {
  return encodeURIComponent(value.trim())
}

function buildPathFromPattern(pattern: string, params: RouteParams) {
  return pattern.replace(/:([A-Za-z0-9_]+)/g, (_, key: string) => {
    const value = params[key]

    if (!hasRouteParam(value)) {
      throw new Error(`Missing route parameter: ${key}`)
    }

    return encodeRouteSegment(value)
  })
}

export const pathBuilders = {
  home: () => routePaths.home,
  login: () => routePaths.login,
  hub: () => routePaths.hub,
  characterList: () => routePaths.characterList,
  characterCreate: () => routePaths.characterCreate,
  characterDetail: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.characterDetail, { id })
      : routePaths.characterDetail,
  deckList: () => routePaths.deckList,
  cardDetail: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.cardDetail, { id })
      : routePaths.cardDetail,
  inventory: () => routePaths.inventory,
  deckEditor: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.deckEditor, { id })
      : routePaths.deckEditor,
  sessionEntry: () => routePaths.sessionEntry,
  sessionLobbyPlayer: (code?: string) =>
    hasRouteParam(code)
      ? buildPathFromPattern(routePatterns.sessionLobbyPlayer, { code })
      : routePaths.sessionLobbyPlayer,
  sessionLobbyGm: (code?: string) =>
    hasRouteParam(code)
      ? buildPathFromPattern(routePatterns.sessionLobbyGm, { code })
      : routePaths.sessionLobbyGm,
  combat: (code?: string) =>
    hasRouteParam(code)
      ? buildPathFromPattern(routePatterns.combat, { code })
      : routePaths.combat,
} as const

export const PAGES: PageDefinition[] = [
  {
    key: 'home',
    label: 'Home',
    path: routePaths.home,
    title: 'Home',
    description: '새로운 UI 기반을 위한 출발 지점입니다. 핵심 대시보드는 추후 재구성됩니다.',
  },
  {
    key: 'lobby',
    label: 'Lobby / Session',
    path: routePaths.sessionEntry,
    title: 'Lobby / Session',
    description: '세션 준비, 참여, 관리 흐름은 이후 요구사항에 맞춰 다시 설계될 예정입니다.',
  },
  {
    key: 'decks',
    label: 'Decks',
    path: routePaths.deckList,
    title: 'Decks',
    description: '덱 편집과 상세 인터랙션은 이후 단계에서 새 UX 기준으로 재구축됩니다.',
  },
  {
    key: 'character',
    label: 'Character',
    path: '/character',
    title: 'Character',
    description: '캐릭터 프로필/성장 화면은 향후 API 계약에 맞춰 재작성될 예정입니다.',
  },
  {
    key: 'inventory',
    label: 'Inventory',
    path: '/inventory',
    title: 'Inventory',
    description: '인벤토리 조회 및 아이템 동작 UI는 다음 단계에서 다시 구현합니다.',
  },
  {
    key: 'combat',
    label: 'Combat',
    path: routePaths.combat,
    title: 'Combat',
    description: '전투 보드 및 액션 UI는 별도 전투 설계안 기반으로 재개발될 예정입니다.',
  },
  {
    key: 'logs',
    label: 'Logs / Results',
    path: '/logs',
    title: 'Logs / Results',
    description: '전투 로그와 결과 뷰는 이후 정보 구조를 정리한 뒤 다시 구축됩니다.',
  },
]

const pageMap = new Map(PAGES.map((page) => [page.path, page]))

export const APP_NAV_ITEMS = [
  {
    key: 'hub',
    label: 'Hub',
    path: routePaths.hub,
    description: 'Overview and central entry point',
    enabled: true,
  },
  {
    key: 'character',
    label: 'Characters',
    path: routePaths.characterList,
    description: 'Roster and detail records',
    enabled: true,
  },
  {
    key: 'decks',
    label: 'Cards',
    path: routePaths.deckList,
    description: 'Card archive and lookup flow',
    enabled: true,
  },
  {
    key: 'inventory',
    label: 'Rules',
    path: routePaths.inventory,
    description: 'Keywords, statuses, and passive references',
    enabled: true,
  },
  {
    key: 'lobby',
    label: 'Session',
    path: routePaths.sessionEntry,
    description: 'Entry and lobby flow',
    enabled: true,
  },
  {
    key: 'combat',
    label: 'Combat',
    path: routePaths.combat,
    description: 'Combat command screen',
    enabled: true,
  },
] satisfies AppNavItem[]

export type AppNavItem = {
  key: Exclude<PageKey, 'login'>
  label: string
  path: string
  description: string
  enabled: boolean
}

const activePageMap = new Map<string, PageDefinition>([
  [
    routePaths.login,
    {
      key: 'login',
      label: 'Login',
      path: routePaths.login,
      area: 'public',
      title: 'Archive Access',
      description: 'Public entry screen for archive access.',
      eyebrow: 'Public Entry',
    },
  ],
  [
    routePaths.hub,
    {
      key: 'hub',
      label: 'Hub',
      path: routePaths.hub,
      area: 'app',
      title: 'Duel Tower Hub',
      description: 'Internal hub for overview, status, and next work targets.',
      eyebrow: 'Grand Archive',
      tags: [
        { label: 'Batch 1', tone: 'accent' },
        { label: 'MVP Shell', tone: 'success' },
      ],
    },
  ],
  [
    routePaths.characterList,
    {
      key: 'character',
      label: 'Characters',
      path: routePaths.characterList,
      area: 'app',
      title: 'Character Roster',
      description: 'Browse the roster and move into the next detail step from the current selection.',
      eyebrow: 'Roster Ledger',
      tags: [{ label: 'Batch 2', tone: 'accent' }],
    },
  ],
  [
    routePaths.deckList,
    {
      key: 'decks',
      label: 'Cards',
      path: routePaths.deckList,
      area: 'app',
      title: 'Card Archive',
      description: 'Browse the live card archive, search by name, and filter by type or keyword.',
      eyebrow: 'Content Archive',
      tags: [{ label: 'Batch 2', tone: 'accent' }],
    },
  ],
  [
    routePaths.cardDetail,
    {
      key: 'card-detail',
      label: 'Card Detail',
      path: routePaths.cardDetail,
      area: 'app',
      title: 'Card Detail',
      description: 'Review the selected card record from the live archive.',
      eyebrow: 'Card Record',
      tags: [{ label: 'Content API', tone: 'accent' }],
    },
  ],
  [
    routePaths.inventory,
    {
      key: 'inventory',
      label: 'Rules Reference',
      path: routePaths.inventory,
      area: 'app',
      title: 'Rules Reference',
      description: 'Review keywords, statuses, and passive definitions from the live content API.',
      eyebrow: 'Reference Archive',
      tags: [{ label: 'Content API', tone: 'accent' }],
    },
  ],
  [
    routePaths.sessionEntry,
    {
      key: 'lobby',
      label: 'Session Entry',
      path: routePaths.sessionEntry,
      area: 'app',
      title: 'Session Entry',
      description: 'Enter by code or choose from open sessions before moving into the player lobby.',
      eyebrow: 'Session Gate',
      tags: [{ label: 'Batch 2', tone: 'accent' }],
    },
  ],
  [
    routePaths.characterDetail,
    {
      key: 'character-detail',
      label: 'Character Detail',
      path: routePaths.characterDetail,
      area: 'app',
      title: 'Character Detail / Edit',
      description: 'Read the selected adventurer record and prepare the next edit step.',
      eyebrow: 'Record Detail',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    routePaths.characterCreate,
    {
      key: 'character-create',
      label: 'Character Create',
      path: routePaths.characterCreate,
      area: 'app',
      title: 'Character Create',
      description: 'Create a new adventurer record before moving back into the roster flow.',
      eyebrow: 'Record Creation',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    routePaths.deckEditor,
    {
      key: 'deck-editor',
      label: 'Deck Editor',
      path: routePaths.deckEditor,
      area: 'app',
      title: 'Deck Editor',
      description: 'Review the selected deck structure before wiring the real editor actions.',
      eyebrow: 'Tactical Editor',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    routePaths.sessionLobbyPlayer,
    {
      key: 'player-lobby',
      label: 'Player Lobby',
      path: routePaths.sessionLobbyPlayer,
      area: 'app',
      title: 'Player Lobby',
      description: 'Inspect session summary, participant slots, and readiness flow from the player side.',
      eyebrow: 'Session Lobby',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    routePaths.sessionLobbyGm,
    {
      key: 'gm-lobby',
      label: 'GM Lobby',
      path: routePaths.sessionLobbyGm,
      area: 'app',
      title: 'GM Lobby',
      description: 'Manage participant readiness and session controls from the GM side.',
      eyebrow: 'Session Control',
      tags: [{ label: 'Batch 4', tone: 'accent' }],
    },
  ],
  [
    routePaths.combat,
    {
      key: 'combat',
      label: 'Combat Command',
      path: routePaths.combat,
      area: 'app',
      title: 'Combat Command',
      description: 'Review battle state, issue commands, and track the action log in one screen.',
      eyebrow: 'Battlefield Control',
      tags: [{ label: 'Batch 4', tone: 'accent' }],
    },
  ],
])

const dynamicRouteEntries = [
  { pattern: routePatterns.characterDetail, page: activePageMap.get(routePaths.characterDetail) },
  { pattern: routePatterns.cardDetail, page: activePageMap.get(routePaths.cardDetail) },
  { pattern: routePatterns.deckEditor, page: activePageMap.get(routePaths.deckEditor) },
  { pattern: routePatterns.sessionLobbyPlayer, page: activePageMap.get(routePaths.sessionLobbyPlayer) },
  { pattern: routePatterns.sessionLobbyGm, page: activePageMap.get(routePaths.sessionLobbyGm) },
  { pattern: routePatterns.combat, page: activePageMap.get(routePaths.combat) },
].filter((entry): entry is { pattern: string; page: PageDefinition } => entry.page !== undefined)

function getPathSegments(pathname: string) {
  const normalized = normalizePath(pathname)

  if (normalized === routePaths.home) {
    return [] as string[]
  }

  return normalized.slice(1).split('/')
}

export function matchRoutePattern(pattern: string, pathname: string): RouteParams | null {
  const patternSegments = getPathSegments(pattern)
  const pathSegments = getPathSegments(pathname)

  if (patternSegments.length !== pathSegments.length) {
    return null
  }

  const params: RouteParams = {}

  for (const [index, patternSegment] of patternSegments.entries()) {
    const pathSegment = pathSegments[index]

    if (patternSegment.startsWith(':')) {
      const key = patternSegment.slice(1)

      if (!key || !pathSegment) {
        return null
      }

      params[key] = decodeURIComponent(pathSegment)
      continue
    }

    if (patternSegment !== pathSegment) {
      return null
    }
  }

  return params
}

export function normalizePath(pathname: string): string {
  if (!pathname) return routePaths.home

  const withoutHash = pathname.split('#')[0] ?? pathname
  const withoutQuery = withoutHash.split('?')[0] ?? withoutHash

  if (!withoutQuery) return routePaths.home

  return withoutQuery.endsWith('/') && withoutQuery !== routePaths.home
    ? withoutQuery.slice(0, -1)
    : withoutQuery
}

export function resolveRouteMatch(pathname: string): RouteMatch | null {
  const normalized = normalizePath(pathname)
  const staticPage = activePageMap.get(normalized) ?? pageMap.get(normalized)

  if (staticPage) {
    return {
      page: staticPage,
      params: {},
    }
  }

  for (const entry of dynamicRouteEntries) {
    const params = matchRoutePattern(entry.pattern, normalized)

    if (params) {
      return {
        page: {
          ...entry.page,
          path: normalized,
        },
        params,
      }
    }
  }

  return null
}

export function resolvePage(pathname: string): PageDefinition {
  return (
    resolveRouteMatch(pathname)?.page ??
    activePageMap.get(routePaths.login) ??
    pageMap.get(routePaths.home) ??
    PAGES[0]
  )
}
