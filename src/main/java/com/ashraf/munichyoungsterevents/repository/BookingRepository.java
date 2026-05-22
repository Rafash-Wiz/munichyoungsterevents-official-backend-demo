package com.ashraf.munichyoungsterevents.repository;

import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingCancellationReason;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByUserIdOrderByBookedAtDescIdDesc(Long userId);

    List<Booking> findByEventIdOrderByBookedAtDescIdDesc(Long eventId);

    List<Booking> findByStatusOrderByBookedAtDescIdDesc(BookingStatus status);

    List<Booking> findByUserIdAndStatusOrderByBookedAtDescIdDesc(Long userId, BookingStatus status);

    List<Booking> findByEventIdAndStatusOrderByBookedAtDescIdDesc(Long eventId, BookingStatus status);

    List<Booking> findByUserIdAndEventIdOrderByBookedAtDescIdDesc(Long userId, Long eventId);

    List<Booking> findByUserIdAndEventIdAndStatusOrderByBookedAtDescIdDesc(Long userId, Long eventId, BookingStatus status);

    java.util.Optional<Booking> findByUserIdAndEventIdAndStatus(Long userId, Long eventId, BookingStatus status);

    List<Booking> findAllByOrderByBookedAtDescIdDesc();

    Page<Booking> findAllByOrderByBookedAtDescIdDesc(Pageable pageable);

    Page<Booking> findByUserIdOrderByBookedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Booking> findByEventIdOrderByBookedAtDescIdDesc(Long eventId, Pageable pageable);

    Page<Booking> findByStatusOrderByBookedAtDescIdDesc(BookingStatus status, Pageable pageable);

    Page<Booking> findByUserIdAndStatusOrderByBookedAtDescIdDesc(Long userId, BookingStatus status, Pageable pageable);

    Page<Booking> findByEventIdAndStatusOrderByBookedAtDescIdDesc(Long eventId, BookingStatus status, Pageable pageable);

    Page<Booking> findByUserIdAndEventIdOrderByBookedAtDescIdDesc(Long userId, Long eventId, Pageable pageable);

    Page<Booking> findByUserIdAndEventIdAndStatusOrderByBookedAtDescIdDesc(Long userId, Long eventId, BookingStatus status, Pageable pageable);

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, BookingStatus status);

    long countByEventIdAndStatusNot(Long eventId, BookingStatus status);

    long countByEventIdAndStatusAndCancellationReasonAndCancelledFromStatus(
            Long eventId,
            BookingStatus status,
            BookingCancellationReason cancellationReason,
            BookingStatus cancelledFromStatus
    );

    boolean existsByUserIdAndEventIdAndStatusNot(Long userId, Long eventId, BookingStatus status);

    List<Booking> findByStatusAndBookedAtBefore(BookingStatus status, LocalDateTime cutoff);
}
