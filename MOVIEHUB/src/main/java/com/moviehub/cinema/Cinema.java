package com.moviehub.cinema;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.moviehub.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cinemas")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cinema extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false)
    private String address;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "text")
    private String description;

    private Double latitude;

    private Double longitude;

    /** IANA zone id, e.g. Europe/Paris. Showtimes are entered and shown in this zone. */
    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "cinema")
    @OrderBy("name")
    private List<Room> rooms = new ArrayList<>();

    public Cinema(String name, String city, String address, String timeZone) {
        this.name = name;
        this.city = city;
        this.address = address;
        this.timeZone = timeZone;
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
