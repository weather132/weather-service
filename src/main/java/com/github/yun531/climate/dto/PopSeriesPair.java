package com.github.yun531.climate.dto;

import io.micrometer.common.lang.Nullable;
import java.time.LocalDateTime;

/** 판정용 입력 구조체  */
public record PopSeriesPair(
        @Nullable PopSeries24 current,
        @Nullable PopSeries24 previous,
        int reportTimeGap,
        LocalDateTime curReportTime
) {}
