package com.github.yun531.climate.notification.infra.alert;

import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.snapshot.domain.reader.SnapshotReader;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotPopViewReaderTest {

    @Mock SnapshotReader snapshotReader;
    @Spy  PopViewMapper mapper = new PopViewMapper();     // SnapshotPopViewReader의 의존성
    @InjectMocks SnapshotPopViewReader reader;

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("loadCurrent — SnapshotReader.loadCurrent → PopView 변환")
    void loadCurrent_delegates() {
        when(snapshotReader.loadCurrent("R1")).thenReturn(buildSnapshot());

        PopView result = reader.loadCurrent("R1");

        assertThat(result).isNotNull();
        assertThat(result.hourly().points()).hasSize(26);
        verify(snapshotReader).loadCurrent("R1");
    }

    @Test
    @DisplayName("loadPrevious — SnapshotReader.loadPrevious → PopView 변환")
    void loadPrevious_delegates() {
        when(snapshotReader.loadPrevious("R1")).thenReturn(buildSnapshot());

        PopView result = reader.loadPrevious("R1");

        assertThat(result).isNotNull();
        verify(snapshotReader).loadPrevious("R1");
    }

    @Test
    @DisplayName("loadCurrentPreviousPair — 둘 다 있으면 Pair 반환")
    void loadPair_bothExist() {
        when(snapshotReader.loadCurrent("R1")).thenReturn(buildSnapshot());
        when(snapshotReader.loadPrevious("R1")).thenReturn(buildSnapshot());

        PopView.Pair pair = reader.loadCurrentPreviousPair("R1");

        assertThat(pair).isNotNull();
        assertThat(pair.current()).isNotNull();
        assertThat(pair.previous()).isNotNull();
    }

    @Test
    @DisplayName("loadCurrentPreviousPair — 하나 null → null")
    void loadPair_oneNull() {
        when(snapshotReader.loadCurrent("R1")).thenReturn(null);
        when(snapshotReader.loadPrevious("R1")).thenReturn(buildSnapshot());

        assertThat(reader.loadCurrentPreviousPair("R1")).isNull();
    }

    // -- 헬퍼 --

    private WeatherSnapshot buildSnapshot() {
        List<HourlyPoint> hourly = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            hourly.add(new HourlyPoint(ANNOUNCE_TIME.plusHours(i + 1), i, i * 3));
        }
        return new WeatherSnapshot("R1", ANNOUNCE_TIME, hourly, List.of());
    }
}
