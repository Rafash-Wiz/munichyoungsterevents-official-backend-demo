package com.ashraf.munichyoungsterevents.controller;

import com.ashraf.munichyoungsterevents.dto.EventDTO;
import com.ashraf.munichyoungsterevents.dto.PageResponseDTO;
import jakarta.validation.Valid;
import com.ashraf.munichyoungsterevents.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@Valid @RequestBody EventDTO eventDTO) {
        EventDTO createdEvent = eventService.createEvent(eventDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<EventDTO>> getAllEvents(Pageable pageable) {
        Page<EventDTO> eventPage = eventService.getAllEvents(pageable);
        return ResponseEntity.ok(PageResponseDTO.from(eventPage));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(@PathVariable Long id, @Valid @RequestBody EventDTO eventDTO) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventDTO));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<EventDTO> cancelEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.cancelEvent(id));
    }

    @PatchMapping("/{id}/open")
    public ResponseEntity<EventDTO> openEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.openEvent(id));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<EventDTO> closeEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.closeEvent(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}
