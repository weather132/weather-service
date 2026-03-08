package com.github.yun531.climate.snapshot.domain.reader;

import com.github.yun531.climate.snapshot.domain.model.SnapKind;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.springframework.lang.Nullable;

/**
 * 스냅샷 읽기 계약.
 * - 외부 소비자는 SnapKind를 알 필요 없이 의미 단위 메서드로 호출한다.
 * - 내부 구현(JpaSnapshotReader, ApiSnapshotReader)이 SnapKind 매핑을 담당한다.
 */
public interface SnapshotReader {

    @Nullable
    WeatherSnapshot loadCurrent(String regionId);

    @Nullable
    WeatherSnapshot loadPrevious(String regionId);
}