package com.github.yun531.climate.util.http;

import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class UrlQueryUtil {

    private UrlQueryUtil() {}

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static String formatIso(LocalDateTime t) {
        return (t == null) ? null : ISO_LOCAL.format(t);
    }

    public static String buildUri(String path, Map<String, ?> params) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        if (params != null) {
            params.forEach((k, v) -> {
                if (v != null) b.queryParam(k, v);
            });
        }
        return b.build(true).toUriString();
    }
}