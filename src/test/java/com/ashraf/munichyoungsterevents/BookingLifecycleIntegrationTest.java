package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.EventStatus;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import com.ashraf.munichyoungsterevents.service.BookingService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
@ActiveProfiles("test")
class BookingLifecycleIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void cannotConfirmCancelledBooking() {
        Event event = createEvent(2);
        User attendee = createAttendeeUser("cancel-then-confirm");

        authenticate(attendee.getEmail());
        BookingDTO booking = bookingService.createBooking(bookingRequest(attendee.getId(), event.getId()));
        bookingService.cancelBooking(booking.getId());
        SecurityContextHolder.clearContext();

        assertThrows(ConflictException.class, () -> bookingService.confirmBooking(booking.getId()));
    }

    @Test
    void confirmedBookingKeepsCapacityUntilCancelledThenSlotIsFreed() {
        Event event = createEvent(1);
        User attendeeA = createAttendeeUser("capacity-a");
        User attendeeB = createAttendeeUser("capacity-b");

        authenticate(attendeeA.getEmail());
        BookingDTO bookingA = bookingService.createBooking(bookingRequest(attendeeA.getId(), event.getId()));
        bookingService.confirmBooking(bookingA.getId());
        SecurityContextHolder.clearContext();

        authenticate(attendeeB.getEmail());
        assertThrows(ConflictException.class,
                () -> bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId())));
        SecurityContextHolder.clearContext();

        authenticate(attendeeA.getEmail());
        bookingService.cancelBooking(bookingA.getId());
        SecurityContextHolder.clearContext();

        authenticate(attendeeB.getEmail());
        BookingDTO bookingB = bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId()));
        SecurityContextHolder.clearContext();
        assertEquals(BookingStatus.PENDING, bookingB.getStatus());
    }

    @Test
    void cannotBookComingSoonEvent() {
        Event event = createEvent(2);
        event.setStatus(EventStatus.COMING_SOON);
        eventRepository.save(event);

        User attendee = createAttendeeUser("coming-soon");

        authenticate(attendee.getEmail());
        assertThrows(ConflictException.class,
                () -> bookingService.createBooking(bookingRequest(attendee.getId(), event.getId())));
        SecurityContextHolder.clearContext();
    }

    private Event createEvent(int capacity) {
        Event event = new Event(
                "Lifecycle Event " + UUID.randomUUID(),
                "Event used by lifecycle integration tests",
                LocalDateTime.now().plusDays(7),
                "Munich",
                capacity,
                BigDecimal.valueOf(40.00)
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
