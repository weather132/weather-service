package com.github.yun531.climate.service;

import com.github.yun531.climate.domain.PopDailySeries7;
import com.github.yun531.climate.domain.PopSeries24;
import com.github.yun531.climate.dto.POPSnapDto;
import com.github.yun531.climate.repository.ClimateSnapRepository;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClimateService {

    /** todo: 디폴트 스냅샷 id 변화에 대응 (예: 1=현재, 10=이전) */
    private static final long SNAP_CURRENT_DEFAULT = 1L;
    private static final long SNAP_PREV_DEFAULT    = 10L;

    private final ClimateSnapRepository climateSnapRepository;

    public PopSeries loadDefaultPopSeries(Long regionId) {
        return loadPopSeries(regionId, SNAP_CURRENT_DEFAULT, SNAP_PREV_DEFAULT);
    }
    public ForecastSeries loadDefaultForecastSeries(Long regionId) {
        return loadForecastSeries(regionId, SNAP_CURRENT_DEFAULT);
    }

    /** 비(POP) 판정에 필요한 시계열을 로드 (현재*이전 스냅샷) */
    public PopSeries loadPopSeries(Long regionId, Long currentSnapId, Long previousSnapId) {
        List<Long> ids = List.of(currentSnapId, previousSnapId);
        List<POPSnapDto> snaps =
                climateSnapRepository.findPopInfoBySnapIdsAndRegionId(ids, regionId);

        POPSnapDto cur = null;
        POPSnapDto prv = null;
        for (POPSnapDto s : snaps) {
            if (s.getSnapId() == currentSnapId) {
                cur = s;
            } else if (s.getSnapId() == previousSnapId) {
                prv = s;
            }
        }

        if (cur == null || prv == null) {
            return new PopSeries(null, null, 0);
        }

        LocalDateTime curReportTime = cur.getReportTime();
        LocalDateTime prvReportTime = prv.getReportTime();

        long minutes = Duration.between(prvReportTime, curReportTime).toMinutes();
        int reportTimeGap = (int) Math.round(minutes / 60.0);

        return new PopSeries(cur.getHourly(), prv.getHourly(), reportTimeGap);
    }

    /** 예보 요약용: 스냅에서 시간대 [24] + 오전/오후[14] */
    public ForecastSeries loadForecastSeries(Long regionId, Long snapId) {
        List<POPSnapDto> rows =
                climateSnapRepository.findPopInfoBySnapIdsAndRegionId(List.of(snapId), regionId);

        POPSnapDto dto = rows.isEmpty() ? null : rows.get(0);
        if (dto == null) {
            return new ForecastSeries(null, null);
        }

        return new ForecastSeries(dto.getHourly(), dto.getDaily());
    }


    /** 판정용 입력 구조체 (현재 PopSeries24, 이전 PopSeries24) */
    public record PopSeries(@Nullable PopSeries24 current,
                            @Nullable PopSeries24 previous,
                            int reportTimeGap) {}

    /** 예보 요약용 구조체 (시간대 PopSeries24, 일자별 PopDailySeries7) */
    public record ForecastSeries(@Nullable PopSeries24 hourly,
                                 @Nullable PopDailySeries7 daily) {}
}