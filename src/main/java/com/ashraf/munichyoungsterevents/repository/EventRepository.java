package com.ashraf.munichyoungsterevents.repository;

import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            select e
            from Event e
            where e.status <> :cancelledStatus
               or e.cancelledAt is null
               or e.cancelledAt >= :cutoff
            order by case
                when e.status = com.ashraf.munichyoungsterevents.entity.EventStatus.OPEN then 0
                when e.status = com.ashraf.munichyoungsterevents.entity.EventStatus.COMING_SOON then 1
                when e.status = com.ashraf.munichyoungsterevents.entity.EventStatus.CLOSED then 2
                when e.status = com.ashraf.munichyoungsterevents.entity.EventStatus.CANCELLED then 3
                else 4
            end,
            e.dateTime asc,
            e.id desc
            """)
    Page<Event> findVisibleForListing(EventStatus cancelledStatus, java.time.LocalDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(Long id);
}
