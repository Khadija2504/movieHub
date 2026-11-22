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
import lombok.Setter;

/** An auditorium. Its seats are created once from a {@link SeatLayout} and never change afterwards. */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cinema_id", nullable = false, updatable = false)
    private Cinema cinema;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_type", nullable = false, length = 20)
    private ScreenType screenType;

    @Column(name = "row_count", nullable = false, updatable = false)
    private short rowCount;

    @Column(name = "column_count", nullable = false, updatable = false)
    private short columnCount;

    @Column(nullable = false, updatable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active = true;

    public Room(Cinema cinema, String name, ScreenType screenType, SeatLayout layout) {
        this.cinema = cinema;
        this.name = name;
        this.screenType = screenType;
        this.rowCount = (short) layout.rowCount();
        this.columnCount = (short) layout.columnCount();
        this.capacity = layout.seats().size();
        cinema.getRooms().add(this); // keep both sides of the association in sync
    }
}
