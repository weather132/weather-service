package com.github.yun531.climate.service.notification.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class PopSeries24 {
    public static final int SIZE = 26;   //(A01~A26)

    public record Point(
            LocalDateTime validAt,
            int pop
    ) {}

    private final List<Point> points;


    public PopSeries24(List<Point> points) {
        this.points = List.copyOf(points);
        if (this.points.size() != SIZE) {
            throw new IllegalArgumentException("PopSeries24 must have " + SIZE + " points.");
        }
    }

    /** 1~26시간 후 POP 조회 (기존 시그니처 유지) */
    public int get(int offsetHour) {
        return points.get(offsetHour - 1).pop();
    }

    /** 1~26시간 후 validAt 조회 (신규) */
    public LocalDateTime validAt(int offsetHour) {
        return points.get(offsetHour - 1).validAt();
    }

    /** 내부 index 기반 validAt (신규) */
    public LocalDateTime validAtByIndex(int index0to25) {
        return points.get(index0to25).validAt();
    }

    public int size() { return points.size(); }
}