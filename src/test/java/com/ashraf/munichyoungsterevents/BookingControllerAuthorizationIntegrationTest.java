package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE bookings, users, events RESTART IDENTITY CASCADE");
    }

    @Test
    void attendeeSeesOnlyOwnBookingsAndCannotReadAnotherUsersBooking() throws Exception {
        Event event = createEvent();
        User attendeeA = createAttendeeUser("booking-a-" + UUID.randomUUID() + "@example.com", "password123");
        User attendeeB = createAttendeeUser("booking-b-" + UUID.randomUUID() + "@example.com", "password123");

        String attendeeAToken = login(attendeeA.getEmail(), "password123");
        MvcResult bookingAResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerToken(attendeeAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(attendeeA.getId(), event.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(attendeeA.getId().toString()))
                .andReturn();

        String bookingAId = readId(bookingAResult);

        String attendeeBToken = login(attendeeB.getEmail(), "password123");
        MvcResult bookingBResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerToken(attendeeBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(attendeeA.getId(), event.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(attendeeB.getId().toString()))
                .andReturn();

        String bookingBId = readId(bookingBResult);

        mockMvc.perform(get("/api/bookings/me")
                        .header("Authorization", bearerToken(attendeeAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(bookingAId))
                .andExpect(jsonPath("$.content[0].userId").value(attendeeA.getId().toString()))
                .andExpect(jsonPath("$.content[0].eventTitle").value(event.getTitle()))
                .andExpect(jsonPath("$.content[0].eventLocation").value(event.getLocation()))
                .andExpect(jsonPath("$.number").value(0));

        mockMvc.perform(get("/api/bookings/me/pending/" + event.getId())
                        .header("Authorization", bearerToken(attendeeAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingAId))
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.eventTitle").value(event.getTitle()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/bookings/me")
                        .header("Authorization", bearerToken(attendeeBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(bookingBId))
                .andExpect(jsonPath("$.content[0].userId").value(attendeeB.getId().toString()));

        mockMvc.perform(get("/api/bookings/" + bookingAId)
                        .header("Authorization", bearerToken(attendeeBToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingRoutesRespectAdminAndAttendeeAccessRules() throws Exception {
        Event event = createEvent();
        Event secondEvent = createEvent();
        User attendee = createAttendeeUser("booking-owner-" + UUID.randomUUID() + "@example.com", "password123");
        createAdminUser("booking-admin-" + UUID.randomUUID() + "@example.com", "password123");

        String attendeeToken = login(attendee.getEmail(), "password123");
        MvcResult bookingResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerToken(attendeeToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(attendee.getId(), event.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerToken(attendeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(attendee.getId(), secondEvent.getId())))
                .andExpect(status().isCreated());

        String bookingId = readId(bookingResult);

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", bearerToken(attendeeToken)))
                .andExpect(status().isForbidden());

        String adminToken = login(userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow()
                .getEmail(), "password123");

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.number").value(0));

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", bearerToken(adminToken))
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(bookingId))
                .andExpect(jsonPath("$.content[0].eventId").value(event.getId().toString()));

        mockMvc.perform(patch("/api/bookings/" + bookingId + "/confirm")
                        .header("Authorization", bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void adminBookingPaginationIsStableAcrossPagesWhenBookedAtTimestampsMatch() throws Exception {
        Event event = createEvent();
        createAdminUser("booking-admin-" + UUID.randomUUID() + "@example.com", "password123");

        LocalDateTime sharedBookedAt = LocalDateTime.now().minusHours(2);
        for (int i = 0; i < 12; i++) {
            User attendee = createAttendeeUser("stable-page-" + i + "-" + UUID.randomUUID() + "@example.com", "password123");
            Booking booking = new Booking();
            booking.setUser(attendee);
            booking.setEvent(event);
            booking.setBookedAt(sharedBookedAt);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPriceAtBooking(event.getPrice());
            bookingRepository.save(booking);
        }

        String adminToken = login(userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow()
                .getEmail(), "password123");

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", bearerToken(adminToken))
                        .param("eventId", event.getId().toString())
                        .param("status", "CONFIRMED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[0].id").value(12))
                .andExpect(jsonPath("$.content[9].id").value(3))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", bearerToken(adminToken))
                        .param("eventId", event.getId().toString())
                        .param("status", "CONFIRMED")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[1].id").value(1));
    }

    private Event createEvent() {
        Event event = new Event(
                "HTTP Booking Event " + UUID.randomUUID(),
                "HTTP authorization test event",
                LocalDateTime.now().plusDays(10),
                "Munich",
                10,
                BigDecimal.valueOf(25.00)
        );
        return eventRepository.save(event);
    }

    private User createAttendeeUser(String email, String password) {
        return userRepository.save(new User("First", "Last", email, passwordEncoder.encode(password), Role.ATTENDEE, true));
    }

    private void createAdminUser(String email, String password) {
        userRepository.save(new User("Admin", "User", email, passwordEncoder.encode(password), Role.ADMIN, true));
    }

    private String login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return readToken(loginResult);
    }

    private String readToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    private String readId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"id\"\\s*:\\s*([0-9]+).*", "$1");
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String bookingJson(Long userId, Long eventId) {
        return """
                {
                  "userId": "%s",
                  "eventId": "%s"
                }
                """.formatted(userId, eventId);
    }
}
