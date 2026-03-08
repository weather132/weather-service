package com.github.yun531.climate.forecast.domain.reader;

import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import org.springframework.lang.Nullable;

public interface ForecastViewReader {

    @Nullable
    ForecastHourlyView loadHourly(String regionId);

    @Nullable
    ForecastDailyView loadDaily(String regionId);
}