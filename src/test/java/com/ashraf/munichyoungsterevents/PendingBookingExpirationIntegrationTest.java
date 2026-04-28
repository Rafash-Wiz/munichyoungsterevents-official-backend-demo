package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.Attendee;
import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "app.booking.pending-expiration-check-ms=3600000",
        "app.booking.pending-expiration-minutes=5"
})
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
    private AttendeeRepository attendeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldExpireOnlyPendingBookingsOlderThanConfiguredTtl() {
        Event event = createEvent(5);
        Attendee attendeeA = createAttendee("expire-old");
        Attendee attendeeB = createAttendee("keep-recent");
        Attendee attendeeC = createAttendee("keep-confirmed");

        authenticate(attendeeA.getUser().getEmail());
        BookingDTO oldPending = bookingService.createBooking(bookingRequest(attendeeA.getId(), event.getId()));
        SecurityContextHolder.clearContext();
        authenticate(attendeeB.getUser().getEmail());
        BookingDTO recentPending = bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId()));
        SecurityContextHolder.clearContext();
        authenticate(attendeeC.getUser().getEmail());
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

        assertEquals(BookingStatus.CANCELLED, bookingRepository.findById(oldPending.getId()).orElseThrow().getStatus());
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

    private Attendee createAttendee(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        User user = new User(email, "hashed-password", Role.ATTENDEE, true);
        User savedUser = userRepository.save(user);

        Attendee attendee = new Attendee(
                "First",
                "Last",
                email
        );
        attendee.setUser(savedUser);
        Attendee savedAttendee = attendeeRepository.save(attendee);
        savedUser.setAttendee(savedAttendee);
        userRepository.save(savedUser);
        return savedAttendee;
    }

    private BookingDTO bookingRequest(Long attendeeId, Long eventId) {
        BookingDTO bookingDTO = new BookingDTO();
        bookingDTO.setAttendeeId(attendeeId);
        bookingDTO.setEventId(eventId);
        return bookingDTO;
    }

    private void authenticate(String username) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
