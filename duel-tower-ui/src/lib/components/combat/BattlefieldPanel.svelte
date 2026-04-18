<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import CombatEntityCard from './CombatEntityCard.svelte'
  import type {
    CombatInspectorEntityReference,
    CombatEnemyViewModel,
    CombatPlayerViewModel,
    CombatSummonViewModel,
    CombatTag,
  } from './types'

  type Props = {
    playerViews: readonly CombatPlayerViewModel[]
    enemyViews: readonly CombatEnemyViewModel[]
    summonViews: readonly CombatSummonViewModel[]
    currentTurnPlayerId: string | null
    currentEnemyId: string | null
    visiblePlayerId: string | null
    selectedPlayerId: string | null
    selectedTargets: readonly {
      playerId?: string | null
      enemyId?: string | null
      summonOwnerPlayerId?: string | null
      summonInstanceId?: string | null
    }[]
    onSelectPlayer: (playerId: string) => void
    onToggleTargetPlayer: (playerId: string) => void
    onToggleTargetEnemy: (enemyId: string) => void
    onToggleTargetSummon: (owner: string, summonId: string) => void
    onHoverEntity: (entity: CombatInspectorEntityReference | null) => void
    onPinEntity: (entity: CombatInspectorEntityReference | null) => void
    resolveInspectState: (entity: CombatInspectorEntityReference) => 'idle' | 'hovered' | 'pinned'
  }

  let {
    playerViews,
    enemyViews,
    summonViews,
    currentTurnPlayerId,
    currentEnemyId,
    visiblePlayerId,
    selectedPlayerId,
    selectedTargets,
    onSelectPlayer,
    onToggleTargetPlayer,
    onToggleTargetEnemy,
    onToggleTargetSummon,
    onHoverEntity,
    onPinEntity,
    resolveInspectState,
  }: Props = $props()

  function playerHeaderTags(player: CombatPlayerViewModel) {
    const tags: CombatTag[] = []

    if (currentTurnPlayerId === player.playerId) {
      tags.push({ label: 'Current turn', tone: 'success' })
    }

    if (visiblePlayerId === player.playerId) {
      tags.push({ label: 'Visible hand', tone: 'accent' })
    }

    tags.push({ label: player.stateLabel, tone: player.stateTone })
    return tags
  }

  function enemyHeaderTags(enemy: CombatEnemyViewModel) {
    const tags: CombatTag[] = []

    if (currentEnemyId === enemy.enemyId) {
      tags.push({ label: 'Current turn', tone: 'warning' })
    }

    tags.push({ label: enemy.stateLabel, tone: enemy.stateTone })
    return tags
  }

  function summonHeaderTags(summon: CombatSummonViewModel) {
    return [{ label: summon.stateLabel, tone: summon.stateTone }] satisfies CombatTag[]
  }

  function pinEntity(entity: CombatInspectorEntityReference) {
    onPinEntity(entity)
  }
</script>

<div class="battlefield-panel">
  <div class="battlefield-panel__side battlefield-panel__side--players">
    <SectionFrame
      title="Player side"
      description="Player cards highlight live zones, EX state, passives, and pending decisions. Player HP/AP is not exposed in the current player session payload."
    >
      {#if playerViews.length > 0}
        <div class="battlefield-panel__unit-list battlefield-panel__unit-list--players">
          {#each playerViews as player, index}
            <div class:timeline-offset={index % 2 === 1}>
              <CombatEntityCard
                displayMode="compact"
                title={player.playerId}
                subtitle={`${player.ready ? 'Ready participant' : 'Joined participant'} | Hand and zone state`}
                metrics={player.metrics}
                summaryLines={player.summaryLines}
                tagRows={[
                  playerHeaderTags(player),
                  player.statusTags,
                  player.passives.length > 0
                    ? player.passives.map((passiveId) => ({ label: passiveId, tone: 'accent' as const }))
                    : [{ label: 'No passives', tone: 'muted' as const }],
                ]}
                activeTurn={currentTurnPlayerId === player.playerId}
                inspectState={resolveInspectState({ kind: 'player', id: player.playerId })}
                onInspectHoverStart={() => onHoverEntity({ kind: 'player', id: player.playerId })}
                onInspectHoverEnd={() => onHoverEntity(null)}
                onInspectPin={() => pinEntity({ kind: 'player', id: player.playerId })}
                actionButtons={[
                  {
                    label: selectedPlayerId === player.playerId ? 'Selected actor' : 'Select actor',
                    selected: selectedPlayerId === player.playerId,
                    onClick: () => onSelectPlayer(player.playerId),
                  },
                  {
                    label: selectedTargets.some((target) => target.playerId === player.playerId)
                      ? 'Targeted player'
                      : 'Target player',
                    selected: selectedTargets.some((target) => target.playerId === player.playerId),
                    onClick: () => onToggleTargetPlayer(player.playerId),
                  },
                ]}
              />
            </div>
          {/each}
        </div>
      {:else}
        <ContentStatePanel
          title="No player roster yet"
          message="No player state is available for this session yet."
        />
      {/if}
    </SectionFrame>
  </div>

  <div class="battlefield-panel__side battlefield-panel__side--enemies">
    <SectionFrame
      title="Enemy side"
      description="Enemy cards surface combat HP/AP and status pressure first, with summons grouped below the main enemy roster."
    >
      {#if enemyViews.length > 0}
        <div class="battlefield-panel__unit-list battlefield-panel__unit-list--enemies">
          {#each enemyViews as enemy, index}
            <div class:timeline-offset={index % 2 === 1}>
              <CombatEntityCard
                displayMode="compact"
                title={enemy.enemyId}
                subtitle="Combat enemy | Live battlefield unit"
                metrics={enemy.metrics}
                summaryLines={enemy.summaryLines}
                tagRows={[
                  enemyHeaderTags(enemy),
                  enemy.statusEntries.length > 0
                    ? enemy.statusEntries.map((status) => ({ label: status, tone: 'warning' as const }))
                    : [{ label: 'No statuses', tone: 'muted' as const }],
                ]}
                activeTurn={currentEnemyId === enemy.enemyId}
                variant="enemy"
                inspectState={resolveInspectState({ kind: 'enemy', id: enemy.enemyId })}
                onInspectHoverStart={() => onHoverEntity({ kind: 'enemy', id: enemy.enemyId })}
                onInspectHoverEnd={() => onHoverEntity(null)}
                onInspectPin={() => pinEntity({ kind: 'enemy', id: enemy.enemyId })}
                actionButtons={[
                  {
                    label: selectedTargets.some((target) => target.enemyId === enemy.enemyId)
                      ? 'Targeted enemy'
                      : 'Target enemy',
                    selected: selectedTargets.some((target) => target.enemyId === enemy.enemyId),
                    onClick: () => onToggleTargetEnemy(enemy.enemyId),
                  },
                ]}
              />
            </div>
          {/each}
        </div>
      {:else}
        <ContentStatePanel
          title="Enemy state not active yet"
          message="Combat enemies are not present in the current session state yet."
        />
      {/if}

      {#if summonViews.length > 0}
        <div class="battlefield-panel__summon-section">
          <strong>Summons</strong>
          <div class="battlefield-panel__unit-list battlefield-panel__unit-list--summons">
            {#each summonViews as summon}
              <CombatEntityCard
                displayMode="compact"
                title={summon.summonId}
                subtitle={`${summon.owner} | Support unit`}
                metrics={summon.metrics}
                summaryLines={summon.summaryLines}
                tagRows={[summonHeaderTags(summon)]}
                compactMetrics={true}
                inspectState={resolveInspectState({ kind: 'summon', id: summon.summonId, owner: summon.owner })}
                onInspectHoverStart={() => onHoverEntity({ kind: 'summon', id: summon.summonId, owner: summon.owner })}
                onInspectHoverEnd={() => onHoverEntity(null)}
                onInspectPin={() => pinEntity({ kind: 'summon', id: summon.summonId, owner: summon.owner })}
                actionButtons={[
                  {
                    label: selectedTargets.some(
                      (target) =>
                        target.summonOwnerPlayerId === summon.owner &&
                        target.summonInstanceId === summon.summonId,
                    )
                      ? 'Targeted summon'
                      : 'Target summon',
                    selected: selectedTargets.some(
                      (target) =>
                        target.summonOwnerPlayerId === summon.owner &&
                        target.summonInstanceId === summon.summonId,
                    ),
                    onClick: () => onToggleTargetSummon(summon.owner, summon.summonId),
                  },
                ]}
              />
            {/each}
          </div>
        </div>
      {/if}
    </SectionFrame>
  </div>
</div>

<style>
  .battlefield-panel,
  .battlefield-panel__side,
  .battlefield-panel__unit-list,
  .battlefield-panel__summon-section,
  .timeline-offset {
    display: grid;
    gap: 0.75rem;
  }

  .battlefield-panel {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: center;
    gap: clamp(0.85rem, 3vw, 2rem);
    min-height: 28rem;
  }

  .battlefield-panel__side--players {
    justify-self: start;
    width: min(100%, 31rem);
  }

  .battlefield-panel__side--enemies {
    justify-self: end;
    width: min(100%, 32rem);
  }

  .battlefield-panel__unit-list--players,
  .battlefield-panel__unit-list--enemies {
    gap: 0.75rem;
  }

  .battlefield-panel__unit-list--enemies {
    text-align: right;
  }

  .battlefield-panel__unit-list--players .timeline-offset {
    margin-left: clamp(0rem, 2vw, 1rem);
  }

  .battlefield-panel__unit-list--enemies .timeline-offset {
    margin-right: clamp(0rem, 2vw, 1rem);
  }

  .battlefield-panel__summon-section > strong {
    color: var(--combat-tertiary, var(--color-success));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    border-bottom: 1px solid rgba(188, 204, 173, 0.22);
    padding-bottom: 0.4rem;
  }

  @media (max-width: 1080px) {
    .battlefield-panel {
      grid-template-columns: 1fr;
      min-height: auto;
    }

    .battlefield-panel__side--players,
    .battlefield-panel__side--enemies {
      justify-self: stretch;
      width: 100%;
    }

    .battlefield-panel__unit-list--enemies {
      text-align: left;
    }

    .battlefield-panel__unit-list--players .timeline-offset,
    .battlefield-panel__unit-list--enemies .timeline-offset {
      margin-left: 0;
      margin-right: 0;
    }
  }
</style>
