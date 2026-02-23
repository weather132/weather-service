package com.github.yun531.climate.infrastructure.snapshot.store;

import com.github.yun531.climate.shared.snapshot.SnapKind;

public record SnapKey(String regionId, SnapKind kind) {

    public SnapKey {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("regionId must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }

    /** 캐시 키는 안정적으로 (regionId + ":" + code) 형태로 고정 */
    public String asCacheKey() {
        return regionId + ":" + SnapKindCodec.toCode(kind);
    }

    public static SnapKey of(String regionId, SnapKind kind) {
        return new SnapKey(regionId, kind);
    }
}