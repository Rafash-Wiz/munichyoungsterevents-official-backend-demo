package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.entity.Attendee;
import com.ashraf.munichyoungsterevents.entity.EventStatus;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
@AutoConfigureMockMvc
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
    private AttendeeRepository attendeeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE bookings, attendees, users, events RESTART IDENTITY CASCADE");
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
    }

    private void createAttendeeUser(String email, String password) {
        User user = new User(email, passwordEncoder.encode(password), Role.ATTENDEE, true);
        User savedUser = userRepository.save(user);

        Attendee attendee = new Attendee("First", "Last", email);
        attendee.setUser(savedUser);
        Attendee savedAttendee = attendeeRepository.save(attendee);
        savedUser.setAttendee(savedAttendee);
        userRepository.save(savedUser);
    }

    private void createAdminUser(String email, String password) {
        userRepository.save(new User(email, passwordEncoder.encode(password), Role.ADMIN, true));
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
