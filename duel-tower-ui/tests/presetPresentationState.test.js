import assert from 'node:assert/strict'
import {
  buildPresetEditorLocalSummary,
  createPresetEditorLocalPresentation,
  getPresetEditorLocalTitle,
  isPresetEditorLocalDirty,
  isPresetEditorPreviewLocallyStale,
} from '../src/lib/presets/presentationState.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

const baseState = {
  name: 'Preset Alpha',
  characterId: 101,
  deckCardIds: ['C001', 'C002'],
  exCardId: 'EX001',
  passiveIds: ['P001'],
}

const baseDraft = {
  name: 'Preset Alpha',
  characterId: 101,
  deckCardIds: ['C001', 'C002'],
  exCardId: 'EX001',
  passiveIds: ['P001'],
}

const baseResolved = {
  characterLabel: 'Alice #101',
  characterSubtitle: 'Starter character',
  characterTags: [{ label: 'Starter', tone: 'accent' }],
  exLabel: 'Meteor EX',
  exSubtitle: 'Costs 2 mana',
  exTags: [{ label: 'Burst', tone: 'warning' }],
  deckItems: [
    { id: 'C001', label: 'Strike', subtitle: 'Attack', meta: 'Entry 1', tags: [{ label: 'Attack', tone: 'accent' }] },
    { id: 'C002', label: 'Guard', subtitle: 'Skill', meta: 'Entry 2', tags: [{ label: 'Skill', tone: 'muted' }] },
  ],
  passiveItems: [
    { id: 'P001', label: 'Quick Step', subtitle: 'Passive', meta: 'Entry 1', tags: [{ label: 'Passive', tone: 'success' }] },
  ],
}

runTest('name 변경 직후 local title이 입력 상태를 따른다', () => {
  assert.equal(
    getPresetEditorLocalTitle(
      { ...baseState, name: '  Local Draft Name  ' },
      baseDraft,
      'edit',
    ),
    'Local Draft Name',
  )
})

runTest('summary가 로컬 character/deck/passive/ex 입력을 반영한다', () => {
  assert.equal(
    buildPresetEditorLocalSummary({
      ...baseState,
      characterId: 202,
      deckCardIds: ['C001', 'C002', 'C003'],
      passiveIds: ['P001', 'P002'],
      exCardId: 'EX999',
    }),
    'Character 202, 3 deck cards, 2 passives, EX EX999.',
  )
})

runTest('deck 목록 변경 직후 local preview가 새 id를 즉시 반영한다', () => {
  const presentation = createPresetEditorLocalPresentation(
    { ...baseState, deckCardIds: ['C002', 'C999'] },
    baseDraft,
    baseResolved,
    'edit',
    true,
  )

  assert.equal(presentation.previewNeedsResolveRefresh, true)
  assert.equal(presentation.deckItems[0].title, 'Guard')
  assert.equal(presentation.deckItems[1].title, 'C999')
  assert.equal(presentation.deckItems[1].note, '저장 후 갱신됩니다.')
})

runTest('name 변경 직후 local dirty=true', () => {
  assert.equal(
    isPresetEditorLocalDirty(
      { ...baseState, name: 'Preset Beta' },
      baseDraft,
    ),
    true,
  )
})

runTest('character 또는 ex 변경 직후 reference preview가 local draft 기준으로 바뀐다', () => {
  const presentation = createPresetEditorLocalPresentation(
    { ...baseState, characterId: 303, exCardId: 'EX777' },
    baseDraft,
    baseResolved,
    'edit',
    true,
  )

  assert.equal(presentation.character.label, 'Character 303')
  assert.equal(presentation.ex.label, 'EX EX777')
  assert.equal(presentation.previewNeedsResolveRefresh, true)
})

runTest('server draft와 같으면 local preview stale=false', () => {
  assert.equal(isPresetEditorPreviewLocallyStale(baseState, baseDraft), false)
})

runTest('save 이후 screen draft가 local draft와 다시 맞으면 dirty=false로 돌아온다', () => {
  const savedDraft = {
    ...baseDraft,
    name: 'Preset Beta',
    characterId: 303,
    deckCardIds: ['C002', 'C999'],
    exCardId: 'EX777',
    passiveIds: ['P009'],
  }

  const resyncedState = {
    name: 'Preset Beta',
    characterId: 303,
    deckCardIds: ['C002', 'C999'],
    exCardId: 'EX777',
    passiveIds: ['P009'],
  }

  assert.equal(isPresetEditorLocalDirty(resyncedState, savedDraft), false)
  assert.equal(isPresetEditorPreviewLocallyStale(resyncedState, savedDraft), false)
})

runTest('clone 이후 새 screen draft로 재동기화되면 local summary도 새 snapshot을 따른다', () => {
  const clonedDraft = {
    ...baseDraft,
    name: 'Preset Clone',
    characterId: 404,
    deckCardIds: ['C004'],
    exCardId: 'EX404',
    passiveIds: ['P404', 'P405'],
  }

  const resyncedState = {
    name: 'Preset Clone',
    characterId: 404,
    deckCardIds: ['C004'],
    exCardId: 'EX404',
    passiveIds: ['P404', 'P405'],
  }

  assert.equal(buildPresetEditorLocalSummary(resyncedState), 'Character 404, 1 deck cards, 2 passives, EX EX404.')
})
