package com.github.yun531.climate.service;

import com.github.yun531.climate.service.rule.AlertTypeEnum;
import com.github.yun531.climate.service.rule.RainOnsetChangeRule;
import com.github.yun531.climate.service.rule.WarningIssuedRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 실제 스프링 빈(룰/리포지토리/서비스) + DB를 쓰는 통합 테스트.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        // --- 초기화
        "SET FOREIGN_KEY_CHECKS = 0",
        "TRUNCATE TABLE climate_snap",
        "TRUNCATE TABLE warning_state",
        "SET FOREIGN_KEY_CHECKS = 1",

        // --- climate_snap 시드
        "INSERT INTO climate_snap VALUES " +
                "(10, 1,  '2025-11-18 08:00:00'," +
                    " 1,2,3,4,5,6,7,8,9,10,11,12,13,12,11,10,9,8,7,6,5,4,3,2," +
                    "  5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6," +
                    "  50,40,50,60,60, 60,40,30,20,10, 0,0,10,20,20, 30,40,60,10,0, 20,40,70,40," +
                    "  70,40, 40,40, 30,30, 40,40, 30,60, 50,50, 40,40)," +
                "(1, 1, '2025-11-18 11:00:00'," +
                    "  1,2,3,4,5,6,7,8,9,10,11,12,13,12,11,10,9,8,7,6,5,4,3,2," +
                    "  5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6," +
                    "  40,50,60,60,60, 60,30,20,10,0, 0,10,20,20,30, 60,60,60,0,20, 40,70,40,60," +
                    "  30,30, 40,40, 30,60, 50,50, 40,40, 20,20, 0,0)",

        // --- warning_state 시드
        "INSERT INTO warning_state (region_id, kind, level, updated_at) VALUES " +
                "(1, 'RAIN',  'ADVISORY', '2025-11-04 05:00:00')," +
                "(1, 'HEAT',  'WARNING',  '2025-11-04 06:30:00')," +
                "(2, 'WIND',  'ADVISORY', '2025-11-04 07:15:00')"
})
@Import(NotificationGeneratorServiceIT.SpyConfig.class)
class NotificationGeneratorServiceIT {

    @Autowired
    private NotificationGeneratorService service;

    @Autowired private RainOnsetChangeRule rainRule;      // 이제 테스트 설정이 주입
    @Autowired private WarningIssuedRule warningRule;     // 이제 테스트 설정이 주입

    @TestConfiguration
    static class SpyConfig {
        @Bean(name = "rainOnsetChangeRule")
        RainOnsetChangeRule rainOnsetChangeRuleSpy(ClimateService climateService) {
            return Mockito.spy(new RainOnsetChangeRule(climateService));
        }
        @Bean(name = "warningIssuedRule")
        WarningIssuedRule warningIssuedRuleSpy(WarningService warningService) {
            return Mockito.spy(new WarningIssuedRule(warningService));
        }
    }

    @Test
    @DisplayName("receiveWarnings=false: 비 시작 알림만 생성되고 특보 알림은 제외된다")
    void only_rain_when_receiveWarnings_false() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");

        var messages = service.generate(List.of(1), false, since);

        assertThat(messages).isNotEmpty();
        assertThat(String.join("\n", messages))
                .contains("지역 1")
                .contains("비 시작")
                .doesNotContain("발효");

        verify(rainRule, times(1)).evaluate(any(), any());
        verify(warningRule, never()).evaluate(any(), any());
    }

    @Test
    @DisplayName("receiveWarnings=true: 비 시작 + 특보 발효 알림 모두 생성된다")
    void rain_and_warnings_when_receiveWarnings_true() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        int regionId01 = 1, regionId02 = 2;

        var messages = service.generate(List.of(regionId01, regionId02), true, since);

        assertThat(messages).isNotEmpty();
        var joined = String.join("\n", messages);
        assertThat(joined).contains("비 시작");
        assertThat(joined).contains("발효");

        verify(rainRule, times(1)).evaluate(any(), any());
        verify(warningRule, times(1)).evaluate(any(), any());
    }

    @Test
    @DisplayName("지역 ID는 최대 3개까지만 룰에 전달된다 (앞 3개 사용)")
    void region_capped_to_three() {
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        var regionIds = List.of(10, 11, 12, 13); // 4개 입력

        // 실행
        service.generate(regionIds, EnumSet.of(AlertTypeEnum.RAIN_ONSET), since, Locale.KOREA);

        // 전달된 인자 캡처
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        verify(rainRule, times(1)).evaluate(captor.capture(), any());
        var passed = captor.getValue();

        assertThat(passed).containsExactly(10, 11, 12);
    }

    @Test
    @DisplayName("정렬: 룰 → 지역 → 타입명 → 시각 순으로 정렬된다")
    void sort_rule_applied() {
        // given
        var since = LocalDateTime.parse("2025-11-04T04:00:00");
        int regionId01 = 1, regionId02 = 1;

        // when
        var messages = service.generate(
                List.of(regionId01, regionId02),
                EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED),
                since,
                Locale.KOREA
        );

        // then
        assertThat(messages).isNotEmpty();

        // 1) 룰 블록 우선: '비 시작'(RAIN_ONSET) 들이 '발효'(WARNING_ISSUED) 보다 앞에 와야 한다
        List<Integer> rainIdx = new ArrayList<>();
        List<Integer> warnIdx = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            String m = messages.get(i);
            if (m.contains("비 시작")) rainIdx.add(i);
            if (m.contains("발효"))    warnIdx.add(i);
        }
        assertThat(rainIdx).isNotEmpty();
        assertThat(warnIdx).isNotEmpty();
        // 가장 뒤의 '비 시작' 인덱스 < 가장 앞의 '발효' 인덱스
        assertThat(Collections.max(rainIdx)).isLessThan(Collections.min(warnIdx));

        // 2) 같은 룰 블록(비 시작) 내에서는 지역 번호가 오름차순인지 대략 확인
        int firstRain = rainIdx.get(0);
        int lastRain  = rainIdx.get(rainIdx.size() - 1);
        List<String> rainMsgs = messages.subList(firstRain, lastRain + 1);

        var regionNums = rainMsgs.stream()
                .map(s -> {
                    var m = java.util.regex.Pattern.compile("지역\\s+(\\d+)").matcher(s);
                    return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
                })
                .toList();

        assertThat(regionNums).isSorted(); // region asc
    }
}
