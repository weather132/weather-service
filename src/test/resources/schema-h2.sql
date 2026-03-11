-- =========================================================
-- climate_snap
-- =========================================================
CREATE TABLE climate_snap (
    snap_id           INT          NOT NULL,
    region_id         CHAR(8)      NOT NULL,
    report_time       TIMESTAMP    NOT NULL,
    series_start_time TIMESTAMP    NOT NULL,

    -- 시간대별 온도 (01~26)
    temp_A01 SMALLINT, temp_A02 SMALLINT, temp_A03 SMALLINT, temp_A04 SMALLINT, temp_A05 SMALLINT,
    temp_A06 SMALLINT, temp_A07 SMALLINT, temp_A08 SMALLINT, temp_A09 SMALLINT, temp_A10 SMALLINT,
    temp_A11 SMALLINT, temp_A12 SMALLINT, temp_A13 SMALLINT, temp_A14 SMALLINT, temp_A15 SMALLINT,
    temp_A16 SMALLINT, temp_A17 SMALLINT, temp_A18 SMALLINT, temp_A19 SMALLINT, temp_A20 SMALLINT,
    temp_A21 SMALLINT, temp_A22 SMALLINT, temp_A23 SMALLINT, temp_A24 SMALLINT, temp_A25 SMALLINT,
    temp_A26 SMALLINT,

    -- 일자별 최저/최고 온도
    temp_A0d_min SMALLINT, temp_A0d_max SMALLINT,
    temp_A1d_min SMALLINT, temp_A1d_max SMALLINT,
    temp_A2d_min SMALLINT, temp_A2d_max SMALLINT,
    temp_A3d_min SMALLINT, temp_A3d_max SMALLINT,
    temp_A4d_min SMALLINT, temp_A4d_max SMALLINT,
    temp_A5d_min SMALLINT, temp_A5d_max SMALLINT,
    temp_A6d_min SMALLINT, temp_A6d_max SMALLINT,

    -- 시간대별 강수확률 (01~26)
    POP_A01 SMALLINT, POP_A02 SMALLINT, POP_A03 SMALLINT, POP_A04 SMALLINT, POP_A05 SMALLINT,
    POP_A06 SMALLINT, POP_A07 SMALLINT, POP_A08 SMALLINT, POP_A09 SMALLINT, POP_A10 SMALLINT,
    POP_A11 SMALLINT, POP_A12 SMALLINT, POP_A13 SMALLINT, POP_A14 SMALLINT, POP_A15 SMALLINT,
    POP_A16 SMALLINT, POP_A17 SMALLINT, POP_A18 SMALLINT, POP_A19 SMALLINT, POP_A20 SMALLINT,
    POP_A21 SMALLINT, POP_A22 SMALLINT, POP_A23 SMALLINT, POP_A24 SMALLINT, POP_A25 SMALLINT,
    POP_A26 SMALLINT,

    -- 일자별 오전/오후 강수확률
    POP_A0d_am SMALLINT, POP_A0d_pm SMALLINT,
    POP_A1d_am SMALLINT, POP_A1d_pm SMALLINT,
    POP_A2d_am SMALLINT, POP_A2d_pm SMALLINT,
    POP_A3d_am SMALLINT, POP_A3d_pm SMALLINT,
    POP_A4d_am SMALLINT, POP_A4d_pm SMALLINT,
    POP_A5d_am SMALLINT, POP_A5d_pm SMALLINT,
    POP_A6d_am SMALLINT, POP_A6d_pm SMALLINT,

    PRIMARY KEY (snap_id, region_id)
);

CREATE INDEX idx_region_report ON climate_snap (region_id, report_time);


-- =========================================================
-- warning_state
-- =========================================================
CREATE TABLE warning_state (
    warning_id   INT          AUTO_INCREMENT PRIMARY KEY,
    region_id    CHAR(8)      NOT NULL,
    kind         VARCHAR(16),
    level        VARCHAR(16),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
