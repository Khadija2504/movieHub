package com.moviehub.booking;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.booking.BookingDtos.BookingResponse;
import com.moviehub.booking.BookingDtos.CreateBookingRequest;
import com.moviehub.booking.BookingDtos.PaymentSession;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The current user's cinema bookings. Requires authentication. */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hold seats of a screening for 10 minutes while paying")
    public BookingResponse create(@AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody CreateBookingRequest request) {
        return service.create(user.id(), request);
    }

    @GetMapping
    @Operation(summary = "My bookings: upcoming (soonest first) or past/cancelled (most recent first)")
    public PageResponse<BookingResponse> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "UPCOMING") BookingService.Scope scope,
            @RequestParam(defaultValue = "1") int page) {
        return service.list(user.id(), scope, page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my bookings")
    public BookingResponse get(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.get(user.id(), id);
    }

    @PostMapping("/{id}/payment")
    @Operation(summary = "Start (or resume) the payment of a held booking")
    public PaymentSession startPayment(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.startPayment(user.id(), id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm the booking once paid (stays PENDING while the payment is processing)")
    public BookingResponse confirm(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.confirm(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel: unpaid holds any time, paid bookings (refunded) until 2 hours before the screening")
    public BookingResponse cancel(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.cancel(user.id(), id);
    }

    @GetMapping(value = "/{id}/ticket.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR code of a confirmed booking's ticket")
    public ResponseEntity<byte[]> ticket(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .contentType(MediaType.IMAGE_PNG)
                .body(service.ticketQr(user.id(), id));
    }
}
