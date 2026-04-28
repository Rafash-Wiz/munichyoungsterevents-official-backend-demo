package com.ashraf.munichyoungsterevents.repository;

import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByAttendeeId(Long attendeeId);

    List<Booking> findByAttendeeIdOrderByBookedAtDesc(Long attendeeId);

    List<Booking> findByStatusOrderByBookedAtDesc(BookingStatus status);

    List<Booking> findByAttendeeIdAndStatusOrderByBookedAtDesc(Long attendeeId, BookingStatus status);

    java.util.Optional<Booking> findByAttendeeIdAndEventIdAndStatus(Long attendeeId, Long eventId, BookingStatus status);

    List<Booking> findAllByOrderByBookedAtDesc();

    long countByEventId(Long eventId);

    long countByEventIdAndStatusNot(Long eventId, BookingStatus status);

    boolean existsByAttendeeIdAndEventIdAndStatusNot(Long attendeeId, Long eventId, BookingStatus status);

    List<Booking> findByStatusAndBookedAtBefore(BookingStatus status, LocalDateTime cutoff);
}
