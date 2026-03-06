<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CombatTarget } from '../model'
  import type { TeamPlayer, TeamSide, TeamSummon } from './types'
  import CharacterPanel from '../components/CharacterPanel.svelte'
  import StatusBadge from '../components/StatusBadge.svelte'

  const dispatch = createEventDispatcher<{ selectTarget: CombatTarget }>()

  export let side: TeamSide = 'ally'
  export let title = ''
  export let players: TeamPlayer[] = []
  export let summons: TeamSummon[] = []
  export let validTargets: CombatTarget[] = []
  export let selectedTarget: string | null = null

  $: heading = title || (side === 'ally' ? '아군' : '적 진영')
  $: validTargetKeys = new Set(validTargets.map((t) => targetKey(t)))

  function targetKey(target: CombatTarget) {
    return target.type === 'player' ? `player:${target.playerId}` : `summon:${target.playerId}:${target.summonId}`
  }

  function canTarget(target: CombatTarget) {
    return validTargetKeys.has(targetKey(target))
  }
</script>

<section class="panel zone">
  <div class="panelTitle">{heading}</div>
  <div class="spacer"></div>
  {#if !players.length}
    <div class="hint">표시할 플레이어 없음</div>
  {:else}
    <div class="stack">
      {#each players as p (p.playerId)}
        {@const target = { type: 'player', playerId: p.playerId } as CombatTarget}
        <button class="unit" class:isTargetable={canTarget(target)} class:isSelected={selectedTarget === targetKey(target)} disabled={!canTarget(target)} on:click={() => dispatch('selectTarget', target)}>
          <CharacterPanel player={p} />
        </button>
      {/each}
    </div>
  {/if}

  {#if summons.length}
    <div class="spacer"></div>
    <div class="hint">소환수</div>
    <div class="stack">
      {#each summons as s (`${s.owner}:${s.summonId}`)}
        {@const target = { type: 'summon', playerId: s.owner, summonId: s.summonId } as CombatTarget}
        <button class="unit" class:isTargetable={canTarget(target)} class:isSelected={selectedTarget === targetKey(target)} disabled={!canTarget(target)} on:click={() => dispatch('selectTarget', target)}>
          <div class="rowLine">
            <b>{s.summonId}</b>
            <StatusBadge label={s.owner} />
          </div>
          <div class="hint">HP {s.hp} · ATK {s.atk} · HEAL {s.heal}</div>
        </button>
      {/each}
    </div>
  {/if}
</section>

<style>
  .zone{min-height:300px}
  .stack{display:flex; flex-direction:column; gap:8px}
  .unit{width:100%; text-align:left; padding:0; border:1px solid var(--line); border-radius:14px; background: rgba(0,0,0,.12); color:inherit}
  .unit:disabled{opacity:.9}
  .unit.isTargetable{cursor:pointer}
  .unit.isTargetable:hover{border-color: rgba(93,214,255,.35)}
  .unit.isSelected{outline:2px solid rgba(93,214,255,.55)}
  .rowLine{display:flex; justify-content:space-between; align-items:center; gap:8px; padding:10px 10px 0}
</style>
