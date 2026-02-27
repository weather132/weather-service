package com.github.yun531.climate.warning.domain.policy;

import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class WarningIssuedPolicy {

    /**
     * since 이후에 갱신(또는 발효)된 특보 상태인지 판단한다.
     * - state 또는 since가 null이면 false
     * - updatedAt이 null이면 false
     */
    public boolean isNewlyIssuedSince(WarningStateView state, LocalDateTime since) {
        return state != null
                && state.updatedAt() != null
                && since != null
                && state.updatedAt().isAfter(since);
    }
}