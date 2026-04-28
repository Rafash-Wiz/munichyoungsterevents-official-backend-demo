package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.Attendee;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.EventStatus;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import com.ashraf.munichyoungsterevents.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
class BookingLifecycleIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void cannotConfirmCancelledBooking() {
        Event event = createEvent(2);
        Attendee attendee = createAttendee("cancel-then-confirm");

        authenticate(attendee.getUser().getEmail());
        BookingDTO booking = bookingService.createBooking(bookingRequest(attendee.getId(), event.getId()));
        bookingService.cancelBooking(booking.getId());
        SecurityContextHolder.clearContext();

        assertThrows(ConflictException.class, () -> bookingService.confirmBooking(booking.getId()));
    }

    @Test
    void confirmedBookingKeepsCapacityUntilCancelledThenSlotIsFreed() {
        Event event = createEvent(1);
        Attendee attendeeA = createAttendee("capacity-a");
        Attendee attendeeB = createAttendee("capacity-b");

        authenticate(attendeeA.getUser().getEmail());
        BookingDTO bookingA = bookingService.createBooking(bookingRequest(attendeeA.getId(), event.getId()));
        bookingService.confirmBooking(bookingA.getId());
        SecurityContextHolder.clearContext();

        authenticate(attendeeB.getUser().getEmail());
        assertThrows(ConflictException.class,
                () -> bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId())));
        SecurityContextHolder.clearContext();

        authenticate(attendeeA.getUser().getEmail());
        bookingService.cancelBooking(bookingA.getId());
        SecurityContextHolder.clearContext();

        authenticate(attendeeB.getUser().getEmail());
        BookingDTO bookingB = bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId()));
        SecurityContextHolder.clearContext();
        assertEquals(BookingStatus.PENDING, bookingB.getStatus());
    }

    @Test
    void cannotBookComingSoonEvent() {
        Event event = createEvent(2);
        event.setStatus(EventStatus.COMING_SOON);
        eventRepository.save(event);

        Attendee attendee = createAttendee("coming-soon");

        authenticate(attendee.getUser().getEmail());
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
