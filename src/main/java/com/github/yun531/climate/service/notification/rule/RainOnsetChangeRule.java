package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.RainThresholdEnum;
import com.github.yun531.climate.service.notification.model.PopViewPair;
import com.github.yun531.climate.service.notification.model.payload.ValidAtPayload;
import com.github.yun531.climate.service.notification.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.util.cache.CacheEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class RainOnsetChangeRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private static final int RAIN_TH = RainThresholdEnum.RAIN.getThreshold();
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    /** 추적용(typed payload의 srcRule에 넣을 값) */
    private static final String SRC_RULE_NAME = "RainOnsetChangeRule";

    /** now 기준으로 1~24시간 윈도우만 반환 */
    private static final int WINDOW_HOURS = 24;

    private final SnapshotQueryService snapshotQueryService;

    /** baseTime 없이 now 기준 윈도우(+1~+24)로 제한 */
    private final RainOnsetEventValidAtAdjuster windowAdjuster =
            new RainOnsetEventValidAtAdjuster(WINDOW_HOURS);

    /** typed payload를 생성하는 컴퓨터 */
    private final RainOnsetEventComputer computer =
            new RainOnsetEventComputer(RAIN_TH, SRC_RULE_NAME);

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_ONSET;
    }

    @Override
    protected int thresholdMinutes() {
        return RECOMPUTE_THRESHOLD_MINUTES;
    }

    @Override
    protected CacheEntry<List<AlertEvent>> computeForRegion(String regionId) {
        PopViewPair pair = snapshotQueryService.loadDefaultPopViewPair(regionId);
        if (pair == null || pair.current() == null || pair.previous() == null) {
            return new CacheEntry<>(List.of(), null);
        }

        // 캐시 computedAt: 가능하면 "발표 시각(reportTime)"을 사용
        LocalDateTime computedAt =
                (pair.current().reportTime() != null) ? pair.current().reportTime() : nowMinutes();

        List<AlertEvent> events = computer.detect(regionId, pair, computedAt);
        return new CacheEntry<>(events, computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(
            String regionId,
            List<AlertEvent> events,
            LocalDateTime computedAt,
            LocalDateTime now,
            NotificationRequest request
    ) {
        if (events == null || events.isEmpty()) return List.of();

        // now 기준 윈도우(+1~+24)로 제한 + occurredAt은 nowHour로 통일
        List<AlertEvent> adjusted = windowAdjuster.adjust(events, now);
        if (adjusted == null || adjusted.isEmpty()) return List.of();

        Integer maxHour = request.rainHourLimit();
        if (maxHour != null) {
            adjusted = filterByMaxHour(adjusted, maxHour, now);
        }

        return adjusted.isEmpty() ? List.of() : List.copyOf(adjusted);
    }

    /** rainHourLimit: now 기준 N시간 이내만 남김
     * - validAt <= nowHour + maxHourInclusive  */
    private List<AlertEvent> filterByMaxHour(List<AlertEvent> events, int maxHourInclusive, LocalDateTime now) {
        LocalDateTime nowHour = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime limit = nowHour.plusHours(maxHourInclusive);

        List<AlertEvent> out = new ArrayList<>();
        for (AlertEvent e : events) {
            LocalDateTime at = extractValidAt(e);
            if (at == null || !at.isAfter(limit)) { // at <= limit
                out.add(e);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private LocalDateTime extractValidAt(AlertEvent event) {
        if (event == null || event.payload() == null) return null;
        if (event.payload() instanceof ValidAtPayload v) return v.validAt();
        return null;
    }
}