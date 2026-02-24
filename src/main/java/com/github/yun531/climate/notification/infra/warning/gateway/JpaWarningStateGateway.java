package com.github.yun531.climate.notification.infra.warning.gateway;

import com.github.yun531.climate.notification.domain.model.WarningKind;
import com.github.yun531.climate.notification.domain.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.readmodel.WarningStateView;
import com.github.yun531.climate.notification.infra.persistence.repository.WarningStateRepository;
import com.github.yun531.climate.notification.infra.warning.assembler.WarningStateViewAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Primary      //todo 현재 기본은 JPA, warningapi 전환 시 ApiWarningStateGateway로 @Primary 이동
@RequiredArgsConstructor
public class JpaWarningStateGateway implements WarningStateReadPort {

    private final WarningStateRepository repo;

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        if (regionId == null || regionId.isBlank()) return Map.of();

        var rows = repo.findByRegionIdIn(List.of(regionId));
        return WarningStateViewAssembler.pickLatestByKind(regionId, rows);
    }
}