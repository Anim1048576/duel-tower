<script lang="ts">
  export let label = ''
  export let value = 0
  export let max = 1
  $: percent = Math.max(0, Math.min(100, Math.round((value / Math.max(1, max)) * 100)))
</script>

<div class="resource">
  <div class="head"><span>{label}</span><span>{value}/{max}</span></div>
  <div class="track"><div class="fill" style={`width:${percent}%`}></div></div>
</div>

<style>
  .resource{display:flex; flex-direction:column; gap:6px}
  .head{display:flex; justify-content:space-between; font-size:12px; color:var(--text-muted)}
  .track{height:8px; border-radius:999px; background:var(--surface-2); overflow:hidden; border:1px solid var(--line-default)}
  .fill{height:100%; background:linear-gradient(90deg, var(--state-info), var(--state-ok))}
</style>
