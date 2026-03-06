<script lang="ts">
  import { confirmAction, resolveConfirm } from '../../stores/ui'

  function onBackdropKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') resolveConfirm(false)
  }

  function onBackdropClick(event: MouseEvent) {
    if (event.target === event.currentTarget) resolveConfirm(false)
  }
</script>

{#if $confirmAction.open && $confirmAction.payload}
  <div class="backdrop" role="button" tabindex="0" aria-label="모달 닫기" on:click={onBackdropClick} on:keydown={onBackdropKeydown}>
    <section class="panel" role="dialog" aria-modal="true" tabindex="-1">
      <div class="title">{$confirmAction.payload.title}</div>
      {#if $confirmAction.payload.message}
        <div class="message">{$confirmAction.payload.message}</div>
      {/if}
      <div class="actions">
        <button class="btn" on:click={() => resolveConfirm(false)}>{$confirmAction.payload.cancelLabel ?? '취소'}</button>
        <button class="btn" class:danger={$confirmAction.payload.tone === 'danger'} class:primary={$confirmAction.payload.tone !== 'danger'} on:click={() => resolveConfirm(true)}>{$confirmAction.payload.confirmLabel ?? '확인'}</button>
      </div>
    </section>
  </div>
{/if}

<style>
  .backdrop{position:fixed; inset:0; z-index:70; display:grid; place-items:center; background:rgba(0,0,0,.56)}
  .panel{width:min(520px, calc(100% - 24px)); border:1px solid var(--line-default); border-radius:16px; padding:14px; background:var(--surface-1)}
  .title{font-weight:900}
  .message{margin-top:8px; color:var(--text-muted)}
  .actions{display:flex; justify-content:flex-end; gap:8px; margin-top:14px}
</style>
