import { listCharacters } from '../../../../lib/api/characters'
import type { CharacterProfileResponse } from '../../../../lib/api/characterTypes'
import { listCards, listPassives } from '../../../../lib/api/content'
import type { CardDefinition, PassiveDefinition } from '../../../../lib/api/contentTypes'
import { listPresets } from '../../../../lib/api/presets'
import type { PresetResponse } from '../../../../lib/api/presetTypes'
import { getApiErrorMessage } from '../../../../lib/api/types'

export type LobbyReferenceCatalogResult = {
  characters: CharacterProfileResponse[]
  cards: CardDefinition[]
  passives: PassiveDefinition[]
  errorMessage: string | null
}

export type LobbyPresetCatalogResult = {
  presets: PresetResponse[]
  selectedPresetId: string
  errorMessage: string | null
}

export async function loadLobbyReferenceCatalogs({
  unavailableMessage,
}: {
  unavailableMessage: (errors: string[]) => string
}): Promise<LobbyReferenceCatalogResult> {
  const [characterResult, cardResult, passiveResult] = await Promise.allSettled([
    listCharacters(),
    listCards(),
    listPassives(),
  ])

  const errors: string[] = []

  const characters =
    characterResult.status === 'fulfilled'
      ? characterResult.value
      : (errors.push('character roster'), [])

  const cards =
    cardResult.status === 'fulfilled'
      ? cardResult.value
      : (errors.push('card archive'), [])

  const passives =
    passiveResult.status === 'fulfilled'
      ? passiveResult.value
      : (errors.push('passive archive'), [])

  return {
    characters,
    cards,
    passives,
    errorMessage: errors.length > 0 ? unavailableMessage(errors) : null,
  }
}

export async function loadLobbyPresetCatalog({
  currentSelectedPresetId,
  unavailableMessage,
}: {
  currentSelectedPresetId: string
  unavailableMessage: string
}): Promise<LobbyPresetCatalogResult> {
  try {
    const presets = await listPresets()
    const selectedPresetId =
      presets.find((entry) => String(entry.id) === currentSelectedPresetId)
        ? currentSelectedPresetId
        : presets[0]
          ? String(presets[0].id)
          : ''

    return {
      presets,
      selectedPresetId,
      errorMessage: null,
    }
  } catch (error) {
    return {
      presets: [],
      selectedPresetId: '',
      errorMessage: getApiErrorMessage(error, unavailableMessage),
    }
  }
}
