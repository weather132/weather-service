package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import org.springframework.lang.Nullable;

/**
 * 특정 regionId + snapId 조합에 대한 예보 스냅샷을 제공하는 포트.
 * 구현체는 JPA, 원격 API 등 어떤 방식으로든 ForecastSnapshot을 만들어서 반환.
 */
public interface SnapshotProvider {
    /**
     * @param regionId 지역 ID
     * @param snapId   SNAP_CURRENT(1), SNAP_PREVIOUS(10) 등 스냅 구분 코드
     * @return ForecastSnapshot 또는 존재하지 않으면 null
     */
    @Nullable
    ForecastSnap loadSnapshot(String regionId, int snapId);
}
