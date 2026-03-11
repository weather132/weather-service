package com.github.yun531.climate.snapshot.infra.reader;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.yun531.climate.snapshot.domain.policy.PublishSchedulePolicy;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.config.SnapshotApiProperties;
import com.github.yun531.climate.snapshot.infra.config.SnapshotCacheProperties;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.api.RestSnapshotApiClient;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.mapper.SnapshotApiResponseMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiSnapshotReader 통합 테스트.
 * WireMock 으로 외부 기상 API를 스텁하여 전체 흐름을 검증한다:
 *   RestSnapshotApiClient -> SnapshotApiResponseMapper -> CachingSnapshotReader(캐시)
 * Spring Context 없이 순수 컴포넌트 조립으로 테스트.
 * JSON 픽스처는 src/test/resources/fixtures/ 에서 로드.
 */
class ApiSnapshotReaderIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ApiSnapshotReader reader;

    // fixtures/*.json 로드 결과
    private static String hourlyJson;
    private static String dailyJson;

    // 고정 시간: 2026-01-22 05:15 (05:00 발표 + delay 10분 경과)
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 22, 5, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @BeforeAll
    static void loadFixtures() throws IOException {
        hourlyJson = readFixture("fixtures/hourly-snapshot-response.json");
        dailyJson = readFixture("fixtures/daily-forecast-response.json");
    }

    @BeforeEach
    void setUp() {
        PublishSchedulePolicy publishSchedule = new PublishSchedulePolicy(10);

        SnapshotApiProperties apiProps = new SnapshotApiProperties(
                wireMock.baseUrl(), 2000, 5000);

        SnapshotCacheProperties cacheProps = new SnapshotCacheProperties(180, 165);

        RestSnapshotApiClient client = new RestSnapshotApiClient(apiProps, new RestTemplateBuilder());
        SnapshotApiResponseMapper mapper = new SnapshotApiResponseMapper();

        reader = new ApiSnapshotReader(cacheProps, publishSchedule, FIXED_CLOCK, client, mapper);
    }

    // =========================================================
    // 시나리오 1: 정상 응답 (hourly + daily)
    // =========================================================

    @Test
    @DisplayName("정상 hourly + daily 응답 -> WeatherSnapshot 조립 성공")
    void normalResponse_producesSnapshot() {
        stubHourlyOk();
        stubDailyOk();

        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        assertThat(snap).isNotNull();
        assertThat(snap.regionId()).isEqualTo("11B10101");
        assertThat(snap.reportTime()).isEqualTo(LocalDateTime.of(2026, 1, 22, 5, 0));
        assertThat(snap.hourly()).hasSize(26);
        assertThat(snap.daily()).hasSize(7);

        // hourly 첫 포인트 검증
        assertThat(snap.hourly().get(0).validAt())
                .isEqualTo(LocalDateTime.of(2026, 1, 22, 6, 0));
        assertThat(snap.hourly().get(0).pop()).isEqualTo(40);
        assertThat(snap.hourly().get(0).temp()).isEqualTo(1);
    }

    // =========================================================
    // 시나리오 2: hourly 정상 응답이지만 데이터 없음 (items = [])
    // =========================================================

    @Test
    @DisplayName("hourly items 빈 배열 -> null 반환, 캐싱 안 됨 (daily 호출 없음)")
    void emptyHourlyData_returnsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/hourly/snapshot"))
                .willReturn(okJson(
                        "{\"announceTime\":\"2026-01-22T05:00:00\",\"coordsX\":60,\"coordsY\":127," +
                                "\"gridForecastData\":[]}")));

        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        assertThat(snap).isNull();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/daily/forecast")));
    }

    // =========================================================
    // 시나리오 3: hourly API 실패 시 불완전 스냅샷 캐싱 방지
    // =========================================================

    @Test
    @DisplayName("hourly API 500 -> 불완전 스냅샷 캐싱 방지, null 반환 (다음 요청에서 재시도)")
    void hourlyServerError_returnsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/hourly/snapshot"))
                .willReturn(serverError()));
        stubDailyOk();

        WeatherSnapshot snap = reader.loadCurrent("11B10101");
        assertThat(snap).isNull();
    }

    // =========================================================
    // 시나리오 4: hourly 타임아웃
    // =========================================================

    @Test
    @DisplayName("hourly 응답 지연(타임아웃 초과) -> null 반환")
    void hourlyTimeout_returnsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/hourly/snapshot"))
                .willReturn(ok().withFixedDelay(10_000)));
        stubDailyOk();

        WeatherSnapshot snap = reader.loadCurrent("11B10101");
        assertThat(snap).isNull();
    }

    // =========================================================
    // 시나리오 5: 캐시 히트 (동일 publishTime 재요청)
    // =========================================================

    @Test
    @DisplayName("동일 regionId 2회 요청 -> hourly API 1회만 호출 (캐시 히트)")
    void cacheHit_singleApiCall() {
        stubHourlyOk();
        stubDailyOk();

        WeatherSnapshot snap1 = reader.loadCurrent("11B10101");
        assertThat(snap1).isNotNull();

        WeatherSnapshot snap2 = reader.loadCurrent("11B10101");
        assertThat(snap2).isNotNull();

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/hourly/snapshot")));
    }

    // =========================================================
    // 시나리오 6: PREVIOUS 스냅샷 로드
    // =========================================================

    @Test
    @DisplayName("loadPrevious -> CURRENT-3h(02:00) announceTime 으로 조회")
    void loadPrevious_resolvesCorrectPublishTime() {
        stubHourlyOk();
        stubDailyOk();

        WeatherSnapshot snap = reader.loadPrevious("11B10101");

        assertThat(snap).isNotNull();
        wireMock.verify(getRequestedFor(urlPathEqualTo("/hourly/snapshot"))
                .withQueryParam("announceTime", containing("02:00:00")));
    }

    // =========================================================
    // 시나리오 7: daily API 실패 시 불완전 스냅샷 캐싱 방지
    // =========================================================

    @Test
    @DisplayName("daily API 500 -> 불완전 스냅샷 캐싱 방지, null 반환 (다음 요청에서 재시도)")
    void dailyFails_returnsNull() {
        stubHourlyOk();
        wireMock.stubFor(get(urlPathEqualTo("/daily/forecast"))
                .willReturn(serverError()));

        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        assertThat(snap).isNull();
    }

    // =========================================================
    // 시나리오 8: JSON 역직렬화 전체 필드 검증
    // =========================================================

    @Test
    @DisplayName("JSON 응답의 모든 필드가 정확히 역직렬화된다")
    void jsonDeserialization_allFieldsMapped() {
        stubHourlyOk();
        stubDailyOk();

        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        // hourly 26번째(마지막) 포인트
        assertThat(snap.hourly().get(25).validAt())
                .isEqualTo(LocalDateTime.of(2026, 1, 23, 7, 0));
        assertThat(snap.hourly().get(25).pop()).isEqualTo(80);
        assertThat(snap.hourly().get(25).temp()).isEqualTo(5);

        // daily day0: AM pop=30, PM pop=60
        assertThat(snap.daily().get(0).amPop()).isEqualTo(30);
        assertThat(snap.daily().get(0).pmPop()).isEqualTo(60);
    }

    // =========================================================
    // 시나리오 9: regionId blank -> null (API 미호출)
    // =========================================================

    @Test
    @DisplayName("regionId가 blank 이면 API 호출 없이 null 반환")
    void blankRegionId_returnsNull_noApiCall() {
        WeatherSnapshot snap = reader.loadCurrent("");

        assertThat(snap).isNull();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/hourly/snapshot")));
    }

    // =========================================================
    // WireMock 스텁 헬퍼
    // =========================================================

    private void stubHourlyOk() {
        wireMock.stubFor(get(urlPathEqualTo("/hourly/snapshot"))
                .willReturn(okJson(hourlyJson)));
    }

    private void stubDailyOk() {
        wireMock.stubFor(get(urlPathEqualTo("/daily/forecast"))
                .willReturn(okJson(dailyJson)));
    }

    // =========================================================
    // Fixture 로드 헬퍼
    // =========================================================

    private static String readFixture(String classpath) throws IOException {
        try (InputStream is = ApiSnapshotReaderIntegrationTest.class
                .getClassLoader().getResourceAsStream(classpath)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + classpath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}