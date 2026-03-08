package com.github.yun531.climate.forecast.infra;

import com.github.yun531.climate.forecast.domain.reader.ForecastViewReader;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import com.github.yun531.climate.snapshot.domain.reader.SnapshotReader;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ForecastViewReader 구현체.
 * - SnapshotReader 에서 데이터를 로드하고, ForecastViewMapper로 forecast 자체 타입으로 변환.
 */
@Component
@RequiredArgsConstructor
public class SnapshotForecastViewReader implements ForecastViewReader {

    private final SnapshotReader snapshotReader;
    private final ForecastViewMapper mapper;

    /** 시간대별 온도+POP 예보  */
    @Override
    public ForecastHourlyView loadHourly(String regionId) {
        WeatherSnapshot snap = snapshotReader.loadCurrent(regionId);
        return mapper.toHourlyView(snap);
    }

    /** 일자별 am/pm 온도+POP 예보 */
    @Override
    public ForecastDailyView loadDaily(String regionId) {
        WeatherSnapshot snap = snapshotReader.loadCurrent(regionId);
        return mapper.toDailyView(snap);
    }
}