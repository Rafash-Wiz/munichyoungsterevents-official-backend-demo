package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.Attendee;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
class BookingConcurrencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldAllowOnlyOneBookingWhenCapacityIsOneUnderConcurrentRequests() throws Exception {
        Event event = createEvent(1);
        Attendee attendeeA = createAttendee("capacity-a");
        Attendee attendeeB = createAttendee("capacity-b");

        List<AttemptResult> results = runConcurrently(
                attendeeA.getUser().getEmail(),
                () -> bookingService.createBooking(bookingRequest(attendeeA.getId(), event.getId())),
                attendeeB.getUser().getEmail(),
                () -> bookingService.createBooking(bookingRequest(attendeeB.getId(), event.getId()))
        );

        assertOneSuccessAndOneConflict(results);

        long activeBookings = bookingRepository.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED);
        assertEquals(1L, activeBookings);
    }

    @Test
    void shouldAllowOnlyOneActiveBookingForSameAttendeeAndEventUnderConcurrentRequests() throws Exception {
        Event event = createEvent(5);
        Attendee attendee = createAttendee("duplicate");

        List<AttemptResult> results = runConcurrently(
                attendee.getUser().getEmail(),
                () -> bookingService.createBooking(bookingRequest(attendee.getId(), event.getId())),
                attendee.getUser().getEmail(),
                () -> bookingService.createBooking(bookingRequest(attendee.getId(), event.getId()))
        );

        assertOneSuccessAndOneConflict(results);

        long activeBookings = bookingRepository.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED);
        assertEquals(1L, activeBookings);
    }

    private Event createEvent(int capacity) {
        Event event = new Event(
                "Concurrency Event " + UUID.randomUUID(),
                "Event used by concurrency integration tests",
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

    private List<AttemptResult> runConcurrently(
            String usernameA, Callable<BookingDTO> taskA,
            String usernameB, Callable<BookingDTO> taskB) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttemptResult>> futures = new ArrayList<>();

        futures.add(submitAuthenticated(executor, ready, start, usernameA, taskA));
        futures.add(submitAuthenticated(executor, ready, start, usernameB, taskB));

        ready.await();
        start.countDown();

        List<AttemptResult> results = new ArrayList<>();
        for (Future<AttemptResult> future : futures) {
            results.add(future.get());
        }

        executor.shutdown();
        return results;
    }

    private Future<AttemptResult> submitAuthenticated(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            String username,
            Callable<BookingDTO> task) {
        return executor.submit(() -> {
            ready.countDown();
            start.await();
            authenticate(username);

            try {
                return new AttemptResult(task.call(), null);
            } catch (Throwable ex) {
                return new AttemptResult(null, ex);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private void authenticate(String username) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void assertOneSuccessAndOneConflict(List<AttemptResult> results) {
        long successCount = results.stream().filter(result -> result.booking() != null).count();
        long errorCount = results.stream().filter(result -> result.error() != null).count();

        assertEquals(1L, successCount);
        assertEquals(1L, errorCount);

        Throwable error = results.stream()
                .map(AttemptResult::error)
                .filter(value -> value != null)
                .findFirst()
                .orElseThrow();

        assertInstanceOf(ConflictException.class, error);
    }

    private record AttemptResult(BookingDTO booking, Throwable error) {
    }
}
