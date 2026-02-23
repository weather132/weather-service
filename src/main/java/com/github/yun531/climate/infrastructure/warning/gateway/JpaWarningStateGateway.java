package com.github.yun531.climate.infrastructure.warning.gateway;

import com.github.yun531.climate.infrastructure.persistence.entity.WarningState;
import com.github.yun531.climate.infrastructure.persistence.repository.WarningStateRepository;
import com.github.yun531.climate.notification.domain.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.readmodel.WarningStateView;
import com.github.yun531.climate.service.notification.model.WarningKind;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Primary      //todo 현재 기본은 JPA, warningapi 전환 시 ApiWarningStateGateway로 @Primary 이동
@RequiredArgsConstructor
public class JpaWarningStateGateway implements WarningStateReadPort {

    private final WarningStateRepository repo;

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        if (regionId == null || regionId.isBlank()) return Map.of();

        List<WarningState> rows = repo.findByRegionIdIn(List.of(regionId));
        if (rows == null || rows.isEmpty()) return Map.of();

        Map<WarningKind, WarningState> picked = new HashMap<>();

        for (WarningState ws : rows) {
            if (ws == null) continue;
            if (!regionId.equals(ws.getRegionId())) continue;

            WarningKind kind = ws.getKind();
            if (kind == null) continue;

            picked.merge(kind, ws, JpaWarningStateGateway::newer);
        }

        if (picked.isEmpty()) return Map.of();

        Map<WarningKind, WarningStateView> out = new HashMap<>();
        for (var e : picked.entrySet()) {
            out.put(e.getKey(), toView(e.getValue()));
        }
        return out;
    }

    @Override
    public boolean isNewlyIssuedSince(@Nullable WarningStateView state, @Nullable LocalDateTime since) {
        return state != null
                && state.updatedAt() != null
                && since != null
                && state.updatedAt().isAfter(since);
    }

    private static WarningStateView toView(WarningState ws) {
        if (ws == null) return null;
        return new WarningStateView(
                ws.getRegionId(),
                ws.getKind(),
                ws.getLevel(),
                ws.getUpdatedAt()
        );
    }

    /** updatedAt 최신 우선, 동일 시각이면 warningId 큰 쪽 우선 */
    private static WarningState newer(WarningState a, WarningState b) {
        if (a == null) return b;
        if (b == null) return a;

        LocalDateTime ta = a.getUpdatedAt();
        LocalDateTime tb = b.getUpdatedAt();

        if (ta == null && tb == null) return safeId(a) >= safeId(b) ? a : b;
        if (ta == null) return b;
        if (tb == null) return a;

        int cmp = ta.compareTo(tb);
        if (cmp != 0) return (cmp > 0) ? a : b;

        return safeId(a) >= safeId(b) ? a : b;
    }

    private static int safeId(WarningState ws) {
        return (ws == null || ws.getWarningId() == null) ? -1 : ws.getWarningId();
    }
}