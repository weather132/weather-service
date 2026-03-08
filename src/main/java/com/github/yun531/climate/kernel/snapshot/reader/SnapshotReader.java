package com.github.yun531.climate.kernel.snapshot.reader;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.readmodel.WeatherSnapshot;
import org.springframework.lang.Nullable;

/** forecast/notification이 공통으로 쓰는 "스냅 읽기 계약" */
public interface SnapshotReader {

    /** 서버 내부 규칙의 의미 단위: CURRENT / PREVIOUS */
    @Nullable
    WeatherSnapshot load(String regionId, SnapKind kind); // todo: 외부에ㅓ snapkind 몰라도 사용할 수 있또록 함수 분리
}