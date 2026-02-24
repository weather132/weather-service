package com.github.yun531.climate.notification.domain.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyRainFlags(boolean rainAm, boolean rainPm) {}