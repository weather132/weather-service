package com.github.yun531.climate.snapshot.infra.persistence.repository;

import com.github.yun531.climate.snapshot.infra.persistence.entity.SnapshotEntity;
import com.github.yun531.climate.snapshot.infra.persistence.entity.SnapshotEntityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<SnapshotEntity, SnapshotEntityId> {
    // snap_id + region_id
    SnapshotEntity findBySnapIdAndRegionId(Integer snapId, String regionId);
}