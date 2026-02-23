package com.github.yun531.climate.infrastructure.warning.gateway;

import com.github.yun531.climate.infrastructure.remote.warningapi.api.WarningApiClient;
import com.github.yun531.climate.notification.domain.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.readmodel.WarningStateView;
import com.github.yun531.climate.service.notification.model.WarningKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * warningapi 전환용 게이트웨이(구현 틀).
 * - 지금은 외부 호출 로직을 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApiWarningStateGateway implements WarningStateReadPort {

    private final WarningApiClient client; // 지금은 사용하지 않지만, 전환 준비용으로 주입 유지

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        // TODO warningapi 구현 후:
        // - client.fetchLatest(regionId)
        // - items -> kind별 최신 1개 선택
        // - WarningStateView로 변환 후 반환
        return Map.of();
    }

    @Override
    public boolean isNewlyIssuedSince(WarningStateView state, LocalDateTime since) {
        return state != null
                && state.updatedAt() != null
                && since != null
                && state.updatedAt().isAfter(since);
    }
}