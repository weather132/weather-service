package com.github.yun531.climate.controller.internal.dto;

public record FcmTestRequest(
        String topic,
        String type,
        String triggerAt,
        Integer hour,
        boolean dryRun
) {}