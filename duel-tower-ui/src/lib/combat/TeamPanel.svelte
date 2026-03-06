<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { TeamPlayer, TeamSide, CombatTarget, TeamSummon } from './types'

  const dispatch = createEventDispatcher<{ selectTarget: CombatTarget }>()

  export let side: TeamSide = 'ally'
  export let title = ''
  export let players: TeamPlayer[] = []
  export let summons: TeamSummon[] = []
  export let targetable = false
  export let selectedTarget: string | null = null

  $: heading = title || (side === 'ally' ? '아군' : '적 진영')

  function targetKey(target: CombatTarget) {
    return target.type === 'player' ? `player:${target.playerId}` : `summon:${target.playerId}:${target.summonId}`
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
        <button
          class="unit"
          class:isTargetable={targetable}
          class:isSelected={selectedTarget === targetKey({ type: 'player', playerId: p.playerId })}
          disabled={!targetable}
          on:click={() => dispatch('selectTarget', { type: 'player', playerId: p.playerId })}
        >
          <div class="rowLine">
            <b class="mono">{p.playerId}</b>
            {#if p.pendingDecision}
              <span class="badge no">결정 필요</span>
            {/if}
          </div>
          <div class="hint">핸드 {p.hand.length} · 필드 {p.field.length} · 묘지 {p.grave.length}</div>
        </button>
      {/each}
    </div>
  {/if}

  {#if summons.length}
    <div class="spacer"></div>
    <div class="hint">소환수</div>
    <div class="stack">
      {#each summons as s (`${s.owner}:${s.summonId}`)}
        <button
          class="unit"
          class:isTargetable={targetable}
          class:isSelected={selectedTarget === targetKey({ type: 'summon', playerId: s.owner, summonId: s.summonId })}
          disabled={!targetable}
          on:click={() => dispatch('selectTarget', { type: 'summon', playerId: s.owner, summonId: s.summonId })}
        >
          <div class="rowLine">
            <b>{s.summonId}</b>
            <span class="badge">{s.owner}</span>
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
  .unit{width:100%; text-align:left; padding:10px; border:1px solid var(--line); border-radius:14px; background: rgba(0,0,0,.12); color:inherit}
  .unit:disabled{opacity:.9}
  .unit.isTargetable{cursor:pointer}
  .unit.isTargetable:hover{border-color: rgba(93,214,255,.35)}
  .unit.isSelected{outline:2px solid rgba(93,214,255,.55)}
  .rowLine{display:flex; justify-content:space-between; align-items:center; gap:8px}
</style>
