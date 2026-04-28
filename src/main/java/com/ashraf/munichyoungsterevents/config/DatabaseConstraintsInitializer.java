package com.ashraf.munichyoungsterevents.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseConstraintsInitializer {

    @Bean
    public ApplicationRunner activeBookingUniqueIndexInitializer(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_active_attendee_event
                ON bookings (attendee_id, event_id)
                WHERE status <> 'CANCELLED'
                """);
    }
}
