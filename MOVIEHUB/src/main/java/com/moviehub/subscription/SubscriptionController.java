package com.moviehub.subscription;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.security.UserPrincipal;
import com.moviehub.store.StoreDtos.OrderResponse;
import com.moviehub.store.StoreService;
import com.moviehub.subscription.SubscriptionDtos.PlanRequest;
import com.moviehub.subscription.SubscriptionDtos.PlanResponse;
import com.moviehub.subscription.SubscriptionDtos.SubscriptionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Streaming plans (public) and the current user's subscription. */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;
    private final StoreService store;

    @GetMapping("/plans")
    @Operation(summary = "Available plans (public)")
    public List<PlanResponse> plans() {
        return service.plans();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe: creates the order of the first period, paid with /api/orders/{id}/payment")
    public OrderResponse subscribe(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody PlanRequest request) {
        return store.orderSubscription(user.id(), request.planId());
    }

    @GetMapping("/me")
    @Operation(summary = "My subscription (the running one, or the last ended one); 204 if I never subscribed")
    public ResponseEntity<SubscriptionResponse> mine(@AuthenticationPrincipal UserPrincipal user) {
        return service.current(user.id()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/me/cancel")
    @Operation(summary = "Turn auto-renew off (access stays until the end of the paid period)")
    public SubscriptionResponse cancel(@AuthenticationPrincipal UserPrincipal user) {
        return service.cancel(user.id());
    }

    @PostMapping("/me/renew")
    @Operation(summary = "Turn auto-renew back on")
    public SubscriptionResponse renew(@AuthenticationPrincipal UserPrincipal user) {
        return service.resume(user.id());
    }

    @PutMapping("/me/plan")
    @Operation(summary = "Change plan from the next renewal")
    public SubscriptionResponse changePlan(@AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody PlanRequest request) {
        return service.changePlan(user.id(), request.planId());
    }
}
