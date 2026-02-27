package com.github.yun531.climate.kernel.warning.port;

import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;

import java.time.LocalDateTime;

public interface WarningIssuedJudgePort {

    /**
     * since 이후에 갱신(또는 발효)된 특보 상태인지 판단한다.
     * - state/since/updatedAt 중 하나라도 null이면 false
     */
    boolean isNewlyIssuedSince(WarningStateView state, LocalDateTime since);
}