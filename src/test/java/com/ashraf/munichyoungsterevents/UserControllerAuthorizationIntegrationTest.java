package com.ashraf.munichyoungsterevents;

import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "app.booking.pending-expiration-check-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE bookings, users, events RESTART IDENTITY CASCADE");
    }

    @Test
    void adminCanFilterUsersByRole() throws Exception {
        createUser("attendee-a-" + UUID.randomUUID() + "@example.com", "password123", Role.ATTENDEE, "Attendee", "One");
        createUser("attendee-b-" + UUID.randomUUID() + "@example.com", "password123", Role.ATTENDEE, "Attendee", "Two");
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "password123", Role.ADMIN, "Admin", "User");

        String adminToken = login(adminEmail, "password123");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken(adminToken))
                        .param("role", "ATTENDEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("ATTENDEE"))
                .andExpect(jsonPath("$.content[1].role").value("ATTENDEE"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void adminCanFilterUsersByIdFirstNameAndLastName() throws Exception {
        User targetUser = createUser("ash-" + UUID.randomUUID() + "@example.com", "password123", Role.ATTENDEE, "Ash", "Doe");
        createUser("other-" + UUID.randomUUID() + "@example.com", "password123", Role.ATTENDEE, "Other", "Person");
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "password123", Role.ADMIN, "Admin", "User");

        assertNotNull(targetUser.getId());
        String adminToken = login(adminEmail, "password123");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken(adminToken))
                        .param("role", "ATTENDEE")
                        .param("id", targetUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(targetUser.getId()))
                .andExpect(jsonPath("$.content[0].firstName").value("Ash"));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken(adminToken))
                        .param("role", "ATTENDEE")
                        .param("firstName", "as"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Ash"));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken(adminToken))
                        .param("role", "ATTENDEE")
                        .param("lastName", "do"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Doe"));
    }

    private User createUser(String email, String password, Role role, String firstName, String lastName) {
        return userRepository.save(new User(firstName, lastName, email, passwordEncoder.encode(password), role, true));
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

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
