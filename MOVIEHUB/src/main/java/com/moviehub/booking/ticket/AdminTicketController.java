package com.moviehub.booking.ticket;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.booking.BookingDtos.TicketCheck;
import com.moviehub.booking.BookingDtos.TicketCodeRequest;
import com.moviehub.booking.BookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ticket control at the cinema entrance (content of the QR code). ADMIN role. */
@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@Tag(name = "Admin - Tickets")
public class AdminTicketController {

    private final BookingService bookingService;

    @PostMapping("/verify")
    @Operation(summary = "Check a scanned ticket code without using it")
    public TicketCheck verify(@Valid @RequestBody TicketCodeRequest request) {
        return bookingService.verifyTicket(request.code());
    }

    @PostMapping("/check-in")
    @Operation(summary = "Validate a ticket at the entrance (a ticket can be used once)")
    public TicketCheck checkIn(@Valid @RequestBody TicketCodeRequest request) {
        return bookingService.checkIn(request.code());
    }
}
