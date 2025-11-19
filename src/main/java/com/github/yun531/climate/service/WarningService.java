package com.github.yun531.climate.service;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.dto.WarningStateDto;
import com.github.yun531.climate.entity.WarningState;
import com.github.yun531.climate.repository.WarningStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WarningService {

    private final WarningStateRepository warningStateRepository;

    public Map<Integer, Map<WarningKind, WarningStateDto>> findLatestByRegionAndKind(List<Integer> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return Map.of();

        // 입력 regionId 순서를 보존하기 위해 미리 빈 맵을 만들어 둠
        Map<Integer, Map<WarningKind, WarningStateDto>> result = new LinkedHashMap<>();
        for (int regionId : regionIds) {
            result.put(regionId, new LinkedHashMap<>());
        }

        // 요청된 모든 regionId의 모든 레코드를 한번에 로드
        List<WarningState> rows = warningStateRepository.findByRegionIdIn(regionIds);

        // regionId + kind 별로 최신(updated_at) 1건을 선택
        // (동일 시각 tie-breaker로 warningId가 큰 쪽 채택)
        Map<Integer, Map<WarningKind, WarningState>> pick = new HashMap<>();
        for (WarningState ws : rows) {
            int regionId = ws.getRegionId();
            if (!result.containsKey(regionId)) continue; // 방어

            Map<WarningKind, WarningState> byKind =                                // 레퍼런스 참조
                    pick.computeIfAbsent(regionId, k -> new HashMap<>());    // computeIfAbsent: regionId 키가 pick 안에 없으면 새로 만들어 넣고, 있으면 기존 값 그대로 가져와라

            WarningState prev = byKind.get(ws.getKind());
            if (prev == null || isAfter(ws, prev)) {
                byKind.put(ws.getKind(), ws);
            }
        }

        // Entity -> DTO 변환
        for (var e : pick.entrySet()) {
            int regionId = e.getKey();
            Map<WarningKind, WarningStateDto> mapByKind = new LinkedHashMap<>();

            for (var kEntry : e.getValue().entrySet()) {
                WarningState ws = kEntry.getValue();
                mapByKind.put(kEntry.getKey(), WarningStateDto.from(ws));
            }
            result.put(regionId, mapByKind);
        }

        return result;
    }

    private boolean isAfter(WarningState a, WarningState b) {
        LocalDateTime ia = a.getUpdatedAt();
        LocalDateTime ib = b.getUpdatedAt();

        if (ia == null && ib == null) {
            return a.getWarningId() > b.getWarningId();
        }
        if (ia == null) return false;
        if (ib == null) return true;   // null 이 아닌쪽 이 더 최신

        int cmp = ia.compareTo(ib);
        if (cmp != 0) return cmp > 0;

        return a.getWarningId() > b.getWarningId(); // 동일 시각일 때 더 최신 id 선택
    }

    /** since 이후 새로 발효/변경 판단 */
    public boolean isNewlyIssuedSince(WarningStateDto state, LocalDateTime since) {
        return state != null
                && state.getUpdatedAt() != null
                && state.getUpdatedAt().isAfter(since);
    }
}