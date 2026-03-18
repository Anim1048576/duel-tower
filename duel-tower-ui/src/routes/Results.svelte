<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { navigate } from '../lib/router'
  import { combat, command } from '../stores/combat'
  import { logs } from '../stores/log'

  function tone(type: string) {
    if (type === 'reward') return 'ok'
    if (type === 'explore_fail') return 'no'
    return 'info'
  }

  $: resultCards = $combat.state?.run?.recentResults ?? []
  $: runState = $combat.state?.run ?? null
  $: currentNode = runState?.currentNode ?? null
  $: recentLogs = $logs.slice(0, 5)
  $: detailLogs = $combat.lastResolutionLogs.slice(0, 5)

  async function backToNode() {
    await command({ type: 'CLEAR_RECENT_RESULTS' })
    navigate('/node')
  }
</script>

<PageSkeleton title="Results" summary="전투 종료/탐사 실패/보상 획득 공통 결과 카드">
  <button slot="actions" class="btn" on:click={backToNode}>노드로 복귀</button>

  {#if !$combat.state}
    <div class="hint">세션 상태를 불러오는 중...</div>
  {:else if !resultCards.length}
    <div class="hint">최근 탐색 결과가 없어 기본 로그만 표시한다.</div>
  {:else}
    <div class="card" style="margin-top:10px">
      <div class="cardTitle">현재 정산 중인 노드</div>
      <div class="hint">{currentNode ? `층 ${currentNode.floor} · ${currentNode.name} · ${currentNode.typeLabel}` : '정산 대상 노드 없음'}</div>
    </div>
    <div class="table" style="margin-top:10px">
      {#each resultCards as card (card.id)}
        <article class="tr">
          <div class="grow">
            <div class="row wrap">
              <b>{card.title}</b>
              <span class={`badge ${tone(card.type)}`}>{card.type}</span>
            </div>
            <div class="hint">{card.summary}</div>
            {#if card.detail}
              <div style="margin-top:8px">{card.detail}</div>
            {/if}
          </div>
        </article>
      {/each}
    </div>
  {/if}

  <div class="spacer"></div>
  <div class="grid2">
    <section class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">최근 로그 재확인</div>
        <button class="btn" on:click={() => navigate('/logs')}>전체 로그</button>
      </div>
      <div class="spacer"></div>
      {#if !recentLogs.length}
        <div class="hint">표시할 최근 로그 없음</div>
      {:else}
        <div class="table">
          {#each recentLogs as log (log.id)}
            <div class="tr">
              <div class="grow">
                <b>{log.title}</b>
                <div class="hint">{log.message || '—'}</div>
              </div>
              <span class="mono">{new Date(log.at).toLocaleTimeString()}</span>
            </div>
          {/each}
        </div>
      {/if}
    </section>

    <section class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">상세 로그 재확인</div>
        <button class="btn" on:click={() => navigate('/combat')}>전투 상세</button>
      </div>
      <div class="spacer"></div>
      {#if !detailLogs.length}
        <div class="hint">resolution log 없음</div>
      {:else}
        <div class="table">
          {#each detailLogs as detail (detail.id)}
            <div class="tr">
              <div class="grow">
                <b>{detail.summary}</b>
                <div class="hint">{detail.breakdown}</div>
              </div>
              <span class="mono">{new Date(detail.at).toLocaleTimeString()}</span>
            </div>
          {/each}
        </div>
      {/if}
    </section>
  </div>
</PageSkeleton>
