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
  assert.match(characterDetailSource, /disabled=\{applySavedDeckBlocked\}/)
  assert.match(characterDetailSource, /Save this character before applying a saved deck\./)
  assert.match(characterDetailSource, /setSelectionHandoff\(selectionHandoffKeys\.deckApplyCharacterId, requestedCharacterId\)/)
  assert.match(characterDetailSource, /navigateTo\(pathBuilders\.deckList\(\)\)/)
})

runTest('Character detail blocks saved deck apply when the loaded profile form is dirty', () => {
  assert.match(characterDetailSource, /function isSameFormState\(left: CharacterFormState, right: CharacterFormState\)/)
  assert.match(
    characterDetailSource,
    /const formDirty = \$derived\.by\(\(\) =>\s*character === null \? false : !isSameFormState\(form, createFormStateFromResponse\(character\)\),\s*\)/,
  )
  assert.match(
    characterDetailSource,
    /const applySavedDeckBlocked = \$derived\.by\(\(\) =>\s*isCreateMode \|\| !requestedCharacterId \|\| saving \|\| deleting \|\| loading \|\| formDirty,\s*\)/,
  )
  assert.match(characterDetailSource, /if \(formDirty\) \{[\s\S]*Save or discard your profile changes before applying a saved deck\./)
  assert.match(characterDetailSource, /Save or discard profile changes before applying a saved deck\./)
})

runTest('Character detail renders applied deck from server-loaded character state', () => {
  assert.match(characterDetailSource, /const currentDeck = \$derived\.by\(\(\) => character\?\.currentSkillDeck \?\? \[\]\)/)
  assert.doesNotMatch(characterDetailSource, /currentSkillDeck:/)
  assert.doesNotMatch(characterDetailSource, /currentSkillDeckText/)
  assert.doesNotMatch(characterDetailSource, /\.join\(['"`]\\n['"`]\)/)
  assert.doesNotMatch(characterDetailSource, /\.split\(\s*\/\\r\?\\n/)
})

runTest('Deck list exposes Apply deck to character only inside character apply context', () => {
  assert.match(deckListSource, /deckApplyCharacterId = readSelectionHandoff\(selectionHandoffKeys\.deckApplyCharacterId\)/)
  assert.match(deckListSource, /const inCharacterApplyFlow = \$derived\.by\(\(\) => Boolean\(deckApplyCharacterId\)\)/)
  assert.match(deckListSource, /\{#if inCharacterApplyFlow\}[\s\S]*Apply deck to character[\s\S]*\{\/if\}/)
  assert.match(deckListSource, /onSelect=\{inCharacterApplyFlow \? selectDeck : openDeckEditor\}/)
  assert.match(deckListSource, /Cancel deck application/)
})

runTest('Saved deck application wording avoids link or assignment language', () => {
  const combinedFlowSource = [characterDetailSource, deckListSource].join('\n')

  assert.match(characterDetailSource, /Deck Applied/)
  assert.match(characterDetailSource, /No Applied Deck/)
  assert.match(characterDetailSource, /No saved deck has been applied yet\./)
  assert.doesNotMatch(combinedFlowSource, /Deck Linked|No Deck|No linked deck|Assign to deck|deck assignment|No deck has been assigned/i)
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
