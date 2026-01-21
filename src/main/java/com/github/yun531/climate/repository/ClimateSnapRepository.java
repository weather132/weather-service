package com.github.yun531.climate.repository;

import com.github.yun531.climate.repository.dto.POPSnapDto;
import com.github.yun531.climate.entity.ClimateSnap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClimateSnapRepository extends JpaRepository<ClimateSnap, Integer> {

    // snap_id로 특정 데이터 조회
    List<ClimateSnap> findBySnapIdIn(List<Integer> snapIds);

    // region_id로 조회
    List<ClimateSnap> findByRegionId(String regionId);

    // snap_id와 region_id를 함께 조건으로 조회
    ClimateSnap findBySnapIdAndRegionId(Integer snapId, String regionId);

    @Query("""
        SELECT new com.github.yun531.climate.repository.dto.POPSnapDto(
            c.snapId, c.regionId, c.reportTime,

            c.validAtA01, c.validAtA02, c.validAtA03, c.validAtA04, c.validAtA05,
            c.validAtA06, c.validAtA07, c.validAtA08, c.validAtA09, c.validAtA10,
            c.validAtA11, c.validAtA12, c.validAtA13, c.validAtA14, c.validAtA15,
            c.validAtA16, c.validAtA17, c.validAtA18, c.validAtA19, c.validAtA20,
            c.validAtA21, c.validAtA22, c.validAtA23, c.validAtA24, c.validAtA25, c.validAtA26,

            c.popA01, c.popA02, c.popA03, c.popA04, c.popA05,
            c.popA06, c.popA07, c.popA08, c.popA09, c.popA10,
            c.popA11, c.popA12, c.popA13, c.popA14, c.popA15,
            c.popA16, c.popA17, c.popA18, c.popA19, c.popA20,
            c.popA21, c.popA22, c.popA23, c.popA24, c.popA25, c.popA26,

            c.popA0dAm, c.popA0dPm,
            c.popA1dAm, c.popA1dPm,
            c.popA2dAm, c.popA2dPm,
            c.popA3dAm, c.popA3dPm,
            c.popA4dAm, c.popA4dPm,
            c.popA5dAm, c.popA5dPm,
            c.popA6dAm, c.popA6dPm
        )
        FROM ClimateSnap c
        WHERE c.snapId IN :snapIds and c.regionId = :regionId
    """)
    List<POPSnapDto> findPopInfoBySnapIdsAndRegionId(
            @Param("snapIds") List<Integer> snapIds,
            @Param("regionId") String regionId
    );
}