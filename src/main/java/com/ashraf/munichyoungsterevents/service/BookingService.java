package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingService {

    BookingDTO createBooking(BookingDTO bookingDTO);

    Page<BookingDTO> getAllBookings(Long userId, Long eventId, BookingStatus status, Pageable pageable);

    BookingDTO getBookingById(Long id);

    Page<BookingDTO> getMyBookings(Pageable pageable);

    BookingDTO getMyPendingBookingForEvent(Long eventId);

    BookingDTO confirmBooking(Long id);

    BookingDTO cancelBooking(Long id);
}
