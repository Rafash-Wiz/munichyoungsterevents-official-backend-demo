package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingCancellationReason;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class PendingBookingExpirationService {

    private final BookingRepository bookingRepository;

    @Value("${app.booking.pending-expiration-minutes:5}")
    private double pendingExpirationMinutes;

    public PendingBookingExpirationService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedDelayString = "${app.booking.pending-expiration-check-ms:60000}")
    public void runPendingExpirationJob() {
        expireExpiredPendingBookings();
    }

    public int expireExpiredPendingBookings() {
        long expirationSeconds = Math.round(pendingExpirationMinutes * 60.0d);
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(expirationSeconds);
        List<Booking> expiredPending = bookingRepository.findByStatusAndBookedAtBefore(BookingStatus.PENDING, cutoff);

        if (expiredPending.isEmpty()) {
            return 0;
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        expiredPending.forEach(booking -> {
            booking.setCancelledFromStatus(booking.getStatus());
            booking.setCancellationReason(BookingCancellationReason.EXPIRED);
            booking.setCancelledAt(cancelledAt);
            booking.setStatus(BookingStatus.CANCELLED);
        });
        bookingRepository.saveAll(expiredPending);
        return expiredPending.size();
    }
}
