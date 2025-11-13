package com.github.yun531.climate.service.rule;

import java.time.Instant;
import java.util.List;

public interface AlertRule {
    /** @return 해당 룰이 지원하는 AlertTypeEnum */
    AlertTypeEnum supports();

    /**
     * @param regionIds 대상 지역들
     * @param since     이 시각 이후 발생한 것만 (null 허용)
     * @return AlertEvent 리스트 (문자열은 후처리에서 변환)
     */
    List<AlertEvent> evaluate(List<Long> regionIds, Instant since);  //todo Instant || localdatetime
}
