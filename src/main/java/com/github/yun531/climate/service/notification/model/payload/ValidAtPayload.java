package com.github.yun531.climate.service.notification.model.payload;

import java.time.LocalDateTime;

/** validAt 기반 이벤트(윈도우 필터링/정렬 등) 공통 계약 */
public interface ValidAtPayload {
    LocalDateTime validAt();
}