package com.github.yun531.climate.forecast.presentation;

import com.github.yun531.climate.forecast.application.ForecastService;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ForecastController.class)
class ForecastControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ForecastService forecastService;

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    // ======================= hourly =======================

    @Nested
    @DisplayName("GET /forecast/hourly")
    class Hourly {

        @Test
        @DisplayName("정상 응답 -> 200 + hourlyPoints 직렬화")
        void returnsOk() throws Exception {
            ForecastHourlyView view = new ForecastHourlyView("R1", ANNOUNCE_TIME, List.of(
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(1), 5, 30),
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(2), 8, 60)
            ));
            when(forecastService.getHourlyForecast("R1")).thenReturn(view);

            mvc.perform(get("/forecast/hourly").param("regionId", "R1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regionId").value("R1"))
                    .andExpect(jsonPath("$.hourlyPoints").isArray())
                    .andExpect(jsonPath("$.hourlyPoints.length()").value(2))
                    .andExpect(jsonPath("$.hourlyPoints[0].temp").value(5))
                    .andExpect(jsonPath("$.hourlyPoints[0].pop").value(30));
        }

        @Test
        @DisplayName("regionId 누락 -> 400")
        void missingRegionId_returns400() throws Exception {
            mvc.perform(get("/forecast/hourly"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("service null 반환 -> 204 No Content")
        void serviceReturnsNull_returns204() throws Exception {
            when(forecastService.getHourlyForecast("R1")).thenReturn(null);

            mvc.perform(get("/forecast/hourly").param("regionId", "R1"))
                    .andExpect(status().isNoContent());
        }
    }

    // ======================= daily =======================

    @Nested
    @DisplayName("GET /forecast/daily")
    class Daily {

        @Test
        @DisplayName("정상 응답 -> 200 + dailyPoints 직렬화")
        void returnsOk() throws Exception {
            ForecastDailyView view = new ForecastDailyView("R1", ANNOUNCE_TIME, List.of(
                    new ForecastDailyPoint(0, -5, 5, 30, 60),
                    new ForecastDailyPoint(1, -3, 7, 20, 40)
            ));
            when(forecastService.getDailyForecast("R1")).thenReturn(view);

            mvc.perform(get("/forecast/daily").param("regionId", "R1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regionId").value("R1"))
                    .andExpect(jsonPath("$.dailyPoints").isArray())
                    .andExpect(jsonPath("$.dailyPoints.length()").value(2))
                    .andExpect(jsonPath("$.dailyPoints[0].dayOffset").value(0))
                    .andExpect(jsonPath("$.dailyPoints[0].minTemp").value(-5));
        }

        @Test
        @DisplayName("regionId 누락 -> 400")
        void missingRegionId_returns400() throws Exception {
            mvc.perform(get("/forecast/daily"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("service null 반환 -> 204 No Content")
        void serviceReturnsNull_returns204() throws Exception {
            when(forecastService.getDailyForecast("R1")).thenReturn(null);

            mvc.perform(get("/forecast/daily").param("regionId", "R1"))
                    .andExpect(status().isNoContent());
        }
    }
}