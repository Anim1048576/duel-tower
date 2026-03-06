<script lang="ts">
  import { navigate } from '../lib/router'
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { combat } from '../stores/combat'

  $: inCombat = Boolean($combat.state?.combat)
  $: version = $combat.state?.version
</script>

<PageSkeleton title="Combat" summary="전투 전용 레이아웃 (디버그/임시 조작 제거)">
  <button slot="actions" class="btn" on:click={() => navigate('/logs')}>전투 로그</button>

  <div class="kvs">
    <div class="kv"><div class="k">상태</div><div class="v">{inCombat ? '진행 중' : '대기'}</div></div>
    <div class="kv"><div class="k">State Ver</div><div class="v mono">{version ?? '—'}</div></div>
  </div>
  <div class="spacer"></div>
  <div class="hint">전투 액션 바/타임라인/판정 UI는 이 레이아웃에 단계적으로 추가 예정.</div>
</PageSkeleton>
