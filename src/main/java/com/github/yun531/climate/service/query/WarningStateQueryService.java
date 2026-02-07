package com.github.yun531.climate.service.query;

import com.github.yun531.climate.entity.WarningState;
import com.github.yun531.climate.repository.WarningStateRepository;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarningStateQueryService {

    private final WarningStateRepository warningStateRepository;

    /** 여러 지역(regionId)들에 대해, 경보 종류(kind)별로 가장 최신의 경보 상태 1건만 뽑아서 DTO로 반환 */
    public Map<String, Map<WarningKind, WarningStateDto>> findLatestByRegionAndKind(List<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return Map.of();

        // 입력 regionId 순서 보존
        Map<String, Map<WarningKind, WarningStateDto>> result = new LinkedHashMap<>();
        for (String regionId : regionIds) {
            result.put(regionId, new LinkedHashMap<>());
        }

        Set<String> valid = new HashSet<>(regionIds);

        // regionId + kind => 최신 WarningState(엔티티)만 보관
        Map<String, Map<WarningKind, WarningState>> picked = new HashMap<>();

        List<WarningState> rows = warningStateRepository.findByRegionIdIn(regionIds);
        for (WarningState ws : rows) {
            if (ws == null) continue;

            String regionId = ws.getRegionId();
            if (!valid.contains(regionId)) continue;

            picked.computeIfAbsent(regionId, k -> new HashMap<>())
                    .merge(ws.getKind(), ws, WarningStateQueryService::newer);
        }

        // DTO 변환하여 result에 채우기
        for (String regionId : regionIds) {
            Map<WarningKind, WarningState> byKind = picked.get(regionId);
            if (byKind == null) continue;

            Map<WarningKind, WarningStateDto> dtoByKind = result.get(regionId);
            for (var e : byKind.entrySet()) {
                dtoByKind.put(e.getKey(), WarningStateDto.from(e.getValue()));
            }
        }

        return result;
    }

    /** updatedAt 최신 우선, 동일 시각이면 warningId 큰 쪽 우선 */
    private static WarningState newer(WarningState a, WarningState b) {
        if (a == null) return b;
        if (b == null) return a;

        LocalDateTime ta = a.getUpdatedAt();
        LocalDateTime tb = b.getUpdatedAt();

        if (ta == null && tb == null) return a.getWarningId() >= b.getWarningId() ? a : b;
        if (ta == null) return b;
        if (tb == null) return a;

        int cmp = ta.compareTo(tb);
        if (cmp != 0) return (cmp > 0) ? a : b;

        return a.getWarningId() >= b.getWarningId() ? a : b;
    }

    /** since 이후 새로 발효/변경 판단 */
    public boolean isNewlyIssuedSince(WarningStateDto state, LocalDateTime since) {
        return state != null
                && state.updatedAt() != null
                && since != null
                && state.updatedAt().isAfter(since);
    }
}