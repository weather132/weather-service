package com.github.yun531.climate.service.forecast;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 일반 일기예보 앱에서 사용할 예보 조회 서비스.
 * - ClimateService가 생성한 예보 DTO를 기반으로
 *   - 시간대별 예보: now 기준으로 hourOffset 보정
 *   - 일자별 예보: 그대로 반환
 */
@Service
@RequiredArgsConstructor
public class AppForecastService {

    private final SnapshotQueryService snapshotQueryService;

    /**
     * 시간대별 예보 조회.
     * - ClimateService.getHourlyForecast(regionId)로 스냅샷 기준 예보를 가져온 뒤
     * - 현재 시각(now)과 reportTime 차이만큼 hourOffset을 앞으로 당겨서 반환.
     */
    public HourlyForecastDto getHourlyForecast(String regionId) {
        HourlyForecastDto base = snapshotQueryService.getHourlyForecast(regionId);
        if (base == null) {
            return null;
        }
        return adjustHourlyOffsets(base, LocalDateTime.now());
    }

    /** 일자별 예보 조회. */
    public DailyForecastDto getDailyForecast(String regionId) {
        return snapshotQueryService.getDailyForecast(regionId);
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
            return base;
        }

        // todo: diffHours 사용해서 reportedTime 가공 및 저장
        // 스냅샷은 3시간마다 갱신되므로, 최대 2시간까지만 보정
        int diffHours = Math.min(rawDiffHours, 2);

        List<HourlyPoint> trimmed =
                base.hours().stream()
                        .sorted(Comparator.comparingInt(HourlyPoint::hourOffset))
                        .skip(diffHours)   // diffHours 만큼 앞 요소 제거
                        .limit(24)         // 최대 24개
                        .toList();

        // offset을 1~24로 재부여
        List<HourlyPoint> reindexed =
                IntStream.range(0, trimmed.size())
                        .mapToObj(i -> {
                            HourlyPoint p = trimmed.get(i);
                            return new HourlyPoint(
                                    i + 1,      // 1부터 시작
                                    p.temp(),
                                    p.pop()
                            );
                        })
                        .toList();

        return new HourlyForecastDto(
                base.regionId(),
                base.reportTime(),
                reindexed
        );
    }
}