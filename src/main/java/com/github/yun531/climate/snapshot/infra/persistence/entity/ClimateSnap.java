package com.github.yun531.climate.snapshot.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 필수
@Entity
@IdClass(ClimateSnapId.class)
@Table(name = "climate_snap")
public class ClimateSnap {

    @Id
    @Column(name = "snap_id")
    private Integer snapId;

    @Id
    @Column(name = "region_id", nullable = false, length = 8)
    private String regionId;

    @Column(name = "report_time", nullable = false)    // todo : db에 넣을 때 fommating 해서 문자열로 넣느게 나을수도 있음
    private LocalDateTime reportTime;

    @Column(name = "series_start_time", nullable = false)
    private LocalDateTime seriesStartTime;

    // ---- 시간대별 온도 (01~26시) ----
    @Column(name = "temp_A01") private Integer tempA01; @Column(name = "temp_A02") private Integer tempA02;
    @Column(name = "temp_A03") private Integer tempA03; @Column(name = "temp_A04") private Integer tempA04;
    @Column(name = "temp_A05") private Integer tempA05; @Column(name = "temp_A06") private Integer tempA06;
    @Column(name = "temp_A07") private Integer tempA07; @Column(name = "temp_A08") private Integer tempA08;
    @Column(name = "temp_A09") private Integer tempA09; @Column(name = "temp_A10") private Integer tempA10;
    @Column(name = "temp_A11") private Integer tempA11; @Column(name = "temp_A12") private Integer tempA12;
    @Column(name = "temp_A13") private Integer tempA13; @Column(name = "temp_A14") private Integer tempA14;
    @Column(name = "temp_A15") private Integer tempA15; @Column(name = "temp_A16") private Integer tempA16;
    @Column(name = "temp_A17") private Integer tempA17; @Column(name = "temp_A18") private Integer tempA18;
    @Column(name = "temp_A19") private Integer tempA19; @Column(name = "temp_A20") private Integer tempA20;
    @Column(name = "temp_A21") private Integer tempA21; @Column(name = "temp_A22") private Integer tempA22;
    @Column(name = "temp_A23") private Integer tempA23; @Column(name = "temp_A24") private Integer tempA24;
    @Column(name = "temp_A25") private Integer tempA25; @Column(name = "temp_A26") private Integer tempA26;

    // ---- 일자별 최저/최고 온도 ----
    @Column(name = "temp_A0d_min") private Integer tempA0dMin; @Column(name = "temp_A0d_max") private Integer tempA0dMax;
    @Column(name = "temp_A1d_min") private Integer tempA1dMin; @Column(name = "temp_A1d_max") private Integer tempA1dMax;
    @Column(name = "temp_A2d_min") private Integer tempA2dMin; @Column(name = "temp_A2d_max") private Integer tempA2dMax;
    @Column(name = "temp_A3d_min") private Integer tempA3dMin; @Column(name = "temp_A3d_max") private Integer tempA3dMax;
    @Column(name = "temp_A4d_min") private Integer tempA4dMin; @Column(name = "temp_A4d_max") private Integer tempA4dMax;
    @Column(name = "temp_A5d_min") private Integer tempA5dMin; @Column(name = "temp_A5d_max") private Integer tempA5dMax;
    @Column(name = "temp_A6d_min") private Integer tempA6dMin; @Column(name = "temp_A6d_max") private Integer tempA6dMax;

    // ---- 시간대별 강수확률 (01~26시) ----
    @Column(name = "POP_A01") private Integer popA01; @Column(name = "POP_A02") private Integer popA02;
    @Column(name = "POP_A03") private Integer popA03; @Column(name = "POP_A04") private Integer popA04;
    @Column(name = "POP_A05") private Integer popA05; @Column(name = "POP_A06") private Integer popA06;
    @Column(name = "POP_A07") private Integer popA07; @Column(name = "POP_A08") private Integer popA08;
    @Column(name = "POP_A09") private Integer popA09; @Column(name = "POP_A10") private Integer popA10;
    @Column(name = "POP_A11") private Integer popA11; @Column(name = "POP_A12") private Integer popA12;
    @Column(name = "POP_A13") private Integer popA13; @Column(name = "POP_A14") private Integer popA14;
    @Column(name = "POP_A15") private Integer popA15; @Column(name = "POP_A16") private Integer popA16;
    @Column(name = "POP_A17") private Integer popA17; @Column(name = "POP_A18") private Integer popA18;
    @Column(name = "POP_A19") private Integer popA19; @Column(name = "POP_A20") private Integer popA20;
    @Column(name = "POP_A21") private Integer popA21; @Column(name = "POP_A22") private Integer popA22;
    @Column(name = "POP_A23") private Integer popA23; @Column(name = "POP_A24") private Integer popA24;
    @Column(name = "POP_A25") private Integer popA25; @Column(name = "POP_A26") private Integer popA26;

    // ---- 일자별 오전/오후 강수확률 ----
    @Column(name = "POP_A0d_am") private Integer popA0dAm; @Column(name = "POP_A0d_pm") private Integer popA0dPm;
    @Column(name = "POP_A1d_am") private Integer popA1dAm; @Column(name = "POP_A1d_pm") private Integer popA1dPm;
    @Column(name = "POP_A2d_am") private Integer popA2dAm; @Column(name = "POP_A2d_pm") private Integer popA2dPm;
    @Column(name = "POP_A3d_am") private Integer popA3dAm; @Column(name = "POP_A3d_pm") private Integer popA3dPm;
    @Column(name = "POP_A4d_am") private Integer popA4dAm; @Column(name = "POP_A4d_pm") private Integer popA4dPm;
    @Column(name = "POP_A5d_am") private Integer popA5dAm; @Column(name = "POP_A5d_pm") private Integer popA5dPm;
    @Column(name = "POP_A6d_am") private Integer popA6dAm; @Column(name = "POP_A6d_pm") private Integer popA6dPm;
}