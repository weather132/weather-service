package com.github.yun531.climate.warning.infra.adapter;

import com.github.yun531.climate.warning.domain.policy.WarningIssuedPolicy;
import com.github.yun531.climate.kernel.warning.port.WarningIssuedJudgePort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class WarningIssuedJudgeAdapter implements WarningIssuedJudgePort {

    private final WarningIssuedPolicy policy;

    @Override
    public boolean isNewlyIssuedSince(WarningStateView state, LocalDateTime since) {
        return policy.isNewlyIssuedSince(state, since);
    }
}