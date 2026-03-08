package com.github.yun531.climate.warning.infra.persistence.mapper;

import com.github.yun531.climate.warning.infra.persistence.entity.WarningStateEntity;
import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IssuedWarningMapper {

    private IssuedWarningMapper() {}

    public static Map<WarningKind, IssuedWarning> pickLatestByKind(String regionId, List<WarningStateEntity> rows) {
        if (regionId == null || regionId.isBlank() || rows == null || rows.isEmpty()) return Map.of();

        Map<WarningKind, WarningStateEntity> picked = new HashMap<>();

        for (WarningStateEntity ws : rows) {
            if (ws == null) continue;
            if (!regionId.equals(ws.getRegionId())) continue;

            WarningKind kind = ws.getKind();
            if (kind == null) continue;

            picked.merge(kind, ws, IssuedWarningMapper::newer);
        }

        if (picked.isEmpty()) return Map.of();

        Map<WarningKind, IssuedWarning> out = new HashMap<>();
        for (var e : picked.entrySet()) {
            out.put(e.getKey(), toView(e.getValue()));
        }
        return out;
    }

    public static IssuedWarning toView(WarningStateEntity ws) {
        if (ws == null) return null;
        return new IssuedWarning(
                ws.getRegionId(),
                ws.getKind(),
                ws.getLevel(),
                ws.getUpdatedAt()
        );
    }

    /** updatedAt 최신 우선, 동일 시각이면 warningId 큰 쪽 우선 */
    private static WarningStateEntity newer(WarningStateEntity a, WarningStateEntity b) {
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

    private static int safeId(WarningStateEntity ws) {
        return (ws == null || ws.getWarningId() == null) ? -1 : ws.getWarningId();
    }
}