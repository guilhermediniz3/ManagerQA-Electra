package com.tester;

import com.tester.dashboard.DashboardController;
import com.tester.demand.DemandService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardDateFilterTest {
    private final DemandService demands = mock(DemandService.class);
    private final DashboardController dashboard = new DashboardController(demands);

    @Test void includesJiraAndIsoTimestampsUsingTheIssueLocalDate() {
        when(demands.list()).thenReturn(List.of(
            demand("QA-1", "2026-09-09T23:30:00.000-0300"),
            demand("QA-2", "2026-09-09T00:30:00.123+0530"),
            demand("QA-3", "2026-09-09T12:30:00-03:00"),
            demand("QA-4", "2026-09-09T15:30:00Z"),
            demand("QA-5", "2026-09-08T23:59:59.999-0300"),
            demand("QA-6", "2026-09-10T00:00:00.000-0300"),
            demand("QA-7", "invalid-date")
        ));
        var day = LocalDate.of(2026, 9, 9);
        assertThat(dashboard.get(null, null, day, day).total()).isEqualTo(4);
        assertThat(dashboard.get(null, null, null, null).total()).isEqualTo(7);
    }

    @Test void supportsOpenEndedDateRangesAndRejectsInvertedRanges() {
        when(demands.list()).thenReturn(List.of(
            demand("QA-1", "2026-09-08T12:00:00.000+0000"),
            demand("QA-2", "2026-09-09T12:00:00.000+0000"),
            demand("QA-3", "2026-09-10T12:00:00.000+0000")
        ));
        var day = LocalDate.of(2026, 9, 9);
        assertThat(dashboard.get(null, null, day, null).total()).isEqualTo(2);
        assertThat(dashboard.get(null, null, null, day).total()).isEqualTo(2);
        assertThatThrownBy(() -> dashboard.get(null, null, day.plusDays(1), day))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static DemandService.View demand(String key, String updated) {
        return new DemandService.View(
            key, "Demanda", "", "1", "Em Teste", null, null, "QA",
            null, null, updated, null, null, null, false,
            null, null, null, null, null, null, null
        );
    }
}