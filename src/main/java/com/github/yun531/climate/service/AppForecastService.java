package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일반 일기예보 앱에서 사용할 예보 조회 서비스.
 * - ClimateService가 생성한 예보 DTO를 기반으로
 *   - 시간대별 예보: now 기준으로 hourOffset 보정
 *   - 일자별 예보: 그대로 반환
 */
@Service
@RequiredArgsConstructor
public class AppForecastService {

    private final ClimateService climateService;

    /**
     * 시간대별 예보 조회.
     * - ClimateService.getHourlyForecast(regionId)로 스냅샷 기준 예보를 가져온 뒤
     * - 현재 시각(now)과 reportTime 차이만큼 hourOffset을 앞으로 당겨서 반환.
     */
    public HourlyForecastDto getHourlyForecast(int regionId) {
        HourlyForecastDto base = climateService.getHourlyForecast(regionId);
        if (base == null) {
            return null;
        }
        return adjustHourlyOffsets(base, LocalDateTime.now());
    }

    /** 일자별 예보 조회. */
    public DailyForecastDto getDailyForecast(int regionId) {
        return climateService.getDailyForecast(regionId);
    }

    /**
     * HourlyForecastDto의 hourOffset을 현재 시각 기준으로 보정.
     * - 스냅샷이 3시간마다 갱신된다는 가정 하에 diffHours는 최대 2로 클램핑
     */
    private HourlyForecastDto adjustHourlyOffsets(HourlyForecastDto base, LocalDateTime now) {
        if (base.reportTime() == null) {
            return base;
        }

        long diffMinutes = Duration.between(base.reportTime(), now).toMinutes();
        int rawDiffHours = (int) (diffMinutes / 60);

        if (rawDiffHours <= 0) {
            // 아직 발표 직후이거나 과거 기준이면 그대로 사용
            return base;
        }

        // 스냅샷은 3시간마다 갱신되므로, 최대 2시간까지만 보정
        int diffHours = Math.min(rawDiffHours, 2);

        List<HourlyForecastDto.HourlyForecastEntry> shifted =
                base.hours().stream()
                        .map(e -> new HourlyForecastDto.HourlyForecastEntry(
                                e.hourOffset() - diffHours,
                                e.temp(),
                                e.pop()
                        ))
                        .filter(e -> e.hourOffset() >= 0)
                        .toList();

        return new HourlyForecastDto(
                base.regionId(),
                base.reportTime(),
                shifted
        );
    }
}