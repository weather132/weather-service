package com.github.yun531.climate.warning.infra.adapter;

import com.github.yun531.climate.warning.infra.persistence.repository.WarningStateRepository;
import com.github.yun531.climate.warning.infra.mapper.WarningStateViewMapper;
import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Primary      //todo 현재 기본은 JPA, warningapi 전환 시 ApiWarningStateGateway로 @Primary 이동
@RequiredArgsConstructor
public class JpaWarningStateReadAdapter implements WarningStateReadPort {

    private final WarningStateRepository repo;

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        if (regionId == null || regionId.isBlank()) return Map.of();

        var rows = repo.findByRegionIdIn(List.of(regionId));
        return WarningStateViewMapper.pickLatestByKind(regionId, rows);
    }
}