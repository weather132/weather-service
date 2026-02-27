package com.github.yun531.climate.fcm.internal.dto;

public record FcmTestResponse(
        boolean ok,
        String topic,
        boolean dryRun,
        String messageId,
        String error
) {
    public static FcmTestResponse success(String topic, boolean dryRun, String messageId) {
        return new FcmTestResponse(true, topic, dryRun, messageId, null);
    }

    public static FcmTestResponse fail(String topic, boolean dryRun, String error) {
        return new FcmTestResponse(false, topic, dryRun, null, error);
    }
}