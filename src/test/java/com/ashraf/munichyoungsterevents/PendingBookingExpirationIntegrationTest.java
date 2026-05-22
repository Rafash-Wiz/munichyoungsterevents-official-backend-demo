package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingCancellationReason;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import com.ashraf.munichyoungsterevents.service.BookingService;
import com.ashraf.munichyoungsterevents.service.PendingBookingExpirationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "app.booking.pending-expiration-check-ms=3600000",
        "app.booking.pending-expiration-minutes=5"
})
@ActiveProfiles("test")
class PendingBookingExpirationIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PendingBookingExpirationService pendingBookingExpirationService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldExpireOnlyPendingBookingsOlderThanConfiguredTtl() {
        Event event = createEvent(5);
        User attendeeA = createAttendeeUser("expire-old");
        User attendeeB = createAttendeeUser("keep-recent");
        User attendeeC = createAttendeeUser("keep-confirmed");

        authenticate(attendeeA.getEmail());
        BookingDTO oldPending = bookingService.createBooking(bookingRequest(attendeeA.getId(), event.getId()));
        SecurityContextHolder.clearContext();
        authenticate(attendeeB.getEmail());
        BookingDTO recentPending = bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId()));
        SecurityContextHolder.clearContext();
        authenticate(attendeeC.getEmail());
        BookingDTO oldConfirmed = bookingService.createBooking(bookingRequest(attendeeC.getId(), event.getId()));
        bookingService.confirmBooking(oldConfirmed.getId());
        SecurityContextHolder.clearContext();

        Booking oldPendingEntity = bookingRepository.findById(oldPending.getId()).orElseThrow();
        oldPendingEntity.setBookedAt(LocalDateTime.now().minusMinutes(6));
        bookingRepository.save(oldPendingEntity);

        Booking recentPendingEntity = bookingRepository.findById(recentPending.getId()).orElseThrow();
        recentPendingEntity.setBookedAt(LocalDateTime.now().minusMinutes(2));
        bookingRepository.save(recentPendingEntity);

        Booking oldConfirmedEntity = bookingRepository.findById(oldConfirmed.getId()).orElseThrow();
        oldConfirmedEntity.setBookedAt(LocalDateTime.now().minusMinutes(6));
        bookingRepository.save(oldConfirmedEntity);

        int expiredCount = pendingBookingExpirationService.expireExpiredPendingBookings();
        assertEquals(1, expiredCount);

        Booking expiredBooking = bookingRepository.findById(oldPending.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, expiredBooking.getStatus());
        assertEquals(BookingStatus.PENDING, expiredBooking.getCancelledFromStatus());
        assertEquals(BookingCancellationReason.EXPIRED, expiredBooking.getCancellationReason());
        assertNotNull(expiredBooking.getCancelledAt());
        assertEquals(BookingStatus.PENDING, bookingRepository.findById(recentPending.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(oldConfirmed.getId()).orElseThrow().getStatus());
    }

    private Event createEvent(int capacity) {
        Event event = new Event(
                "Expiry Event " + UUID.randomUUID(),
                "Event used by pending-expiration tests",
                LocalDateTime.now().plusDays(7),
                "Munich",
                capacity,
                BigDecimal.valueOf(25.00)
        );
        return eventRepository.save(event);
    }

    private User createAttendeeUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        return userRepository.save(new User("First", "Last", email, "hashed-password", Role.ATTENDEE, true));
    }

    private BookingDTO bookingRequest(Long userId, Long eventId) {
        BookingDTO bookingDTO = new BookingDTO();
        bookingDTO.setUserId(userId);
        bookingDTO.setEventId(eventId);
        return bookingDTO;
    }

    private void authenticate(String username) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
