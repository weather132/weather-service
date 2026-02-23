package com.github.yun531.climate.infrastructure.snapshot.store;

import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.shared.snapshot.SnapKind;
import org.springframework.lang.Nullable;

public interface SnapshotStore {

    /** 서버 내부 규칙의 의미 단위: CURRENT / PREVIOUS */
    @Nullable
    ForecastSnap load(String regionId, SnapKind kind);

    /**
     * 호환/확장용: snapId로 직접 로드.
     * - 1/10은 kind로 매핑해서 load(...)로 위임
     * - 그 외 snapId는 구현체가 처리
     */
    default @Nullable ForecastSnap loadBySnapId(String regionId, int snapId) {
        SnapKind kind = SnapKindCodec.fromCode(snapId);
        return (kind == null) ? null : load(regionId, kind);
    }
}