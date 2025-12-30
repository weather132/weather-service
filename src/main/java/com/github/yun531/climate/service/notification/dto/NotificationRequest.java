package com.github.yun531.climate.service.notification.dto;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * NotificationService 에서 사용하는 알림 생성 요청 정보.
 * - regionIds        : 대상 지역 ID 목록
 * - since            : 기준 시각 (null 이면 서비스에서 now 로 대체)
 * - enabledTypes     : 활성화할 AlertTypeEnum 집합 (null/empty 이면 서비스에서 기본값 적용)
 * - filterWarningKinds      : WARNING_ISSUED 에서 필터링할 WarningKind 셋 (옵션)
 * - rainHourLimit    : RAIN_ONSET 에서 hour <= 이 값인 이벤트까지만 포함 (1~24, null 이면 전체)
 */
public record NotificationRequest(
        List<String> regionIds,
        @Nullable LocalDateTime since,
        @Nullable Set<AlertTypeEnum> enabledTypes,
        @Nullable Set<WarningKind> filterWarningKinds,
        @Nullable Integer rainHourLimit
) {}
