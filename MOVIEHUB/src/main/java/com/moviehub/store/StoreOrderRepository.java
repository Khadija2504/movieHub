package com.moviehub.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {

    @EntityGraph(attributePaths = {"movie", "plan", "user"})
    @Query("select o from StoreOrder o where o.id = :id")
    Optional<StoreOrder> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"movie", "plan", "user"})
    Optional<StoreOrder> findByPaymentId(String paymentId);

    boolean existsByReference(String reference);

    boolean existsByStatusAndSubscriptionId(StoreOrder.Status status, Long subscriptionId);

    /** Payment history: paid, declined and refunded orders (abandoned checkouts are left out). */
    @EntityGraph(attributePaths = {"movie", "plan"})
    @Query(value = "select o from StoreOrder o where o.user.id = :userId "
            + "and o.status in ('PAID', 'FAILED', 'REFUNDED') order by o.createdAt desc",
            countQuery = "select count(o) from StoreOrder o where o.user.id = :userId "
                    + "and o.status in ('PAID', 'FAILED', 'REFUNDED')")
    Page<StoreOrder> findHistory(@Param("userId") Long userId, Pageable pageable);

    @Query("select o from StoreOrder o where o.user.id = :userId and o.status = 'PENDING' "
            + "and o.kind in ('PURCHASE', 'RENTAL') and o.movie.id = :movieId")
    List<StoreOrder> findPendingForMovie(@Param("userId") Long userId, @Param("movieId") Long movieId);

    @Query("select o from StoreOrder o where o.user.id = :userId and o.status = 'PENDING' "
            + "and o.kind = 'SUBSCRIPTION' and o.subscription is null")
    List<StoreOrder> findPendingSubscriptions(@Param("userId") Long userId);

    @Query("select o.id from StoreOrder o where o.status = 'PENDING' and o.expiresAt <= :now order by o.expiresAt")
    List<Long> findPendingExpiredBefore(@Param("now") Instant now, Pageable pageable);
}
