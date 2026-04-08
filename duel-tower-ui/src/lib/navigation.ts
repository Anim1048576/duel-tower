export type PageArea = 'public' | 'app'

export type PageKey =
  | 'login'
  | 'hub'
  | 'home'
  | 'lobby'
  | 'decks'
  | 'presets'
  | 'card-detail'
  | 'character'
  | 'character-create'
  | 'character-detail'
  | 'inventory'
  | 'combat'
  | 'logs'
  | 'deck-editor'
  | 'preset-editor'
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
  presetList: '/presets',
  cardDetail: '/cards',
  inventory: '/inventory',
  deckEditor: '/decks/editor',
  presetEditor: '/presets/editor',
  sessionEntry: '/lobby',
  sessionLobbyPlayer: '/lobby/player',
  sessionLobbyGm: '/lobby/gm',
  combat: '/combat',
} as const

export const routePatterns = {
  characterDetail: '/characters/:id',
  cardDetail: '/cards/:id',
  deckEditor: '/decks/:id/editor',
  presetEditor: '/presets/:id/editor',
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
  presetList: () => routePaths.presetList,
  cardDetail: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.cardDetail, { id })
      : routePaths.cardDetail,
  inventory: () => routePaths.inventory,
  deckEditor: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.deckEditor, { id })
      : routePaths.deckEditor,
  presetEditor: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.presetEditor, { id })
      : routePaths.presetEditor,
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
    description: 'Central entry screen for the Duel Tower interface.',
  },
  {
    key: 'lobby',
    label: 'Lobby / Session',
    path: routePaths.sessionEntry,
    title: 'Lobby / Session',
    description: 'Session entry and lobby flow for player and GM access.',
  },
  {
    key: 'decks',
    label: 'Decks',
    path: routePaths.deckList,
    title: 'Decks',
    description: 'Deck archive and editing access.',
  },
  {
    key: 'character',
    label: 'Character',
    path: '/character',
    title: 'Character',
    description: 'Character roster and profile access.',
  },
  {
    key: 'inventory',
    label: 'Inventory',
    path: '/inventory',
    title: 'Inventory',
    description: 'Rules, statuses, and passive reference access.',
  },
  {
    key: 'combat',
    label: 'Combat',
    path: routePaths.combat,
    title: 'Combat',
    description: 'Battlefield state and command screen.',
  },
  {
    key: 'logs',
    label: 'Logs / Results',
    path: '/logs',
    title: 'Logs / Results',
    description: 'Battle logs and recent result records.',
  },
]

const pageMap = new Map(PAGES.map((page) => [page.path, page]))

export type AppNavItem = {
  key: Exclude<PageKey, 'login'>
  label: string
  path: string
  description: string
  enabled: boolean
}

export const APP_NAV_ITEMS = [
  {
    key: 'hub',
    label: 'Hub',
    path: routePaths.hub,
    description: 'Central overview and archive entry point',
    enabled: true,
  },
  {
    key: 'character',
    label: 'Characters',
    path: routePaths.characterList,
    description: 'Roster, records, and profile access',
    enabled: true,
  },
  {
    key: 'decks',
    label: 'Decks',
    path: routePaths.deckList,
    description: 'Deck archive and editing flow',
    enabled: true,
  },
  {
    key: 'presets',
    label: 'Presets',
    path: routePaths.presetList,
    description: 'Preset archive and loadout editing flow',
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
    description: 'Entry, player lobby, and GM lobby',
    enabled: true,
  },
] satisfies AppNavItem[]

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
      description: 'Central hub for overview, status, and movement into the main archive areas.',
      eyebrow: 'Grand Archive',
      tags: [
        { label: 'Overview', tone: 'accent' },
        { label: 'Archive', tone: 'success' },
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
      description: 'Browse the roster and move into detailed character records from the current selection.',
      eyebrow: 'Roster Ledger',
      tags: [{ label: 'Roster', tone: 'accent' }],
    },
  ],
  [
    routePaths.deckList,
    {
      key: 'decks',
      label: 'Decks',
      path: routePaths.deckList,
      area: 'app',
      title: 'Deck Archive',
      description: 'Browse live deck records and move into the URL-based editor flow from the current selection.',
      eyebrow: 'Deck Ledger',
      tags: [{ label: 'Deck Archive', tone: 'accent' }],
    },
  ],
  [
    routePaths.presetList,
    {
      key: 'presets',
      label: 'Presets',
      path: routePaths.presetList,
      area: 'app',
      title: 'Preset Archive',
      description: 'Browse live preset records and move into the URL-based editor flow from the current selection.',
      eyebrow: 'Preset Ledger',
      tags: [{ label: 'Loadouts', tone: 'accent' }],
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
      tags: [{ label: 'Card Archive', tone: 'accent' }],
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
      tags: [{ label: 'Reference', tone: 'accent' }],
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
      description: 'Create or join a session, then move into the player or GM lobby flow.',
      eyebrow: 'Session Gate',
      tags: [{ label: 'Session Flow', tone: 'accent' }],
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
      description: 'Review the selected adventurer record and update the current detail view.',
      eyebrow: 'Record Detail',
      tags: [{ label: 'Character Record', tone: 'accent' }],
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
      description: 'Create a new adventurer record and return to the roster flow.',
      eyebrow: 'Record Creation',
      tags: [{ label: 'Character Record', tone: 'accent' }],
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
      description: 'Review and update the selected deck structure from the archive.',
      eyebrow: 'Tactical Editor',
      tags: [{ label: 'Deck Editor', tone: 'accent' }],
    },
  ],
  [
    routePaths.presetEditor,
    {
      key: 'preset-editor',
      label: 'Preset Editor',
      path: routePaths.presetEditor,
      area: 'app',
      title: 'Preset Editor',
      description: 'Review and update the selected preset entry from the archive.',
      eyebrow: 'Loadout Editor',
      tags: [{ label: 'Preset Editor', tone: 'accent' }],
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
      tags: [{ label: 'Player View', tone: 'accent' }],
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
      tags: [{ label: 'GM View', tone: 'accent' }],
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
      tags: [{ label: 'Battle View', tone: 'accent' }],
    },
  ],
])

type DynamicRouteSourceEntry = {
  pattern: string
  page: PageDefinition | undefined
}

type DynamicRouteEntry = {
  pattern: string
  page: PageDefinition
}

function hasDynamicRoutePage(entry: DynamicRouteSourceEntry): entry is DynamicRouteEntry {
  return entry.page !== undefined
}

function createDynamicRouteEntries(entries: readonly DynamicRouteSourceEntry[]) {
  return entries.filter(hasDynamicRoutePage)
}

const dynamicRouteEntries = createDynamicRouteEntries([
  { pattern: routePatterns.characterDetail, page: activePageMap.get(routePaths.characterDetail) },
  { pattern: routePatterns.cardDetail, page: activePageMap.get(routePaths.cardDetail) },
  { pattern: routePatterns.deckEditor, page: activePageMap.get(routePaths.deckEditor) },
  { pattern: routePatterns.presetEditor, page: activePageMap.get(routePaths.presetEditor) },
  { pattern: routePatterns.sessionLobbyPlayer, page: activePageMap.get(routePaths.sessionLobbyPlayer) },
  { pattern: routePatterns.sessionLobbyGm, page: activePageMap.get(routePaths.sessionLobbyGm) },
  { pattern: routePatterns.combat, page: activePageMap.get(routePaths.combat) },
])

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
      const page: PageDefinition = {
        ...entry.page,
        path: normalized,
      }

      return {
        page,
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
