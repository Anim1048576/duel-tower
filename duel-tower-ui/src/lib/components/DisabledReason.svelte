<script lang="ts">
  export let show = false
  export let reason = ''
</script>

{#if show && reason}
  <div class="reason">{reason}</div>
{/if}

<style>
  .reason{margin-top:6px; font-size:12px; color: var(--state-danger)}
</style>
