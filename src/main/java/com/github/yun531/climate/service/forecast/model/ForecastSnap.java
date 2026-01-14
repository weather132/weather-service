package com.github.yun531.climate.service.forecast.model;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 예보 스냅샷 (시간대 온도/POP + 일자별 AM/PM 온도/POP)
 * DB / 외부 서버에서 가져온 스냅을 도메인에서 다루기 좋은 형태로 표현
 */
public record ForecastSnap(
        String regionId,
        LocalDateTime reportTime,

        // 시간대별 예보 (1~26시간 후)
        List<HourlyPoint> hourly,
        // 일자별 am/pm 예보 (0~6일차)
        List<DailyPoint> daily
) { }