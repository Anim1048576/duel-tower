<script lang="ts">
  import { ruleTooltip } from '../../stores/ui'
</script>

{#if $ruleTooltip.open}
  <div class="tooltip" style={`left:${$ruleTooltip.x}px; top:${$ruleTooltip.y}px;`}>
    {$ruleTooltip.text}
  </div>
{/if}

<style>
  .tooltip{position:fixed; z-index:90; pointer-events:none; max-width:280px; padding:8px 10px; border-radius:10px; border:1px solid var(--line-info); background:var(--surface-1); color:var(--text); font-size:12px}
</style>
