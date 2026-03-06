<script lang="ts">
  export let title = ''
  export let summary = ''
</script>

<section class="card">
  <div class="row wrap" style="justify-content:space-between; gap:12px; align-items:flex-start;">
    <div class="grow">
      <div class="h2">{title}</div>
      <div class="hint" style="margin-top:6px">{summary}</div>
    </div>
    <div class="row wrap" style="justify-content:flex-end">
      <slot name="actions" />
    </div>
  </div>
  <div class="spacer"></div>
  <slot />
</section>
