package com.moviehub.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.moviehub.subscription.SubscriptionService;

import lombok.RequiredArgsConstructor;

/** Ends checkouts that were not paid in time, and renews (or ends) subscriptions at the end of their period. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.store.jobs", havingValue = "true", matchIfMissing = true)
public class StoreJobs {

    private static final Logger log = LoggerFactory.getLogger(StoreJobs.class);
    private static final int BATCH = 100;

    private final StoreService store;
    private final SubscriptionService subscriptions;

    @Scheduled(initialDelayString = "PT40S", fixedDelayString = "PT1M")
    public void expireCheckouts() {
        for (Long id : store.staleOrderIds(BATCH)) {
            try {
                store.expireIfStale(id);
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", id, e.getMessage());
            }
        }
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void renewSubscriptions() {
        for (Long id : subscriptions.dueIds(BATCH)) {
            try {
                store.renewIfDue(id);
            } catch (RuntimeException e) {
                // e.g. Stripe unreachable: tried again on the next run.
                log.warn("Could not renew subscription {}: {}", id, e.getMessage());
            }
        }
    }
}
