package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.POPSnapDto;
import com.github.yun531.climate.repository.ClimateSnapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClimateService {

    private final ClimateSnapRepository climateSnapRepository;

    /** todo: 디폴트 스냅샷 id 변화에 대응 (예: 1=현재, 10=이전) */
    public PopSeries loadDefaultPopSeries(Long regionId) {
        return loadPopSeries(regionId, 1L, 10L);
    }
    public ForecastSeries loadDefaultForecastSeries(Long regionId) {
        return loadForecastSeries(regionId, 1L); // 필요 시 @Value로 주입
    }

    /** 비(POP) 판정에 필요한 시계열을 로드 (현재, 이전스냅샷-좌측1칸시프트) */
    public PopSeries loadPopSeries(Long regionId, Long currentSnapId, Long previousSnapId) {
        List<Long> ids = List.of(currentSnapId, previousSnapId);
        List<POPSnapDto> snaps = climateSnapRepository.findPopInfoBySnapIdsAndRegionId(ids, regionId);

        POPSnapDto cur = null, prv = null;
        for (POPSnapDto s : snaps) {
            if (s.getSnapId() == currentSnapId) cur = s;
            else if (s.getSnapId() == previousSnapId) prv = s;
        }
        if (cur == null || prv == null) return new PopSeries(null, null);

        int[] curArr = com.github.yun531.climate.support.PopArrays.hourly24(cur);
        int[] prvArr = com.github.yun531.climate.support.PopArrays.hourly24(prv);
        int[] prvShift = com.github.yun531.climate.support.PopArrays.shiftLeftBy1ToLen23(prvArr);
        return new PopSeries(curArr, prvShift);
    }

    /** 예보 요약용: 현재 스냅에서 시간대 24 + 오전/오후[7][2] */
    public ForecastSeries loadForecastSeries(Long regionId, Long snapId) {
        List<POPSnapDto> rows = climateSnapRepository
                .findPopInfoBySnapIdsAndRegionId(List.of(snapId), regionId);
        POPSnapDto dto = rows.isEmpty() ? null : rows.get(0);
        if (dto == null) return new ForecastSeries(null, null);

        int[] hourly24 = com.github.yun531.climate.support.PopArrays.hourly24(dto);
        Byte[][] ampm7x2 = com.github.yun531.climate.support.PopArrays.ampm7x2(dto);
        return new ForecastSeries(hourly24, ampm7x2);
    }


    /** 판정용 입력 구조체 (현재 POP[24], 이전스냅 시프트 POP[23]) */
    public record PopSeries(@org.springframework.lang.Nullable int[] current,
                            @org.springframework.lang.Nullable int[] previousShifted) {}
    /** 예보 요약용 구조체 (현재 POP[24], D+0~6 오전/오후 POP[7][2]) */
    public record ForecastSeries(@org.springframework.lang.Nullable int[] hourly24,
                                 @org.springframework.lang.Nullable Byte[][] ampm7x2) {}
}