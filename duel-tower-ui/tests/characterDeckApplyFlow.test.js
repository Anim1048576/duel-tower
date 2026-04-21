import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const characterDetailSource = readSource('../src/pages/CharacterDetailPage.svelte')
const deckListSource = readSource('../src/pages/DeckListPage.svelte')
const characterApiSource = readSource('../src/lib/api/characters.ts')

function readSource(relativePath) {
  return readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')
}

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

runTest('Character detail exposes Apply saved deck only for saved character flow', () => {
  assert.match(characterDetailSource, />\s*Apply saved deck\s*</)
  assert.match(
    characterDetailSource,
    /disabled=\{isCreateMode \|\| saving \|\| deleting \|\| loading \|\| !requestedCharacterId\}/,
  )
  assert.match(characterDetailSource, /Save this character before applying a saved deck\./)
  assert.match(characterDetailSource, /setSelectionHandoff\(selectionHandoffKeys\.deckApplyCharacterId, requestedCharacterId\)/)
  assert.match(characterDetailSource, /navigateTo\(pathBuilders\.deckList\(\)\)/)
})

runTest('Character detail renders applied deck from server-loaded character state', () => {
  assert.match(characterDetailSource, /const currentDeck = \$derived\.by\(\(\) => character\?\.currentSkillDeck \?\? \[\]\)/)
  assert.match(characterDetailSource, /currentSkillDeck: isCreateMode \? null : \(character\?\.currentSkillDeck \?\? null\)/)
  assert.doesNotMatch(characterDetailSource, /currentSkillDeckText/)
  assert.doesNotMatch(characterDetailSource, /\.join\(['"`]\\n['"`]\)/)
  assert.doesNotMatch(characterDetailSource, /\.split\(\s*\/\\r\?\\n/)
})

runTest('Deck list exposes Apply to character only inside character apply context', () => {
  assert.match(deckListSource, /deckApplyCharacterId = readSelectionHandoff\(selectionHandoffKeys\.deckApplyCharacterId\)/)
  assert.match(deckListSource, /const inCharacterApplyFlow = \$derived\.by\(\(\) => Boolean\(deckApplyCharacterId\)\)/)
  assert.match(deckListSource, /\{#if inCharacterApplyFlow\}[\s\S]*Apply to character[\s\S]*\{\/if\}/)
  assert.match(deckListSource, /onSelect=\{inCharacterApplyFlow \? selectDeck : openDeckEditor\}/)
  assert.match(deckListSource, /Cancel deck apply/)
})

runTest('Deck apply success calls API, consumes context, and returns to character detail', () => {
  assert.match(deckListSource, /await applySavedDeckToCharacter\(deckApplyCharacterId, getDeckId\(selectedDeck\)\)/)
  assert.match(deckListSource, /removeSelectionHandoff\(selectionHandoffKeys\.deckApplyCharacterId\)/)
  assert.match(deckListSource, /navigateTo\(pathBuilders\.characterDetail\(String\(response\.id\)\)/)
  assert.match(deckListSource, /Character detail was refreshed from the server\./)
})

runTest('Deck apply failure stays on deck list and displays API error', () => {
  assert.match(deckListSource, /applyErrorMessage = getApiErrorMessage\(error, 'Unable to apply the selected deck to this character\.'\)/)
  assert.match(deckListSource, /title="Unable to apply deck"/)
  assert.match(deckListSource, /tone="error"/)
})

runTest('Character API client uses only the backend apply endpoint for deck application', () => {
  assert.match(characterApiSource, /export function applySavedDeckToCharacter/)
  assert.match(characterApiSource, /current-skill-deck\/from-deck/)
  assert.match(characterApiSource, /apiPost<CharacterProfileResponse, null>/)
})

runTest('Frontend deck apply flow does not expand counts or validate deck rules', () => {
  const combinedFlowSource = [characterDetailSource, deckListSource, characterApiSource].join('\n')

  assert.doesNotMatch(combinedFlowSource, /for\s*\([^)]*count[^)]*\)/)
  assert.doesNotMatch(combinedFlowSource, /Array\.from\([^)]*count/)
  assert.doesNotMatch(combinedFlowSource, /\.flatMap\(/)
  assert.doesNotMatch(combinedFlowSource, /currentSkillDeckText/)
  assert.doesNotMatch(combinedFlowSource, /maxDeck|maxCopies|validatePlayer|owned card unavailable|EX_NOT_ALLOWED|TOKEN_NOT_ALLOWED/i)
})
