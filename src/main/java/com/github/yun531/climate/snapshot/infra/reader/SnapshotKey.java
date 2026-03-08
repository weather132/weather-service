package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;

/**
 * Snapshot 식별을 위한 값 객체.
 * - (regionId, kind)로부터
 *   1) DB snap_id 코드(int)
 *   2) 캐시 키(String: "regionId:code")
 *   를 일관된 규칙으로 제공한다.
 */
public record SnapshotKey(String regionId, SnapKind kind) {

    private static final String SEP = ":";

    public SnapshotKey {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("regionId must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }

    /** DB (또는 내부 코드)에서 사용하는 snap_id */
    public int asSnapId() {
        return toCode(kind);
    }

    /** 캐시 키는 안정적으로 "regionId:code" 형태로 고정 */
    public String asCacheKey() {
        return regionId + SEP + asSnapId();
    }

    public static SnapshotKey of(String regionId, SnapKind kind) {
        return new SnapshotKey(regionId, kind);
    }

    /** SnapKind -> 코드 변환 규칙 */
    private static int toCode(SnapKind kind) {
        return switch (kind) {
            case CURRENT -> 1;
            case PREVIOUS -> 10;
        };
    }
}