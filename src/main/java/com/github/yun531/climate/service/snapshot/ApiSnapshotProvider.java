package com.github.yun531.climate.service.snapshot;

import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * API 기반 SnapshotProvider 구현체.
 * climate_snap 테이블의 ClimateSnap 엔티티를 ForecastSnapshot으로 변환하고, //todo: 설명 update
 * regionId + snapId 기준으로 스냅샷을 캐시한다.
 */
@Component
@RequiredArgsConstructor
public class ApiSnapshotProvider implements SnapshotProvider {

    //todo: implement api snapshot provider
    @Override
    @Nullable
    public ForecastSnap loadSnapshot(String regionId, int snapId){
        return null;
    }
}
