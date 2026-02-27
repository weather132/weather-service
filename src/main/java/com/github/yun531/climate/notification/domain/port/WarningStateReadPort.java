package com.github.yun531.climate.notification.domain.port;

import com.github.yun531.climate.notification.domain.readmodel.WarningStateView;
import com.github.yun531.climate.notification.domain.model.WarningKind;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.Map;

public interface WarningStateReadPort {

    Map<WarningKind, WarningStateView> loadLatestByKind(String regionId);

    default boolean isNewlyIssuedSince(@Nullable WarningStateView state, @Nullable LocalDateTime since) {
        return state != null
                && state.updatedAt() != null
                && since != null
                && state.updatedAt().isAfter(since);
    }
}