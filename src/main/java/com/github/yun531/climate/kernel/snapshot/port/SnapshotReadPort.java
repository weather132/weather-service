package com.github.yun531.climate.kernel.snapshot.port;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.readmodel.Snapshot;
import org.springframework.lang.Nullable;

/** forecast/notification이 공통으로 쓰는 "스냅 읽기 계약" */
public interface SnapshotReadPort {

    /** 서버 내부 규칙의 의미 단위: CURRENT / PREVIOUS */
    @Nullable
    Snapshot load(String regionId, SnapKind kind);
}