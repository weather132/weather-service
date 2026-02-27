package com.github.yun531.climate.snapshot.infra.persistence.repository;

import com.github.yun531.climate.snapshot.infra.persistence.entity.ClimateSnap;
import com.github.yun531.climate.snapshot.infra.persistence.entity.ClimateSnapId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClimateSnapRepository extends JpaRepository<ClimateSnap, ClimateSnapId> {
    // snap_id + region_id
    ClimateSnap findBySnapIdAndRegionId(Integer snapId, String regionId);
}