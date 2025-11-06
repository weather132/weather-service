package com.github.yun531.climate.service;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.dto.WarningStateDto;
import com.github.yun531.climate.entity.WarningState;
import com.github.yun531.climate.repository.WarningStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarningService {

    private final WarningStateRepository warningStateRepository;

    /**
     * 지역별 모든 kind에 대해, 각 kind의 "가장 최근(updated_at DESC)" 특보 상태 1건씩 반환.
     * 반환 구조: regionId -> (kind -> 최신 WarningStateDto)
     */
    public Map<Long, Map<WarningKind, WarningStateDto>> findLatestByRegionAndKind(List<Long> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return Map.of();

        // 입력 regionId 순서를 보존하기 위해 미리 빈 맵을 만들어 둠
        Map<Long, Map<WarningKind, WarningStateDto>> result = new LinkedHashMap<>();
        for (Long regionId : regionIds) {
            result.put(regionId, new LinkedHashMap<>());
        }

        // 요청된 모든 regionId의 모든 레코드를 한번에 로드
        List<WarningState> rows = warningStateRepository.findByRegionIdIn(regionIds);

        // regionId + kind 별로 최신(updated_at) 1건을 선택
        // (동일 시각 tie-breaker로 warningId가 큰 쪽 채택)
        Map<Long, Map<WarningKind, WarningState>> pick = new HashMap<>();
        for (WarningState ws : rows) {
            Long regionId = ws.getRegionId();
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
            Long regionId = e.getKey();
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
        Instant ia = a.getUpdatedAt();
        Instant ib = b.getUpdatedAt();

        if (ia == null && ib == null) {
            return a.getWarningId() > b.getWarningId(); // tie-break
        }
        if (ia == null) return false;
        if (ib == null) return true;

        int cmp = ia.compareTo(ib);
        if (cmp != 0) return cmp > 0;
        return a.getWarningId() > b.getWarningId(); // 동일 시각일 때 더 최신 id 선택
    }

    /** since 이후 '새로 발효/변경'으로 간주 */
    public boolean isNewlyIssuedSince(WarningStateDto state, Instant since) {
        return state != null
                && state.getUpdatedAt() != null
                && state.getUpdatedAt().isAfter(since);
    }
}