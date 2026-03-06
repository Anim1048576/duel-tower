<script lang="ts">
  export let label = ''
  export let tone: 'neutral' | 'ok' | 'danger' | 'info' = 'neutral'
</script>

<span class="badge" class:ok={tone === 'ok'} class:danger={tone === 'danger'} class:info={tone === 'info'}>{label}</span>

<style>
  .badge{padding:4px 8px; border-radius:999px; border:1px solid var(--line-default); font-size:12px; color:var(--text-muted); background: var(--surface-2)}
  .ok{border-color: var(--line-ok); color: var(--state-ok); background: var(--state-ok-bg)}
  .danger{border-color: var(--line-danger); color: var(--state-danger); background: var(--state-danger-bg)}
  .info{border-color: var(--line-info); color: var(--state-info); background: var(--state-info-bg)}
</style>
