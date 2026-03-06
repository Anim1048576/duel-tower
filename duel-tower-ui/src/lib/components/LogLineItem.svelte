<script lang="ts">
  export let at = ''
  export let level = ''
  export let title = ''
  export let message = ''
</script>

<div class="logItem">
  <div class="head">{at} · {level}</div>
  <div class="body">{title}{message ? ` · ${message}` : ''}</div>
</div>

<style>
  .logItem{padding:10px; border:1px solid var(--line-default); border-radius:14px; background: var(--surface-2)}
  .head{font-size:12px; color:var(--text-muted)}
  .body{margin-top:6px; line-height:1.35}
</style>
