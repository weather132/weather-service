package com.github.yun531.climate.notification.application.alert;

import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.detect.RainForecastDetector;
import com.github.yun531.climate.notification.domain.detect.RainOnsetDetector;
import com.github.yun531.climate.notification.domain.detect.WarningIssuedDetector;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.AlertPayload;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewReader;
import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;
import com.github.yun531.climate.warning.domain.reader.WarningStateReader;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateAlertsServiceTest {

    @Mock PopViewReader popViewReader;
    @Mock WarningStateReader warningStateReader;
    @Mock RainOnsetDetector rainOnsetDetector;
    @Mock RainForecastDetector rainForecastDetector;
    @Mock WarningIssuedDetector warningIssuedDetector;
    @Mock RainOnsetAdjuster rainOnsetAdjuster;
    @Mock RainForecastAdjuster rainForecastAdjuster;

    private GenerateAlertsService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 22, 5, 15);

    @BeforeEach
    void setUp() {
        service = new GenerateAlertsService(
                popViewReader, warningStateReader,
                rainOnsetDetector, rainForecastDetector, warningIssuedDetector,
                rainOnsetAdjuster, rainForecastAdjuster,
                3, 2
        );
    }

    // ======================= 입력 가드 =======================

    @Test
    @DisplayName("null command -> 빈 결과")
    void nullCommand_empty() {
        assertThat(service.generate(null, NOW)).isEmpty();
    }

    @Test
    @DisplayName("빈 regionIds -> 빈 결과")
    void emptyRegionIds_empty() {
        var cmd = new GenerateAlertsCommand(
                List.of(), null, EnumSet.of(AlertTypeEnum.RAIN_ONSET), null, null);

        assertThat(service.generate(cmd, NOW)).isEmpty();
    }

    @Test
    @DisplayName("빈 enabledTypes -> 빈 결과")
    void emptyEnabledTypes_empty() {
        var cmd = new GenerateAlertsCommand(
                List.of("R1"), null, EnumSet.noneOf(AlertTypeEnum.class), null, null);

        assertThat(service.generate(cmd, NOW)).isEmpty();
    }

    @Test
    @DisplayName("maxRegionCount 초과 시 잘림")
    void regionCount_trimmed() {
        PopView.Pair pair = mock(PopView.Pair.class);
        when(popViewReader.loadCurrentPreviousPair(anyString())).thenReturn(pair);
        when(rainOnsetDetector.detect(anyString(), any(), any())).thenReturn(List.of());

        var cmd = new GenerateAlertsCommand(
                List.of("R1", "R2", "R3", "R4", "R5"), null,
                EnumSet.of(AlertTypeEnum.RAIN_ONSET), null, null);

        service.generate(cmd, NOW);

        verify(popViewReader, times(3)).loadCurrentPreviousPair(anyString());
    }

    // ======================= RAIN_ONSET =======================

    @Nested
    @DisplayName("RAIN_ONSET")
    class RainOnset {

        @Test
        @DisplayName("정상 — popView pair 로드 -> detect -> adjust 파이프라인")
        void fullPipeline() {
            PopView.Pair pair = mock(PopView.Pair.class);
            when(popViewReader.loadCurrentPreviousPair("R1")).thenReturn(pair);

            AlertEvent raw = new AlertEvent(AlertTypeEnum.RAIN_ONSET, "R1", NOW,
                    new RainOnsetPayload(AlertTypeEnum.RAIN_ONSET, NOW.plusHours(3), 80));
            when(rainOnsetDetector.detect(eq("R1"), eq(pair), any())).thenReturn(List.of(raw));
            when(rainOnsetAdjuster.adjust(anyList(), any(), any())).thenReturn(List.of(raw));

            var cmd = new GenerateAlertsCommand(
                    List.of("R1"), null, EnumSet.of(AlertTypeEnum.RAIN_ONSET), null, null);

            List<AlertEvent> result = service.generate(cmd, NOW);

            assertThat(result).hasSize(1);
            verify(popViewReader).loadCurrentPreviousPair("R1");
            verify(rainOnsetDetector).detect(eq("R1"), eq(pair), any());
            verify(rainOnsetAdjuster).adjust(anyList(), any(), any());
        }

        @Test
        @DisplayName("pair null -> 빈 결과, detect 미호출")
        void nullPair_empty() {
            when(popViewReader.loadCurrentPreviousPair("R1")).thenReturn(null);

            var cmd = new GenerateAlertsCommand(
                    List.of("R1"), null, EnumSet.of(AlertTypeEnum.RAIN_ONSET), null, null);

            assertThat(service.generate(cmd, NOW)).isEmpty();
            verify(rainOnsetDetector, never()).detect(any(), any(), any());
        }
    }

    // ======================= RAIN_FORECAST =======================

    @Nested
    @DisplayName("RAIN_FORECAST")
    class RainForecast {

        @Test
        @DisplayName("정상 — popView 로드 -> detect -> adjust 파이프라인")
        void fullPipeline() {
            PopView view = mock(PopView.class);
            when(popViewReader.loadCurrent("R1")).thenReturn(view);

            AlertEvent raw = new AlertEvent(
                    AlertTypeEnum.RAIN_FORECAST, "R1", NOW,
                    new RainForecastPayload(AlertTypeEnum.RAIN_FORECAST, List.of(), List.of()));
            when(rainForecastDetector.detect(eq("R1"), eq(view), any())).thenReturn(raw);
            when(rainForecastAdjuster.adjust(eq(raw), any(), any())).thenReturn(raw);

            var cmd = new GenerateAlertsCommand(
                    List.of("R1"), null, EnumSet.of(AlertTypeEnum.RAIN_FORECAST), null, null);

            List<AlertEvent> result = service.generate(cmd, NOW);

            assertThat(result).hasSize(1);
            verify(popViewReader).loadCurrent("R1");
            verify(rainForecastDetector).detect(eq("R1"), eq(view), any());
            verify(rainForecastAdjuster).adjust(eq(raw), any(), any());
        }

        @Test
        @DisplayName("popView null -> 빈 결과, detect 미호출")
        void nullView_empty() {
            when(popViewReader.loadCurrent("R1")).thenReturn(null);

            var cmd = new GenerateAlertsCommand(
                    List.of("R1"), null, EnumSet.of(AlertTypeEnum.RAIN_FORECAST), null, null);

            assertThat(service.generate(cmd, NOW)).isEmpty();
            verify(rainForecastDetector, never()).detect(any(), any(), any());
        }
    }

    // ======================= WARNING_ISSUED =======================

    @Nested
    @DisplayName("WARNING_ISSUED")
    class WarningIssued {

        @Test
        @DisplayName("정상 — warningStateReader 로드 -> detect 파이프라인")
        void fullPipeline() {
            AlertEvent event = new AlertEvent(AlertTypeEnum.WARNING_ISSUED, "R1", NOW,
                    new WarningIssuedPayload(
                            AlertTypeEnum.WARNING_ISSUED, WarningKind.RAIN, WarningLevel.WARNING));
            when(warningStateReader.loadLatestByKind("R1")).thenReturn(
                    Map.of(WarningKind.RAIN,
                            new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.WARNING, NOW)));
            when(warningIssuedDetector.detect(eq("R1"), anyMap(), any(), any(), any()))
                    .thenReturn(List.of(event));

            var cmd = new GenerateAlertsCommand(
                    List.of("R1"), null, EnumSet.of(AlertTypeEnum.WARNING_ISSUED), null, null);

            List<AlertEvent> result = service.generate(cmd, NOW);

            assertThat(result).hasSize(1);
            verify(warningStateReader).loadLatestByKind("R1");
            verify(warningIssuedDetector).detect(eq("R1"), anyMap(), any(), any(), any());
        }

        @Test
        @DisplayName("빈 states -> 빈 결과, detect 미호출")
        void emptyStates_empty() {
            when(warningStateReader.loadLatestByKind("R1")).thenReturn(Map.of());

            var cmd = new GenerateAlertsCommand(
                    List.of("R1"), null, EnumSet.of(AlertTypeEnum.WARNING_ISSUED), null, null);

            assertThat(service.generate(cmd, NOW)).isEmpty();
            verify(warningIssuedDetector, never()).detect(any(), any(), any(), any(), any());
        }
    }
}