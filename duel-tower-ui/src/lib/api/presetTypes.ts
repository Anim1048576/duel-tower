export type PresetId = number

export type PresetIdentifier = PresetId | string

export type PresetOwner = string

export type PresetTimestampValue = string | number

export type PresetPayload = {
  name: string
  characterId: number
  deckCardIds: readonly string[]
  exCardId: string
  passiveIds: readonly string[]
}

export type CreatePresetRequest = PresetPayload

export type UpdatePresetRequest = PresetPayload

export type PresetResponse = {
  id: PresetId
  owner: PresetOwner
  name: string
  characterId: number
  deckCardIds: string[]
  exCardId: string
  passiveIds: string[]
  createdAt: PresetTimestampValue
  updatedAt: PresetTimestampValue
}
