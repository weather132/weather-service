package com.github.yun531.climate.warning.infra.adapter;

import com.github.yun531.climate.warning.infra.remote.warningapi.api.WarningApiClient;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.kernel.warning.model.WarningKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * warningapi 전환용 게이트웨이(구현 틀).
 * - 지금은 외부 호출 로직을 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApiWarningStateReadAdapter implements WarningStateReadPort {

    private final WarningApiClient client; // 지금은 사용하지 않지만, 전환 준비용으로 주입 유지

    @Override
    public Map<WarningKind, WarningStateView> loadLatestByKind(String regionId) {
        // TODO warningapi 구현 후:
        // - client.fetchLatest(regionId)
        // - items -> kind별 최신 1개 선택
        // - WarningStateView로 변환 후 반환
        return Map.of();
    }
}