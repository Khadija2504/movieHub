package com.moviehub.cinema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.moviehub.common.exception.BadRequestException;

/**
 * The seats of a rectangular room. Rows are labelled A, B, C... starting from the screen, seats are
 * numbered 1..n from left to right, and an empty grid column is inserted after each aisle position.
 *
 * @param rowCount    grid rows
 * @param columnCount grid columns (seats per row + aisles)
 */
public record SeatLayout(int rowCount, int columnCount, List<SeatSpec> seats) {

    public static final int MAX_ROWS = 26;
    public static final int MAX_SEATS_PER_ROW = 40;
    public static final int MAX_COLUMNS = 50;

    public record SeatSpec(String rowLabel, int number, int gridRow, int gridColumn, SeatType type) {
    }

    /**
     * @param aislesAfter     seat numbers after which an aisle is inserted (e.g. [4, 16])
     * @param vipRows         row labels whose seats are VIP (e.g. ["H", "I"])
     * @param accessibleSeats seat labels for wheelchair spaces (e.g. ["A1", "A2"])
     */
    public static SeatLayout generate(int rows, int seatsPerRow, Collection<Integer> aislesAfter,
            Collection<String> vipRows, Collection<String> accessibleSeats) {
        if (rows < 1 || rows > MAX_ROWS) {
            throw new BadRequestException("A room must have between 1 and %d rows".formatted(MAX_ROWS));
        }
        if (seatsPerRow < 1 || seatsPerRow > MAX_SEATS_PER_ROW) {
            throw new BadRequestException("A row must have between 1 and %d seats".formatted(MAX_SEATS_PER_ROW));
        }
        Set<Integer> aisles = new TreeSet<>(aislesAfter == null ? List.of() : aislesAfter);
        if (aisles.stream().anyMatch(a -> a == null || a < 1 || a >= seatsPerRow)) {
            throw new BadRequestException(
                    "Aisles must be placed between two seats (after seat 1 to %d)".formatted(seatsPerRow - 1));
        }
        int columns = seatsPerRow + aisles.size();
        if (columns > MAX_COLUMNS) {
            throw new BadRequestException("Too many seats and aisles in a row (max %d)".formatted(MAX_COLUMNS));
        }

        String lastRow = rowLabel(rows - 1);
        Set<String> vip = normalize(vipRows);
        if (vip.stream().anyMatch(r -> r.length() != 1 || r.compareTo("A") < 0 || r.compareTo(lastRow) > 0)) {
            throw new BadRequestException("VIP rows must be between A and " + lastRow);
        }
        Set<String> accessible = normalize(accessibleSeats);

        List<SeatSpec> seats = new ArrayList<>(rows * seatsPerRow);
        Set<String> created = new TreeSet<>();
        for (int r = 0; r < rows; r++) {
            String label = rowLabel(r);
            int column = 0;
            for (int n = 1; n <= seatsPerRow; n++) {
                SeatType type = accessible.contains(label + n) ? SeatType.ACCESSIBLE
                        : vip.contains(label) ? SeatType.VIP : SeatType.STANDARD;
                seats.add(new SeatSpec(label, n, r, column, type));
                created.add(label + n);
                column += aisles.contains(n) ? 2 : 1;
            }
        }
        List<String> unknown = accessible.stream().filter(s -> !created.contains(s)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Unknown accessible seat(s): " + String.join(", ", unknown));
        }
        return new SeatLayout(rows, columns, List.copyOf(seats));
    }

    /** 0 -> A, 1 -> B, ... 25 -> Z */
    public static String rowLabel(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private static Set<String> normalize(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank())
                .map(v -> v.strip().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
