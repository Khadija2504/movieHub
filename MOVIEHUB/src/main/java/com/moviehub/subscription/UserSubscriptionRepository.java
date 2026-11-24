package com.moviehub.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /** The running subscription (ACTIVE; its period may have just ended and wait for the renewal job). */
    @Query("select s from UserSubscription s join fetch s.plan left join fetch s.nextPlan "
            + "where s.user.id = :userId and s.status = 'ACTIVE'")
    Optional<UserSubscription> findActive(@Param("userId") Long userId);

    /** Most recent subscription of any status (to show an ended one). */
    @Query("select s from UserSubscription s join fetch s.plan where s.user.id = :userId order by s.createdAt desc")
    List<UserSubscription> findLatest(@Param("userId") Long userId, Pageable pageable);

    @Query("select s.id from UserSubscription s where s.status = 'ACTIVE' and s.currentPeriodEnd <= :now "
            + "order by s.currentPeriodEnd")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Query("select count(s) from UserSubscription s where s.status = 'ACTIVE' and s.currentPeriodEnd > :now")
    long countActive(@Param("now") Instant now);
}
