package com.github.yun531.climate.infrastructure.persistence.repository;

import com.github.yun531.climate.infrastructure.persistence.entity.ClimateSnap;
import com.github.yun531.climate.infrastructure.persistence.entity.ClimateSnapId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClimateSnapRepository extends JpaRepository<ClimateSnap, ClimateSnapId> {

    // snap_id로 특정 데이터 조회 (여러 region 포함될 수 있음)
    List<ClimateSnap> findBySnapIdIn(List<Integer> snapIds);

    // region_id로 조회
    List<ClimateSnap> findByRegionId(String regionId);

    // snap_id + region_id
    ClimateSnap findBySnapIdAndRegionId(Integer snapId, String regionId);
}