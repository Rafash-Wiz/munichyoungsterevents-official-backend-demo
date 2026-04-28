package com.ashraf.munichyoungsterevents.controller;

import com.ashraf.munichyoungsterevents.dto.AttendeeDTO;
import jakarta.validation.Valid;
import com.ashraf.munichyoungsterevents.service.AttendeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attendees")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;

    @PostMapping
    public ResponseEntity<AttendeeDTO> createAttendee(@Valid @RequestBody AttendeeDTO attendeeDTO) {
        AttendeeDTO createdAttendee = attendeeService.createAttendee(attendeeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAttendee);
    }

    @GetMapping
    public ResponseEntity<List<AttendeeDTO>> getAllAttendees() {
        return ResponseEntity.ok(attendeeService.getAllAttendees());
    }

    @GetMapping("/me")
    public ResponseEntity<AttendeeDTO> getMyAttendee() {
        return ResponseEntity.ok(attendeeService.getMyAttendee());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttendeeDTO> getAttendeeById(@PathVariable Long id) {
        return ResponseEntity.ok(attendeeService.getAttendeeById(id));
    }

    @PutMapping("/me")
    public ResponseEntity<AttendeeDTO> updateMyAttendee(@Valid @RequestBody AttendeeDTO attendeeDTO) {
        return ResponseEntity.ok(attendeeService.updateMyAttendee(attendeeDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttendeeDTO> updateAttendee(@PathVariable Long id, @Valid @RequestBody AttendeeDTO attendeeDTO) {
        return ResponseEntity.ok(attendeeService.updateAttendee(id, attendeeDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttendee(@PathVariable Long id) {
        attendeeService.deleteAttendee(id);
        return ResponseEntity.noContent().build();
    }
}
