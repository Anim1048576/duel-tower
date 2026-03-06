<script lang="ts">
  export let open = false
  export let nodeName = ''
  export let onCancel: (() => void) | undefined = undefined
  export let onResolve: ((result: { success: boolean; gap: number; memoryAccepted: boolean; roll: number }) => void) | undefined = undefined

  let threshold = 11
  let roll = 12
  let memoryAccepted = false

  $: disabledByCritical = roll === 20
  $: gap = Math.abs(threshold - roll)
  $: success = roll >= threshold && !disabledByCritical

  function resolve() {
    if (disabledByCritical) return
    onResolve?.({ success, gap, memoryAccepted, roll })
  }
</script>

{#if open}
  <div class="backdrop" role="button" tabindex="0" on:click={(e) => e.target === e.currentTarget && onCancel?.()} on:keydown={(e) => e.key === 'Escape' && onCancel?.()}>
    <section class="panel" role="dialog" aria-modal="true">
      <div class="modalTitle">Judgement · {nodeName}</div>
      <div class="hint">성공/실패 격차와 기억 받아들이기 선택으로 결과를 확정한다.</div>

      <div class="grid2" style="margin-top:12px">
        <label class="kv">
          <div class="k">판정 기준값</div>
          <input class="input" type="number" min="1" max="20" bind:value={threshold} />
        </label>

        <label class="kv">
          <div class="k">주사위 결과 (d20)</div>
          <input class="input" type="number" min="1" max="20" bind:value={roll} />
        </label>
      </div>

      <label class="row" style="margin-top:10px">
        <input type="checkbox" bind:checked={memoryAccepted} />
        <span>기억 받아들이기</span>
      </label>

      <div class="ti" style="margin-top:10px">
        <div class="logHead">판정 요약</div>
        <div class="logBody">
          {#if disabledByCritical}
            주사위가 20이라 판정이 비활성 상태다. 수치 조정 후 재시도 가능.
          {:else}
            {success ? '성공' : '실패'} · 격차 {gap} ({roll} vs 기준 {threshold})
          {/if}
        </div>
      </div>

      <div class="actions">
        <button class="btn" on:click={() => onCancel?.()}>취소</button>
        <button class="btn primary" disabled={disabledByCritical} on:click={resolve}>결과 확정</button>
      </div>
    </section>
  </div>
{/if}

<style>
  .backdrop{position:fixed; inset:0; z-index:66; display:grid; place-items:center; background:rgba(0,0,0,.56)}
  .panel{width:min(640px, calc(100% - 24px)); border:1px solid var(--line-default); border-radius:16px; padding:14px; background:var(--surface-1)}
  .actions{display:flex; justify-content:flex-end; gap:8px; margin-top:14px}
</style>
