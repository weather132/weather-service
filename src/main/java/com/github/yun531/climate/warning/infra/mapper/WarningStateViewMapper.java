package com.github.yun531.climate.warning.infra.mapper;

import com.github.yun531.climate.warning.infra.persistence.entity.WarningState;
import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WarningStateViewMapper {

    private WarningStateViewMapper() {}

    public static Map<WarningKind, WarningStateView> pickLatestByKind(String regionId, List<WarningState> rows) {
        if (regionId == null || regionId.isBlank() || rows == null || rows.isEmpty()) return Map.of();

        Map<WarningKind, WarningState> picked = new HashMap<>();

        for (WarningState ws : rows) {
            if (ws == null) continue;
            if (!regionId.equals(ws.getRegionId())) continue;

            WarningKind kind = ws.getKind();
            if (kind == null) continue;

            picked.merge(kind, ws, WarningStateViewMapper::newer);
        }

        if (picked.isEmpty()) return Map.of();

        Map<WarningKind, WarningStateView> out = new HashMap<>();
        for (var e : picked.entrySet()) {
            out.put(e.getKey(), toView(e.getValue()));
        }
        return out;
    }

    public static WarningStateView toView(WarningState ws) {
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