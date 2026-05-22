package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingCancellationReason;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.EventStatus;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE bookings, users, events RESTART IDENTITY CASCADE");
    }

    @Test
    void eventRoutesRespectPublicAttendeeAndAdminAccess() throws Exception {
        String attendeeEmail = "attendee-" + UUID.randomUUID() + "@example.com";
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        createAttendeeUser(attendeeEmail, password);
        createAdminUser(adminEmail, password);

        EventPayload payload = new EventPayload(
                "Role Guard " + UUID.randomUUID(),
                "Authorization test event",
                "A longer event description used to verify the API accepts and returns full event details.",
                "https://cdn.example.com/events/role-guard.jpg",
                LocalDateTime.now().plusDays(5),
                "Munich",
                50,
                BigDecimal.valueOf(19.99),
                EventStatus.OPEN
        );

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(payload)))
                .andExpect(status().isUnauthorized());

        MockHttpSession attendeeSession = login(attendeeEmail, password);
        mockMvc.perform(post("/api/events")
                        .session(attendeeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(payload)))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(adminEmail, password);
        mockMvc.perform(post("/api/events")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(payload.title()))
                .andExpect(jsonPath("$.longDescription").value(payload.longDescription()))
                .andExpect(jsonPath("$.imageUrl").value(payload.imageUrl()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        EventPayload cancelledUpdatePayload = new EventPayload(
                payload.title(),
                payload.description(),
                payload.longDescription(),
                payload.imageUrl(),
                payload.dateTime(),
                payload.location(),
                payload.capacity(),
                payload.price(),
                EventStatus.CANCELLED
        );

        Long createdEventId = eventRepository.findAll().stream()
                .filter(event -> event.getTitle().equals(payload.title()))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(put("/api/events/{id}", createdEventId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(cancelledUpdatePayload)))
                .andExpect(status().isConflict());

        EventPayload comingSoonUpdatePayload = new EventPayload(
                payload.title(),
                payload.description(),
                payload.longDescription(),
                payload.imageUrl(),
                payload.dateTime(),
                payload.location(),
                payload.capacity(),
                payload.price(),
                EventStatus.COMING_SOON
        );

        mockMvc.perform(put("/api/events/{id}", createdEventId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(comingSoonUpdatePayload)))
                .andExpect(status().isConflict());
    }

    @Test
    void getAllEventsHidesCancelledEventsOlderThanSevenDaysButKeepsRecentCancelledEvents() throws Exception {
        Event openEvent = eventRepository.save(new Event(
                "Visible Open " + UUID.randomUUID(),
                "Still visible",
                LocalDateTime.now().plusDays(5),
                "Munich",
                20,
                BigDecimal.valueOf(25.00)
        ));

        Event recentCancelledEvent = new Event(
                "Recent Cancelled " + UUID.randomUUID(),
                "Recently cancelled",
                LocalDateTime.now().plusDays(2),
                "Berlin",
                20,
                BigDecimal.valueOf(20.00)
        );
        recentCancelledEvent.setStatus(EventStatus.CANCELLED);
        recentCancelledEvent.setCancelledAt(LocalDateTime.now().minusDays(2));
        recentCancelledEvent = eventRepository.save(recentCancelledEvent);

        Event oldCancelledEvent = new Event(
                "Old Cancelled " + UUID.randomUUID(),
                "Old cancelled event",
                LocalDateTime.now().minusDays(20),
                "Hamburg",
                20,
                BigDecimal.valueOf(15.00)
        );
        oldCancelledEvent.setStatus(EventStatus.CANCELLED);
        oldCancelledEvent.setCancelledAt(LocalDateTime.now().minusDays(8));
        oldCancelledEvent = eventRepository.save(oldCancelledEvent);

        mockMvc.perform(get("/api/events")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(openEvent.getId())).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(recentCancelledEvent.getId())).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(oldCancelledEvent.getId())).isEmpty());
    }

    @Test
    void getAllEventsReturnsLifecycleStatusesInStableBusinessOrder() throws Exception {
        Event openEvent = eventRepository.save(new Event(
                "Ordered Open " + UUID.randomUUID(),
                "Open event",
                LocalDateTime.now().plusDays(1),
                "Munich",
                20,
                BigDecimal.valueOf(25.00)
        ));

        Event comingSoonEvent = new Event(
                "Ordered Coming Soon " + UUID.randomUUID(),
                "Coming soon event",
                LocalDateTime.now().plusDays(2),
                "Berlin",
                20,
                BigDecimal.valueOf(20.00)
        );
        comingSoonEvent.setStatus(EventStatus.COMING_SOON);
        comingSoonEvent = eventRepository.save(comingSoonEvent);

        Event closedEvent = new Event(
                "Ordered Closed " + UUID.randomUUID(),
                "Closed event",
                LocalDateTime.now().plusDays(3),
                "Hamburg",
                20,
                BigDecimal.valueOf(15.00)
        );
        closedEvent.setStatus(EventStatus.CLOSED);
        closedEvent = eventRepository.save(closedEvent);

        Event cancelledEvent = new Event(
                "Ordered Cancelled " + UUID.randomUUID(),
                "Cancelled event",
                LocalDateTime.now().plusDays(4),
                "Cologne",
                20,
                BigDecimal.valueOf(18.00)
        );
        cancelledEvent.setStatus(EventStatus.CANCELLED);
        cancelledEvent.setCancelledAt(LocalDateTime.now());
        cancelledEvent = eventRepository.save(cancelledEvent);

        mockMvc.perform(get("/api/events")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", contains("OPEN", "COMING_SOON", "CLOSED", "CANCELLED")));
    }

    @Test
    void adminCancelEventCancelsActiveBookingsWithEventReason() throws Exception {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String attendeeEmail = "attendee-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        createAdminUser(adminEmail, password);
        createAttendeeUser(attendeeEmail, password);

        User pendingUser = userRepository.save(
                new User("Pending", "User", "pending-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password), Role.ATTENDEE, true)
        );
        User confirmedUser = userRepository.save(
                new User("Confirmed", "User", "confirmed-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password), Role.ATTENDEE, true)
        );
        User cancelledUser = userRepository.save(
                new User("Cancelled", "User", "cancelled-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password), Role.ATTENDEE, true)
        );
        Event event = eventRepository.save(new Event(
                "Cancel Flow " + UUID.randomUUID(),
                "Event for cancellation test",
                LocalDateTime.now().plusDays(10),
                "Munich",
                20,
                BigDecimal.valueOf(25.00)
        ));

        Booking pendingBooking = createBooking(pendingUser, event, BookingStatus.PENDING);
        Booking confirmedBooking = createBooking(confirmedUser, event, BookingStatus.CONFIRMED);
        Booking alreadyCancelledBooking = createBooking(cancelledUser, event, BookingStatus.CANCELLED);

        MockHttpSession attendeeSession = login(attendeeEmail, password);
        mockMvc.perform(patch("/api/events/{id}/cancel", event.getId())
                        .session(attendeeSession))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(adminEmail, password);
        mockMvc.perform(patch("/api/events/{id}/cancel", event.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.availableSpots").value(event.getCapacity()))
                .andExpect(jsonPath("$.bookedCount").value(0))
                .andExpect(jsonPath("$.cancelledConfirmedCount").value(1));

        mockMvc.perform(post("/api/bookings")
                        .session(attendeeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(event.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/events/{id}/cancel", event.getId())
                        .session(adminSession))
                .andExpect(status().isConflict());

        Booking pendingAfter = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, pendingAfter.getStatus());
        assertEquals(BookingStatus.PENDING, pendingAfter.getCancelledFromStatus());
        assertEquals(BookingCancellationReason.EVENT_CANCELLED, pendingAfter.getCancellationReason());
        assertNotNull(pendingAfter.getCancelledAt());

        Booking confirmedAfter = bookingRepository.findById(confirmedBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, confirmedAfter.getStatus());
        assertEquals(BookingStatus.CONFIRMED, confirmedAfter.getCancelledFromStatus());
        assertEquals(BookingCancellationReason.EVENT_CANCELLED, confirmedAfter.getCancellationReason());
        assertNotNull(confirmedAfter.getCancelledAt());

        Booking alreadyCancelledAfter = bookingRepository.findById(alreadyCancelledBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, alreadyCancelledAfter.getStatus());
    }

    @Test
    void openEventRouteOnlyAllowsAdminToOpenComingSoonEvents() throws Exception {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String attendeeEmail = "attendee-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        createAdminUser(adminEmail, password);
        createAttendeeUser(attendeeEmail, password);

        Event comingSoonEvent = new Event(
                "Coming Soon " + UUID.randomUUID(),
                "Coming soon event",
                LocalDateTime.now().plusDays(7),
                "Munich",
                30,
                BigDecimal.valueOf(20.00)
        );
        comingSoonEvent.setStatus(EventStatus.COMING_SOON);
        comingSoonEvent = eventRepository.save(comingSoonEvent);

        Event openEvent = eventRepository.save(new Event(
                "Already Open " + UUID.randomUUID(),
                "Open event",
                LocalDateTime.now().plusDays(8),
                "Berlin",
                25,
                BigDecimal.valueOf(18.00)
        ));

        Event closedEvent = new Event(
                "Closed " + UUID.randomUUID(),
                "Closed event",
                LocalDateTime.now().plusDays(10),
                "Cologne",
                15,
                BigDecimal.valueOf(19.00)
        );
        closedEvent.setStatus(EventStatus.CLOSED);
        closedEvent = eventRepository.save(closedEvent);

        Event cancelledEvent = new Event(
                "Cancelled " + UUID.randomUUID(),
                "Cancelled event",
                LocalDateTime.now().plusDays(9),
                "Hamburg",
                20,
                BigDecimal.valueOf(22.00)
        );
        cancelledEvent.setStatus(EventStatus.CANCELLED);
        cancelledEvent.setCancelledAt(LocalDateTime.now());
        cancelledEvent = eventRepository.save(cancelledEvent);

        MockHttpSession attendeeSession = login(attendeeEmail, password);
        mockMvc.perform(patch("/api/events/{id}/open", comingSoonEvent.getId())
                        .session(attendeeSession))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(adminEmail, password);
        mockMvc.perform(patch("/api/events/{id}/open", comingSoonEvent.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(patch("/api/events/{id}/open", closedEvent.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(patch("/api/events/{id}/open", openEvent.getId())
                        .session(adminSession))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/events/{id}/open", cancelledEvent.getId())
                        .session(adminSession))
                .andExpect(status().isConflict());
    }

    @Test
    void closeEventRouteOnlyAllowsAdminToCloseOpenEvents() throws Exception {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String attendeeEmail = "attendee-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        createAdminUser(adminEmail, password);
        createAttendeeUser(attendeeEmail, password);
        User pendingUser = userRepository.save(
                new User("Pending", "Closer", "pending-close-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password), Role.ATTENDEE, true)
        );
        User confirmedUser = userRepository.save(
                new User("Confirmed", "Closer", "confirmed-close-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password), Role.ATTENDEE, true)
        );
        User cancelledUser = userRepository.save(
                new User("Cancelled", "Closer", "cancelled-close-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password), Role.ATTENDEE, true)
        );

        Event openEvent = eventRepository.save(new Event(
                "Closable Open " + UUID.randomUUID(),
                "Open event to close",
                LocalDateTime.now().plusDays(7),
                "Munich",
                30,
                BigDecimal.valueOf(20.00)
        ));
        Booking pendingBooking = createBooking(pendingUser, openEvent, BookingStatus.PENDING);
        Booking confirmedBooking = createBooking(confirmedUser, openEvent, BookingStatus.CONFIRMED);
        Booking cancelledBooking = createBooking(cancelledUser, openEvent, BookingStatus.CANCELLED);

        Event comingSoonEvent = new Event(
                "Coming Soon Close " + UUID.randomUUID(),
                "Coming soon event",
                LocalDateTime.now().plusDays(8),
                "Berlin",
                25,
                BigDecimal.valueOf(18.00)
        );
        comingSoonEvent.setStatus(EventStatus.COMING_SOON);
        comingSoonEvent = eventRepository.save(comingSoonEvent);

        Event cancelledEvent = new Event(
                "Cancelled Close " + UUID.randomUUID(),
                "Cancelled event",
                LocalDateTime.now().plusDays(9),
                "Hamburg",
                20,
                BigDecimal.valueOf(22.00)
        );
        cancelledEvent.setStatus(EventStatus.CANCELLED);
        cancelledEvent.setCancelledAt(LocalDateTime.now());
        cancelledEvent = eventRepository.save(cancelledEvent);

        MockHttpSession attendeeSession = login(attendeeEmail, password);
        mockMvc.perform(patch("/api/events/{id}/close", openEvent.getId())
                        .session(attendeeSession))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(adminEmail, password);
        mockMvc.perform(patch("/api/events/{id}/close", openEvent.getId())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.bookedCount").value(1));

        Booking pendingAfter = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, pendingAfter.getStatus());
        assertEquals(BookingStatus.PENDING, pendingAfter.getCancelledFromStatus());
        assertEquals(BookingCancellationReason.EVENT_CLOSED, pendingAfter.getCancellationReason());
        assertNotNull(pendingAfter.getCancelledAt());

        Booking confirmedAfter = bookingRepository.findById(confirmedBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, confirmedAfter.getStatus());

        Booking cancelledAfter = bookingRepository.findById(cancelledBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, cancelledAfter.getStatus());

        mockMvc.perform(post("/api/bookings")
                        .session(attendeeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(openEvent.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/events/{id}/close", openEvent.getId())
                        .session(adminSession))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/events/{id}/close", comingSoonEvent.getId())
                        .session(adminSession))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/events/{id}/close", cancelledEvent.getId())
                        .session(adminSession))
                .andExpect(status().isConflict());
    }

    private void createAttendeeUser(String email, String password) {
        userRepository.save(new User("First", "Last", email, passwordEncoder.encode(password), Role.ATTENDEE, true));
    }

    private void createAdminUser(String email, String password) {
        userRepository.save(new User("Admin", "User", email, passwordEncoder.encode(password), Role.ADMIN, true));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String eventJson(EventPayload payload) {
        return """
                {
                  "title": "%s",
                  "description": "%s",
                  "longDescription": "%s",
                  "imageUrl": "%s",
                  "dateTime": "%s",
                  "location": "%s",
                  "capacity": %d,
                  "price": %s,
                  "status": "%s"
                }
                """.formatted(
                payload.title(),
                payload.description(),
                payload.longDescription(),
                payload.imageUrl(),
                payload.dateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                payload.location(),
                payload.capacity(),
                payload.price(),
                payload.status().name()
        );
    }

    private String bookingJson(Long eventId) {
        return """
                {
                  "eventId": %d
                }
                """.formatted(eventId);
    }

    private Booking createBooking(User user, Event event, BookingStatus status) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setBookedAt(LocalDateTime.now().minusMinutes(1));
        booking.setStatus(status);
        booking.setPriceAtBooking(event.getPrice());
        return bookingRepository.save(booking);
    }

    private record EventPayload(
            String title,
            String description,
            String longDescription,
            String imageUrl,
            LocalDateTime dateTime,
            String location,
            Integer capacity,
            BigDecimal price,
            EventStatus status
    ) {
    }
}
