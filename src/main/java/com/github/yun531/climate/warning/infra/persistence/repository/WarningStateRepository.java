package com.github.yun531.climate.warning.infra.persistence.repository;

import com.github.yun531.climate.warning.infra.persistence.entity.WarningStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WarningStateRepository extends JpaRepository<WarningStateEntity, Integer> {

    List<WarningStateEntity> findByRegionIdIn(Collection<String> regionIds);
}