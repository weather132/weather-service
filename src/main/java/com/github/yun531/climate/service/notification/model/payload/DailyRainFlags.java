package com.github.yun531.climate.service.notification.model.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyRainFlags(boolean rainAm, boolean rainPm) {}