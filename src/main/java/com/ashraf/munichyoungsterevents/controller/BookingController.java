package com.ashraf.munichyoungsterevents.controller;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.dto.PageResponseDTO;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingDTO> createBooking(@Valid @RequestBody BookingDTO bookingDTO) {
        BookingDTO createdBooking = bookingService.createBooking(bookingDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBooking);
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<BookingDTO>> getAllBookings(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) BookingStatus status,
            Pageable pageable) {
        Page<BookingDTO> bookingPage = bookingService.getAllBookings(userId, eventId, status, pageable);
        return ResponseEntity.ok(PageResponseDTO.from(bookingPage));
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponseDTO<BookingDTO>> getMyBookings(Pageable pageable) {
        Page<BookingDTO> bookingPage = bookingService.getMyBookings(pageable);
        return ResponseEntity.ok(PageResponseDTO.from(bookingPage));
    }

    @GetMapping("/me/pending/{eventId}")
    public ResponseEntity<BookingDTO> getMyPendingBookingForEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(bookingService.getMyPendingBookingForEvent(eventId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDTO> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<BookingDTO> confirmBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.confirmBooking(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingDTO> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }


}
