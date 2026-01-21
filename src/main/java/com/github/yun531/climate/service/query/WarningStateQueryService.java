package com.github.yun531.climate.service.query;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import com.github.yun531.climate.entity.WarningState;
import com.github.yun531.climate.repository.WarningStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarningStateQueryService {

    private final WarningStateRepository warningStateRepository;

    public Map<String, Map<WarningKind, WarningStateDto>> findLatestByRegionAndKind(List<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return Map.of();
        }

        // regionId 순서를 보존하는 결과 맵 골격
        Map<String, Map<WarningKind, WarningStateDto>> result = initResultSkeleton(regionIds);

        // DB 에서 모든 WarningState 로드
        List<WarningState> warningStates = loadStates(regionIds);

        // regionId + kind 별로 최신 상태 선별
        Map<String, Map<WarningKind, WarningState>> pickedWarningStates =
                selectLatestStates(warningStates, result.keySet());

        // Entity → DTO 로 변환하여 결과에 채우기
        fillResult(result, pickedWarningStates);

        return result;
    }

    /** regionId 순서를 보존하는 빈 결과 맵 생성 */
    private Map<String, Map<WarningKind, WarningStateDto>> initResultSkeleton(List<String> regionIds) {
        Map<String, Map<WarningKind, WarningStateDto>> result = new LinkedHashMap<>();
        for (String regionId : regionIds) {
            result.put(regionId, new LinkedHashMap<>());
        }
        return result;
    }

    /** 주어진 regionIds 에 대한 WarningState 전체 로드 */
    private List<WarningState> loadStates(List<String> regionIds) {
        return warningStateRepository.findByRegionIdIn(regionIds);
    }

    /**
     * regionId + kind 조합별로
     * updated_at 기준 최신 1건(동일 시각이면 warningId 큰 것)을 선별
     */
    private Map<String, Map<WarningKind, WarningState>> selectLatestStates(
            List<WarningState> rows,
            Set<String> validRegionIds
    ) {
        Map<String, Map<WarningKind, WarningState>> pick = new HashMap<>();

        for (WarningState ws : rows) {
            String regionId = ws.getRegionId();
            if (!validRegionIds.contains(regionId)) {
                continue; // 방어: 예상하지 못한 regionId 무시
            }

            Map<WarningKind, WarningState> byKind =
                    pick.computeIfAbsent(regionId, k -> new HashMap<>());

            WarningState prev = byKind.get(ws.getKind());
            if (isAfter(ws, prev)) {
                byKind.put(ws.getKind(), ws);
            }
        }

        return pick;
    }

    /**
     * a 가 b 보다 더 “최신”인지 판정
     * - updatedAt이 더 늦으면 최신
     * - updatedAt 동일하면 warningId가 더 큰 쪽을 최신
     * - updatedAt 이 null 인 경우는 항상 뒤로 밀림
     */
    private boolean isAfter(WarningState a, WarningState b) {
        if (b == null) return true;   // 비교 대상이 없으면 a 가 최신

        LocalDateTime ia = a.getUpdatedAt();
        LocalDateTime ib = b.getUpdatedAt();

        if (ia == null && ib == null) {
            return a.getWarningId() > b.getWarningId();
        }
        if (ia == null) return false;
        if (ib == null) return true;   // null 이 아닌 쪽이 더 최신

        int cmp = ia.compareTo(ib);
        if (cmp != 0) return cmp > 0;

        return a.getWarningId() > b.getWarningId(); // 동일 시각일 때 더 최신 id 선택
    }

    /** Entity → DTO 변환 및 결과 채우기 */
    private void fillResult(
            Map<String, Map<WarningKind, WarningStateDto>> result,
            Map<String, Map<WarningKind, WarningState>> pickedEntities
    ) {
        for (var e : pickedEntities.entrySet()) {
            String regionId = e.getKey();
            Map<WarningKind, WarningState> entityByKind = e.getValue();

            Map<WarningKind, WarningStateDto> dtoByKind = convertEntityMapToDtoMap(entityByKind);
            result.put(regionId, dtoByKind);
        }
    }

    private Map<WarningKind, WarningStateDto> convertEntityMapToDtoMap(
            Map<WarningKind, WarningState> entityByKind
    ) {
        Map<WarningKind, WarningStateDto> dtoByKind = new LinkedHashMap<>();
        for (var entry : entityByKind.entrySet()) {
            WarningKind kind = entry.getKey();
            WarningState ws = entry.getValue();
            dtoByKind.put(kind, WarningStateDto.from(ws));
        }
        return dtoByKind;
    }

    /** since 이후 새로 발효/변경 판단 */
    public boolean isNewlyIssuedSince(WarningStateDto state, LocalDateTime since) {
        return state != null
                && state.getUpdatedAt() != null
                && state.getUpdatedAt().isAfter(since);
    }
}