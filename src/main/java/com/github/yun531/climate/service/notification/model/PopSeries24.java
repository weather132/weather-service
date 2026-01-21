package com.github.yun531.climate.service.notification.model;

import java.time.LocalDateTime;
import java.util.List;

public record PopSeries24(List<Point> points) {

    public static final int SIZE = 26; // (A01~A26)

    public record Point(
            LocalDateTime validAt,
            int pop
    ) {}

    // compact constructor: 불변 보장 + 유효성 검사
    public PopSeries24 {
        points = List.copyOf(points);
        if (points.size() != SIZE) {
            throw new IllegalArgumentException("PopSeries24 must have " + SIZE + " points.");
        }
    }

    /** 1~26시간 후 POP 조회 (기존 시그니처 유지) */
    public int get(int offsetHour) {
        return points.get(offsetHour - 1).pop();
    }

    /** 1~26시간 후 validAt 조회 */
    public LocalDateTime validAt(int offsetHour) {
        return points.get(offsetHour - 1).validAt();
    }

    public int size() {
        return points.size();
    }
}