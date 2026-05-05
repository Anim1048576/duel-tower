package com.example.dueltower.screen.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.example.dueltower.session.dto.ControllableActorDto;

/**
 * Combat screen read model curated for the frontend.
 *
 * <p>The server owns combat-facing card resolution, actor/status/sidebar
 * assembly, action metadata, and read-access summary. The frontend should use
 * this payload as the render source of truth and keep only local
 * selection/presentation state on top.</p>
 */
public class CombatScreenResponse extends ScreenResponseBase {
    private final String sessionCode;
    private final long version;
    private final boolean changed;
    private final Status status;
    private final Access access;
    private final Actors actors;
    private final Zones zones;
    private final Sidebar sidebar;

    public CombatScreenResponse(String screenKey,
                                OffsetDateTime generatedAt,
                                List<String> uiNotices,
                                List<ScreenActionDto> possibleActions,
                                String sessionCode,
                                long version,
                                boolean changed,
                                Status status,
                                Access access,
                                Actors actors,
                                Zones zones,
                                Sidebar sidebar) {
        super(screenKey, generatedAt, uiNotices, possibleActions);
        this.sessionCode = sessionCode;
        this.version = version;
        this.changed = changed;
        this.status = status;
        this.access = access;
        this.actors = actors;
        this.zones = zones;
        this.sidebar = sidebar;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public long getVersion() {
        return version;
    }

    public boolean isChanged() {
        return changed;
    }

    public Status getStatus() {
        return status;
    }

    public Access getAccess() {
        return access;
    }

    public Actors getActors() {
        return actors;
    }

    public Zones getZones() {
        return zones;
    }

    public Sidebar getSidebar() {
        return sidebar;
    }

    public record Status(
            Integer round,
            String phase,
            ActorSummary currentActor,
            String turnOrderSummary,
            String battlefieldSummary,
            String runSummary,
            String tieGroupSummary
    ) {}

    public record ActorSummary(
            String raw,
            String kind,
            String id,
            String label,
            String note,
            String tone
    ) {}

    public record Access(
            String role,
            String runtimePlayerId,
            long expectedVersion,
            GuardSummary guards,
            List<ControllableActorDto> controllableActors
    ) {}

    public record GuardSummary(
            boolean canIssuePlayerCommand,
            boolean canResolvePendingCommand,
            boolean canClearRecentResultsCommand,
            boolean canIssueGmCommand,
            boolean exAvailable,
            boolean hasPendingDecision,
            boolean isCurrentTurnPlayer,
            boolean hasCombatState
    ) {}

    public record Actors(
            List<PlayerView> players,
            List<EnemyView> enemies,
            List<SummonView> summons
    ) {}

    public record PlayerView(
            String playerId,
            boolean ready,
            String stateLabel,
            String stateTone,
            List<Metric> metrics,
            List<String> summaryLines,
            List<Tag> statusTags,
            List<String> passives,
            List<CardView> handCards,
            List<CardView> fieldCards,
            List<CardView> graveCards,
            List<CardView> excludedCards,
            CardView exCard
    ) {}

    public record EnemyView(
            String enemyId,
            String stateLabel,
            String stateTone,
            List<Metric> metrics,
            List<String> summaryLines,
            List<String> statusEntries
    ) {}

    public record SummonView(
            String summonId,
            String owner,
            String stateLabel,
            String stateTone,
            List<Metric> metrics,
            List<String> summaryLines
    ) {}

    public record Metric(
            String label,
            Object value,
            String note
    ) {}

    public record Tag(
            String label,
            String tone
    ) {}

    public record Zones(
            String visiblePlayerId,
            List<CardView> hand,
            List<CardView> field,
            List<CardView> grave,
            List<CardView> excluded,
            CardView ex
    ) {}

    public record CardView(
            String instanceId,
            String defId,
            String title,
            String subtitle,
            boolean unresolved,
            List<Tag> tags,
            String meta
    ) {}

    public record Sidebar(
            List<FeedEntry> events,
            List<FeedEntry> logs,
            List<RecentResultEntry> recentResults
    ) {}

    public record FeedEntry(
            String title,
            List<String> lines
    ) {}

    public record RecentResultEntry(
            String title,
            String summary,
            String meta
    ) {}
}
