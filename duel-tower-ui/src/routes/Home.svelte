<script lang="ts">
  import { navigate } from '../lib/router'
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { session } from '../stores/session'
</script>

<PageSkeleton title="Duel Tower" summary="페이지 단위 재정의 안내">
  <button slot="actions" class="btn primary" on:click={() => navigate('/session')}>세션 시작</button>

  <div class="list">
    <li><b>/session</b> : 세션 생성/참가</li>
    <li><b>/lobby</b> : 세션 허브 / 상태 점검</li>
    <li><b>/combat</b> : 전투 전용 레이아웃</li>
  </div>

  <div class="spacer"></div>
  <div class="hint">현재 세션 코드: <span class="mono">{$session.code || '없음'}</span></div>
</PageSkeleton>
