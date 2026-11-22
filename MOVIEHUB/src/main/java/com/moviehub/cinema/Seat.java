package com.moviehub.cinema;

import com.moviehub.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @Column(name = "row_label", nullable = false, length = 2, updatable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, updatable = false)
    private short number;

    @Column(name = "grid_row", nullable = false, updatable = false)
    private short gridRow;

    @Column(name = "grid_column", nullable = false, updatable = false)
    private short gridColumn;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20, updatable = false)
    private SeatType type;

    public Seat(Room room, SeatLayout.SeatSpec spec) {
        this.room = room;
        this.rowLabel = spec.rowLabel();
        this.number = (short) spec.number();
        this.gridRow = (short) spec.gridRow();
        this.gridColumn = (short) spec.gridColumn();
        this.type = spec.type();
    }

    /** e.g. "F12" */
    public String label() {
        return rowLabel + number;
    }
}
