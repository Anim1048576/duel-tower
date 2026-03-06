<script lang="ts">
  import type { NodeChoice } from './NodeChoiceCard.svelte'

  export let open = false
  export let node: NodeChoice | null = null
  export let onCancel: (() => void) | undefined = undefined
  export let onConfirm: ((node: NodeChoice) => void) | undefined = undefined

  function closeOnBackdrop(event: MouseEvent) {
    if (event.target === event.currentTarget) onCancel?.()
  }

  function confirm() {
    if (!node) return
    onConfirm?.(node)
  }
</script>

{#if open && node}
  <div class="backdrop" role="button" tabindex="0" on:click={closeOnBackdrop} on:keydown={(e) => e.key === 'Escape' && onCancel?.()}>
    <section class="panel" role="dialog" aria-modal="true">
      <div class="modalTitle">노드 진입 확인</div>
      <div class="hint">{node.name} · {node.typeLabel}</div>
      <div class="ti" style="margin-top:12px">
        <div class="logHead">규칙</div>
        <div class="logBody">{node.rule}</div>
      </div>
      <div class="actions">
        <button class="btn" on:click={() => onCancel?.()}>취소</button>
        <button class="btn primary" on:click={confirm}>선택 확정</button>
      </div>
    </section>
  </div>
{/if}

<style>
  .backdrop{position:fixed; inset:0; z-index:65; display:grid; place-items:center; background:rgba(0,0,0,.56)}
  .panel{width:min(560px, calc(100% - 24px)); border:1px solid var(--line-default); border-radius:16px; padding:14px; background:var(--surface-1)}
  .actions{display:flex; justify-content:flex-end; gap:8px; margin-top:14px}
</style>
