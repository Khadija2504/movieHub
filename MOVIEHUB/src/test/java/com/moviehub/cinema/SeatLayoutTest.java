package com.moviehub.cinema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moviehub.cinema.SeatLayout.SeatSpec;
import com.moviehub.common.exception.BadRequestException;

class SeatLayoutTest {

    @Test
    void generatesLabelledSeatsWithAislesAndTypes() {
        SeatLayout layout = SeatLayout.generate(3, 6, List.of(3), List.of("c"), List.of("a6"));

        assertThat(layout.rowCount()).isEqualTo(3);
        assertThat(layout.columnCount()).isEqualTo(7); // 6 seats + 1 aisle
        assertThat(layout.seats()).hasSize(18);

        SeatSpec a3 = find(layout, "A", 3);
        SeatSpec a4 = find(layout, "A", 4);
        assertThat(a3.gridColumn()).isEqualTo(2);
        assertThat(a4.gridColumn()).isEqualTo(4); // column 3 is the aisle
        assertThat(find(layout, "B", 1).gridRow()).isEqualTo(1);

        assertThat(find(layout, "C", 2).type()).isEqualTo(SeatType.VIP);
        assertThat(find(layout, "A", 6).type()).isEqualTo(SeatType.ACCESSIBLE);
        assertThat(find(layout, "B", 2).type()).isEqualTo(SeatType.STANDARD);
    }

    @Test
    void accessibleWinsOverVip() {
        SeatLayout layout = SeatLayout.generate(1, 2, null, List.of("A"), List.of("A1"));
        assertThat(find(layout, "A", 1).type()).isEqualTo(SeatType.ACCESSIBLE);
        assertThat(find(layout, "A", 2).type()).isEqualTo(SeatType.VIP);
    }

    @Test
    void rejectsInvalidLayouts() {
        assertThatThrownBy(() -> SeatLayout.generate(0, 10, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SeatLayout.generate(27, 10, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SeatLayout.generate(5, 41, null, null, null)).isInstanceOf(BadRequestException.class);
        // Aisle after the last seat is not between two seats.
        assertThatThrownBy(() -> SeatLayout.generate(5, 10, List.of(10), null, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SeatLayout.generate(5, 10, null, List.of("F"), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SeatLayout.generate(5, 10, null, null, List.of("A11")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SeatLayout.generate(5, 40, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void slotIncludesCleaningTimeAndDefaultsRuntime() {
        assertThat(ScreeningRules.slot(100).toMinutes()).isEqualTo(115);
        assertThat(ScreeningRules.slot(null).toMinutes()).isEqualTo(135);
    }

    private static SeatSpec find(SeatLayout layout, String row, int number) {
        return layout.seats().stream().filter(s -> s.rowLabel().equals(row) && s.number() == number).findFirst()
                .orElseThrow();
    }
}
