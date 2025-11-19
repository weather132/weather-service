package com.github.yun531.climate.repository;

import com.github.yun531.climate.entity.WarningState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarningStateRepository extends JpaRepository<WarningState, Integer> {

    List<WarningState> findByRegionIdIn(Collection<Integer> regionIds);

    Optional<WarningState> findTopByRegionIdOrderByUpdatedAtDesc(int regionId);
}