package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;

import java.util.List;

public interface BookingService {

    BookingDTO createBooking(BookingDTO bookingDTO);

    List<BookingDTO> getAllBookings();

    List<BookingDTO> getAllBookings(Long attendeeId, BookingStatus status);

    BookingDTO getBookingById(Long id);

    List<BookingDTO> getMyBookings();

    List<BookingDTO> getMyBookings(BookingStatus status);

    BookingDTO getMyPendingBookingForEvent(Long eventId);

    BookingDTO confirmBooking(Long id);

    BookingDTO cancelBooking(Long id);
}
