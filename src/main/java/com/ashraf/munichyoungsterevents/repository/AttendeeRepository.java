package com.ashraf.munichyoungsterevents.repository;

import com.ashraf.munichyoungsterevents.entity.Attendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendeeRepository extends JpaRepository<Attendee, Long> {
    Optional<Attendee> findByEmail(String email);
    boolean existsByEmail(String email);
}
