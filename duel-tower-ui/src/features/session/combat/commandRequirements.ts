import type { TargetRefDto } from '../../../lib/api/sessionTypes'
import type { CombatCommandRequirementViewModel } from '../../../lib/components/combat/types'
import type {
  CombatExtraPlayRequirement,
  CombatPlayTargetSpec,
  CombatResolvedPlaySpec,
} from './playSpec'

export function describeTargetRequirement(targetSpec: CombatPlayTargetSpec) {
  if (!targetSpec.requiredSelection || targetSpec.target === 'NONE') {
    return 'No manual target required'
  }

  switch (targetSpec.target) {
    case 'ENEMY_ONE':
      return 'Select exactly one enemy or summon target'
    case 'ALLY_ONE':
      return 'Select exactly one ally player or summon target'
    case 'ANY_ONE':
      return 'Select exactly one target'
    case 'SELF':
      return 'Self-targeted automatically'
    case 'ENEMY_ALL':
    case 'ENEMY_SIDE':
      return 'Enemy-side target is resolved automatically'
    case 'ALLY_ALL':
    case 'ALLY_SIDE':
      return 'Ally-side target is resolved automatically'
    default:
      return `Target rule: ${targetSpec.target}`
  }
}

export function getPlaySpecRequirement<TType extends CombatExtraPlayRequirement['type']>(
  playSpec: CombatResolvedPlaySpec,
  type: TType,
): Extract<CombatExtraPlayRequirement, { type: TType }> | null {
  return (
    playSpec.extraRequirements.find(
      (requirement): requirement is Extract<CombatExtraPlayRequirement, { type: TType }> =>
        requirement.type === type,
    ) ?? null
  )
}

export function buildCommandRequirementViewModel(
  sourceLabel: string,
  playSpec: CombatResolvedPlaySpec,
): CombatCommandRequirementViewModel {
  const discardRequirement = getPlaySpecRequirement(playSpec, 'discard_from_hand')
  const fieldRequirement = getPlaySpecRequirement(playSpec, 'select_field_cards')
  const choiceRequirement = getPlaySpecRequirement(playSpec, 'choice')

  return {
    sourceLabel,
    targetSummary: describeTargetRequirement(playSpec.target),
    discardSummary:
      discardRequirement?.type === 'discard_from_hand'
        ? `Select ${discardRequirement.count} hand discard${discardRequirement.count > 1 ? 's' : ''}${discardRequirement.excludeSourceCard ? ' excluding the source card' : ''}`
        : 'No extra hand discard required',
    fieldSelectionSummary:
      fieldRequirement?.type === 'select_field_cards'
        ? `Select ${fieldRequirement.minSelections}-${fieldRequirement.maxSelections} field card ids`
        : 'No extra field selection required',
    choiceSummary:
      choiceRequirement?.type === 'choice'
        ? `${choiceRequirement.label} (${choiceRequirement.options.map((option) => option.label).join(', ') || 'choice options'})`
        : 'No explicit choice requirement',
  }
}

export function getTargetSelectionError(
  commandLabel: string,
  targetSpec: CombatPlayTargetSpec,
  selectedTargets: TargetRefDto[],
) {
  if (!targetSpec.requiredSelection || targetSpec.target === 'NONE') {
    return null
  }

  if (targetSpec.target === 'SELF') {
    return null
  }

  if (
    targetSpec.target === 'ALLY_ALL' ||
    targetSpec.target === 'ALLY_SIDE' ||
    targetSpec.target === 'ENEMY_ALL' ||
    targetSpec.target === 'ENEMY_SIDE'
  ) {
    return null
  }

  if (selectedTargets.length !== 1) {
    return `${commandLabel} requires exactly one target selection.`
  }

  const [target] = selectedTargets

  switch (targetSpec.target) {
    case 'ENEMY_ONE':
      return target.enemyId || target.summonInstanceId
        ? null
        : `${commandLabel} requires one enemy or summon target.`
    case 'ALLY_ONE':
      return target.playerId || target.summonInstanceId
        ? null
        : `${commandLabel} requires one ally player or summon target.`
    case 'ANY_ONE':
      return target.enemyId || target.playerId || target.summonInstanceId
        ? null
        : `${commandLabel} requires one target.`
    default:
      return selectedTargets.length > 0 ? null : `${commandLabel} requires a target selection.`
  }
}

export function getPlayCardRequirementError(
  commandLabel: string,
  playSpec: CombatResolvedPlaySpec | null,
  sourceCardId: string,
  selectedTargets: TargetRefDto[],
  selectedDiscardIds: string[],
  selectedFieldIds: string[],
) {
  if (!playSpec) {
    return null
  }

  const targetError = getTargetSelectionError(commandLabel, playSpec.target, selectedTargets)

  if (targetError) {
    return targetError
  }

  const discardRequirement = getPlaySpecRequirement(playSpec, 'discard_from_hand')

  if (discardRequirement?.type === 'discard_from_hand') {
    if (selectedDiscardIds.length !== discardRequirement.count) {
      return `${commandLabel} requires ${discardRequirement.count} hand discard selection${discardRequirement.count > 1 ? 's' : ''}.`
    }

    if (discardRequirement.excludeSourceCard && selectedDiscardIds.includes(sourceCardId)) {
      return 'The source card cannot be selected as an extra discard.'
    }
  }

  const fieldRequirement = getPlaySpecRequirement(playSpec, 'select_field_cards')

  if (fieldRequirement?.type === 'select_field_cards') {
    if (
      selectedFieldIds.length < fieldRequirement.minSelections ||
      selectedFieldIds.length > fieldRequirement.maxSelections
    ) {
      return `${commandLabel} requires ${fieldRequirement.minSelections}-${fieldRequirement.maxSelections} selected field ids.`
    }

    if (fieldRequirement.excludeSourceCard && selectedFieldIds.includes(sourceCardId)) {
      return 'The source card cannot be selected as a field helper id.'
    }
  }

  if (getPlaySpecRequirement(playSpec, 'choice')) {
    return `${commandLabel} has a choice-based follow-up that is not wired in this combat step yet.`
  }

  return null
}
