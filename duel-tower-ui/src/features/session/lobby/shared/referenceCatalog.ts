import { listCharacters } from '../../../../lib/api/characters'
import type { CharacterProfileResponse } from '../../../../lib/api/characterTypes'
import { listCards, listPassives } from '../../../../lib/api/content'
import type { CardDefinition, PassiveDefinition } from '../../../../lib/api/contentTypes'

export type LobbyReferenceCatalogResult = {
  characters: CharacterProfileResponse[]
  cards: CardDefinition[]
  passives: PassiveDefinition[]
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
