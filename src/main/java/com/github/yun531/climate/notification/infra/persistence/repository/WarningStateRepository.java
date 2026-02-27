package com.github.yun531.climate.notification.infra.persistence.repository;

import com.github.yun531.climate.notification.infra.persistence.entity.WarningState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WarningStateRepository extends JpaRepository<WarningState, Integer> {

    List<WarningState> findByRegionIdIn(Collection<String> regionIds);
}