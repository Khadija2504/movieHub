package com.moviehub.store;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.booking.BookingDtos.PaymentSession;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.security.UserPrincipal;
import com.moviehub.store.StoreDtos.MovieOrderRequest;
import com.moviehub.store.StoreDtos.OrderResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Buying and renting movies, and paying store orders (subscriptions are ordered from /api/subscriptions). */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Store")
public class StoreController {

    private final StoreService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start buying or renting a movie; pay it with /{id}/payment")
    public OrderResponse create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody MovieOrderRequest request) {
        return service.orderMovie(user.id(), request.kind(), request.movieId());
    }

    @GetMapping
    @Operation(summary = "My payment history (purchases, rentals, subscription payments)")
    public PageResponse<OrderResponse> history(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "1") int page) {
        return service.history(user.id(), page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my orders")
    public OrderResponse get(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.get(user.id(), id);
    }

    @PostMapping("/{id}/payment")
    @Operation(summary = "Start (or resume) the payment of an order")
    public PaymentSession startPayment(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.startPayment(user.id(), id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm once paid: the movie is added to the library or the subscription starts")
    public OrderResponse confirm(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.confirm(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Abandon an unpaid order")
    public OrderResponse cancel(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return service.cancel(user.id(), id);
    }
}
