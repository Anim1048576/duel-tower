export type CombatPlayTargetType =
  | 'NONE'
  | 'SELF'
  | 'ALLY_ONE'
  | 'ALLY_ALL'
  | 'ALLY_SIDE'
  | 'ENEMY_ONE'
  | 'ENEMY_ALL'
  | 'ENEMY_SIDE'
  | 'ANY_ONE'
  | string

export type CombatPlayTargetSpec = {
  target: CombatPlayTargetType
  requiredSelection: boolean
}

export type CombatChoiceOption = {
  id: string
  label: string
  description: string | null
}

export type CombatExtraPlayRequirement =
  | {
      type: 'discard_from_hand'
      count: number
      excludeSourceCard: boolean
      filter: string
    }
  | {
      type: 'select_field_cards'
      minSelections: number
      maxSelections: number
      scope: string
      filter: string
      excludeSourceCard: boolean
    }
  | {
      type: 'choice'
      id: string
      label: string
      minSelections: number
      maxSelections: number
      options: CombatChoiceOption[]
    }

export type CombatResolvedPlaySpec = {
  target: CombatPlayTargetSpec
  extraRequirements: CombatExtraPlayRequirement[]
}

export function asRecord(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }

  return value as Record<string, unknown>
}

export function readString(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

export function readBoolean(value: unknown, fallback = false) {
  return typeof value === 'boolean' ? value : fallback
}

export function readInteger(value: unknown, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : fallback
}

export function normalizePlaySpec(playSpec: unknown): CombatResolvedPlaySpec {
  const playSpecRecord = asRecord(playSpec)
  const targetRecord = asRecord(playSpecRecord?.target)
  const target = readString(targetRecord?.target) || 'NONE'
  const requiredSelection = readBoolean(targetRecord?.requiredSelection)
  const extraRequirementValues = Array.isArray(playSpecRecord?.extraRequirements)
    ? playSpecRecord.extraRequirements
    : []

  const extraRequirements = extraRequirementValues
    .map((value) => {
      const record = asRecord(value)
      const type = readString(record?.type)

      if (type === 'discard_from_hand') {
        return {
          type,
          count: readInteger(record?.count, 1),
          excludeSourceCard: readBoolean(record?.excludeSourceCard, true),
          filter: readString(record?.filter) || 'ANY',
        } satisfies CombatExtraPlayRequirement
      }

      if (type === 'select_field_cards') {
        return {
          type,
          minSelections: readInteger(record?.minSelections),
          maxSelections: readInteger(record?.maxSelections),
          scope: readString(record?.scope) || 'ALL_PLAYER_FIELDS',
          filter: readString(record?.filter) || 'INSTALLED_ONLY',
          excludeSourceCard: readBoolean(record?.excludeSourceCard),
        } satisfies CombatExtraPlayRequirement
      }

      if (type === 'choice') {
        const options = Array.isArray(record?.options)
          ? record.options
              .map((optionValue) => {
                const optionRecord = asRecord(optionValue)
                const id = readString(optionRecord?.id)

                if (!id) {
                  return null
                }

                return {
                  id,
                  label: readString(optionRecord?.label) || id,
                  description: readString(optionRecord?.description) || null,
                } satisfies CombatChoiceOption
              })
              .filter((option): option is CombatChoiceOption => option !== null)
          : []

        return {
          type,
          id: readString(record?.id) || 'choice',
          label: readString(record?.label) || 'Choice input',
          minSelections: readInteger(record?.minSelections),
          maxSelections: readInteger(record?.maxSelections),
          options,
        } satisfies CombatExtraPlayRequirement
      }

      return null
    })
    .filter((requirement): requirement is CombatExtraPlayRequirement => requirement !== null)

  return {
    target: {
      target,
      requiredSelection,
    },
    extraRequirements,
  } satisfies CombatResolvedPlaySpec
}
