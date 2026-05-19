package com.example.dueltower.session.service;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.RunState;
import com.example.dueltower.session.dto.RecentResultsResponse;
import com.example.dueltower.session.dto.RunStateDto;
import com.example.dueltower.session.dto.SessionShopResponse;
import com.example.dueltower.session.dto.SessionRunChoicesResponse;
import com.example.dueltower.session.dto.SessionRunInventoryResponse;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
@Slf4j
/**
 * Session read-only query service.
 *
 * <p>세션 조회 권한 확인과 read DTO 조립을 한 곳에 모은다.</p>
 */
public class SessionQueryService {

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionAccessResolver sessionAccessResolver;

    public SessionQueryService(SessionLifecycleService sessionLifecycleService,
                               SessionAccessResolver sessionAccessResolver) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionAccessResolver = sessionAccessResolver;
    }

    public SessionStateDto getPublicState(String code) {
        return sessionLifecycleService.withLockedSession(code, rt -> {
            log.debug("session state requested code={} version={}", code, rt.state().version());
            return StateMapper.toDto(rt.code(), rt.state());
        });
    }

    public RecentResultsResponse getRecentResults(String code,
                                                  String gmTokenHeader,
                                                  String playerTokenHeader,
                                                  Authentication authentication) {
        return withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET /api/sessions/{code}/recent-results",
                rt -> new RecentResultsResponse(
                        rt.state().version(),
                        rt.state().runState().resultPending(),
                        StateMapper.toCurrentNodeDto(rt.state().runState()),
                        StateMapper.toRecentResultDtos(rt.state().runState())
                )
        );
    }

    public RunStateDto getRun(String code,
                              String gmTokenHeader,
                              String playerTokenHeader,
                              Authentication authentication) {
        return withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET /api/sessions/{code}/run",
                rt -> StateMapper.toRunDto(rt.state().runState())
        );
    }

    public SessionRunInventoryResponse getInventory(String code,
                                                    String gmTokenHeader,
                                                    String playerTokenHeader,
                                                    Authentication authentication) {
        return withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET /api/sessions/{code}/inventory",
                rt -> new SessionRunInventoryResponse(
                        rt.state().version(),
                        StateMapper.toInventoryDto(rt.state().runState())
                )
        );
    }

    public SessionShopResponse getShop(String code,
                                       String gmTokenHeader,
                                       String playerTokenHeader,
                                       Authentication authentication) {
        return withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET /api/sessions/{code}/shop",
                rt -> {
                    ShopAvailability availability = shopAvailability(rt.state());
                    return new SessionShopResponse(
                            rt.code(),
                            rt.state().version(),
                            availability.open(),
                            availability.unavailableReason(),
                            rt.state().runState().inventory().gold(),
                            StateMapper.toShopOfferDtos(rt.state().runState())
                    );
                }
        );
    }

    public RecentResultsResponse getResults(String code,
                                            String gmTokenHeader,
                                            String playerTokenHeader,
                                            Authentication authentication) {
        return withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET /api/sessions/{code}/results",
                rt -> new RecentResultsResponse(
                        rt.state().version(),
                        rt.state().runState().resultPending(),
                        StateMapper.toCurrentNodeDto(rt.state().runState()),
                        StateMapper.toRecentResultDtos(rt.state().runState())
                )
        );
    }

    public SessionRunChoicesResponse getChoices(String code,
                                                String gmTokenHeader,
                                                String playerTokenHeader,
                                                Authentication authentication) {
        return withSessionReadableAccess(
                code,
                gmTokenHeader,
                playerTokenHeader,
                authentication,
                "GET /api/sessions/{code}/choices",
                rt -> new SessionRunChoicesResponse(
                        rt.state().version(),
                        rt.state().runState().resultPending(),
                        StateMapper.toCurrentNodeDto(rt.state().runState()),
                        StateMapper.toNodeChoiceDtos(rt.state().runState())
                )
        );
    }

    /**
     * 조회 전용 세션 read 진입점.
     * get(code), read 권한 확인, access log, lock 기반 DTO 조립을 한 곳에 모은다.
     */
    public <T> T withSessionReadableAccess(String code,
                                           String gmTokenHeader,
                                           String playerTokenHeader,
                                           Authentication authentication,
                                           String endpoint,
                                           Function<SessionRuntime, T> reader) {
        return sessionLifecycleService.withLockedSession(code, rt -> {
            SessionAccessDecision decision = sessionAccessResolver.requireSessionReadable(rt, gmTokenHeader, playerTokenHeader, authentication);
            log.info("session read granted code={} endpoint={} source={} tokenBased={} loginBased={} username={} playerId={}",
                    decision.sessionCode(),
                    endpoint,
                    decision.source(),
                    decision.tokenBased(),
                    decision.loginBased(),
                    decision.username(),
                    decision.playerId()
            );
            return reader.apply(rt);
        });
    }

    private ShopAvailability shopAvailability(GameState state) {
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            return new ShopAvailability(false, "전투 중에는 구매할 수 없습니다.");
        }
        RunState run = state.runState();
        if (run.currentNode() == null || run.currentNode().phase() != RunState.NodePhase.EVENT) {
            return new ShopAvailability(false, "이벤트 노드에서만 구매할 수 있습니다.");
        }
        if (run.resultPending()) {
            return new ShopAvailability(false, "이벤트 결과 확인 후 구매할 수 있습니다.");
        }
        return new ShopAvailability(true, null);
    }

    private record ShopAvailability(boolean open, String unavailableReason) {}
}
