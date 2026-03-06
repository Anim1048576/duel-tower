<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  export let id = ''
</script>

<PageSkeleton title={`Character #${id || '-'}`} summary="캐릭터 상세/성장 페이지 스켈레톤">
  <div class="hint">헤더 / 요약 / 액션 슬롯 구조만 배치됨.</div>
</PageSkeleton>
