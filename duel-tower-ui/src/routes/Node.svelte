<script lang="ts">
  import { run, pickNode, resetRun } from '../stores/run'
  import { navigate } from '../lib/router'

  const badgeFor: Record<string, { cls: string; label: string }> = {
    BATTLE: { cls: 'ok', label: '전투' },
    EVENT: { cls: '', label: '이벤트' },
    SHOP: { cls: '', label: '상점' },
    REST: { cls: '', label: '휴식' },
    ELITE: { cls: 'no', label: '정예' },
    BOSS: { cls: 'no', label: '보스' },
  }
</script>

<section class="split">
  <div>
    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div>
          <div class="h2">노드</div>
          <div class="hint">런 진행 UX 목업. (백엔드 연결 전이라 랜덤 생성)</div>
        </div>
        <div class="row wrap" style="justify-content:flex-end">
          <button class="btn" on:click={() => resetRun()}>런 리셋</button>
          <button class="btn" on:click={() => navigate('/combat')}>전투로</button>
        </div>
      </div>
      <div class="spacer"></div>
      <div class="kvs">
        <div class="kv">
          <div class="k">Floor</div>
          <div class="v">{$run.floor}</div>
        </div>
        <div class="kv">
          <div class="k">Seed</div>
          <div class="v mono">{$run.seed}</div>
        </div>
      </div>
    </div>

    <div class="spacer"></div>

    <div class="card">
      <div class="cardTitle">다음 노드 선택</div>
      <div class="spacer"></div>
      <div class="choices">
        {#each $run.choices as n, i (n.type + ':' + n.floor)}
          <div class="choice" on:click={() => pickNode(i)}>
            <div class="row wrap" style="justify-content:space-between">
              <div class="choiceTitle">{n.title}</div>
              <span class="badge" class:ok={badgeFor[n.type]?.cls === 'ok'} class:no={badgeFor[n.type]?.cls === 'no'}>
                {badgeFor[n.type]?.label ?? n.type}
              </span>
            </div>
            <div class="choiceDesc">{n.desc}</div>
          </div>
        {/each}
      </div>
      <div class="hint">클릭하면 히스토리에 추가되고 다음 층이 생성된다.</div>
    </div>
  </div>

  <aside>
    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">히스토리</div>
        <span class="badge">최근 {$run.history.length}</span>
      </div>
      <div class="spacer"></div>
      {#if !$run.history.length}
        <div class="muted">아직 선택한 노드가 없다.</div>
      {:else}
        <div class="timeline">
          {#each $run.history as n (n.type + ':' + n.floor + ':' + n.title)}
            <div class="ti">
              <div class="row wrap" style="justify-content:space-between">
                <b>{n.title}</b>
                <span class="badge">F{n.floor}</span>
              </div>
              <div class="hint">{badgeFor[n.type]?.label ?? n.type}</div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  </aside>
</section>
