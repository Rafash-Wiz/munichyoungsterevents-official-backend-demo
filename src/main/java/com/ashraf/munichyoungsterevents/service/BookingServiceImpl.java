package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.*;
import com.ashraf.munichyoungsterevents.exception.BadRequestException;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.mapper.BookingMapper;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private final AttendeeRepository attendeeRepository;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final EventRepository eventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    // User = security identity.
    // Attendee = booking owner in the domain.

    public BookingServiceImpl(AttendeeRepository attendeeRepository, BookingRepository bookingRepository,
                              BookingMapper bookingMapper, EventRepository eventRepository,
                              JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.attendeeRepository = attendeeRepository;
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.eventRepository = eventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public BookingDTO createBooking(BookingDTO bookingDTO) {
        validateBookingRequest(bookingDTO);

//        Attendee attendee = attendeeRepository.findById(bookingDTO.getAttendeeId())
//                .orElseThrow(() -> new NotFoundException("Attendee not found with id: " + bookingDTO.getAttendeeId()));
        Attendee attendee = resolveBookingAttendee(bookingDTO);

        jdbcTemplate.execute("SET LOCAL lock_timeout = '3s'");

        Event event = eventRepository.findByIdForUpdate(bookingDTO.getEventId())
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + bookingDTO.getEventId()));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new ConflictException("Booking is not available for this event yet");
        }

        if (bookingRepository.existsByAttendeeIdAndEventIdAndStatusNot(
                attendee.getId(), event.getId(), BookingStatus.CANCELLED)) {
            throw new ConflictException("Active booking already exists for this attendee and event");
        }

        long activeBookings = bookingRepository.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED);
        if (activeBookings >= event.getCapacity()) {
            throw new ConflictException("Event is fully booked");
        }

        Booking booking = new Booking();
        booking.setAttendee(attendee);
        booking.setEvent(event);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookedAt(LocalDateTime.now());
        booking.setPriceAtBooking(event.getPrice());

        try {
            return bookingMapper.toDTO(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Active booking already exists for this attendee and event");
        }
    }

    @Override
    public List<BookingDTO> getAllBookings() {
        return getAllBookings(null, null);
    }

    @Override
    public List<BookingDTO> getAllBookings(Long attendeeId, BookingStatus status) {
        if (attendeeId != null && status != null) {
            return bookingMapper.toDTOList(
                    bookingRepository.findByAttendeeIdAndStatusOrderByBookedAtDesc(attendeeId, status)
            );
        }

        if (attendeeId != null) {
            return bookingMapper.toDTOList(bookingRepository.findByAttendeeIdOrderByBookedAtDesc(attendeeId));
        }

        if (status != null) {
            return bookingMapper.toDTOList(bookingRepository.findByStatusOrderByBookedAtDesc(status));
        }

        return bookingMapper.toDTOList(bookingRepository.findAllByOrderByBookedAtDesc());
    }

    @Override
    public BookingDTO getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));

        validateBookingOwnershipIfNeeded(booking);
        return bookingMapper.toDTO(booking);
    }

    @Override
    public List<BookingDTO> getMyBookings() {
        return getMyBookings(null);
    }

    @Override
    public List<BookingDTO> getMyBookings(BookingStatus status) {
        User currentUser = getCurrentUserIfAuthenticated();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        // This endpoint is intentionally attendee-only so semantics stay clear.
        if (currentUser.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Admins cannot use /api/bookings/me");
        }

        if (currentUser.getAttendee() == null) {
            throw new NotFoundException("No attendee profile is linked to the current user");
        }

        Long attendeeId = currentUser.getAttendee().getId();

        // Optional status filter for attendee self view, still sorted newest first.
        if (status != null) {
            return bookingMapper.toDTOList(
                    bookingRepository.findByAttendeeIdAndStatusOrderByBookedAtDesc(attendeeId, status)
            );
        }

        return bookingMapper.toDTOList(bookingRepository.findByAttendeeIdOrderByBookedAtDesc(attendeeId));
    }

    @Override
    public BookingDTO getMyPendingBookingForEvent(Long eventId) {
        User currentUser = getCurrentUserIfAuthenticated();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        if (currentUser.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Admins cannot use attendee pending-booking lookup");
        }

        if (currentUser.getAttendee() == null) {
            throw new NotFoundException("No attendee profile is linked to the current user");
        }

        Booking booking = bookingRepository.findByAttendeeIdAndEventIdAndStatus(
                        currentUser.getAttendee().getId(),
                        eventId,
                        BookingStatus.PENDING
                )
                .orElseThrow(() -> new NotFoundException("No pending booking found for this event"));

        return bookingMapper.toDTO(booking);
    }

    @Override
    public BookingDTO confirmBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Only PENDING bookings can be confirmed");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingMapper.toDTO(bookingRepository.save(booking));
    }

    @Override
    public BookingDTO cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        validateBookingOwnershipIfNeeded(booking);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking is already cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingMapper.toDTO(bookingRepository.save(booking));
    }

    private void validateBookingRequest(BookingDTO bookingDTO) {
        if (bookingDTO.getAttendeeId() == null) {
            throw new BadRequestException("Attendee id is required");
        }

        if (bookingDTO.getEventId() == null) {
            throw new BadRequestException("Event id is required");
        }
    }

//    private void validateAttendeeOwnershipIfNeeded(Attendee attendee) {
//        User currentUser = getCurrentUserIfAuthenticated();
//        if (currentUser == null || currentUser.getRole() == Role.ADMIN) {
//            return;
//        }
//
//        if (currentUser.getAttendee() == null || !currentUser.getAttendee().getId().equals((attendee.getId()))) {
//            throw new AccessDeniedException("You can only create bookings for your own attendee profile");
//        }
//    }

    private void validateBookingOwnershipIfNeeded(Booking booking) {
        User currentUser = getCurrentUserIfAuthenticated();
        if (currentUser == null || currentUser.getRole() == Role.ADMIN) {
            return;
        }

        if (currentUser.getAttendee() == null
                || booking.getAttendee() == null
                || !currentUser.getAttendee().getId().equals(booking.getAttendee().getId())) {
            throw new AccessDeniedException("You can only access your own bookings");
        }
    }

    private User getCurrentUserIfAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    // Resolve the attendee for booking creation based on the authenticated user.
    // Attendee users must book only for their own linked attendee profile, so we
    // intentionally ignore any client-supplied attendeeId for them. Admin users
    // may still create bookings on behalf of any attendee by using the request id.
    private Attendee resolveBookingAttendee(BookingDTO bookingDTO) {
        User currentUser = getCurrentUserIfAuthenticated();

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        if (currentUser.getRole() == Role.ADMIN) {
            return attendeeRepository.findById(bookingDTO.getAttendeeId())
                    .orElseThrow(() -> new NotFoundException("Attendee not found with id: " + bookingDTO.getAttendeeId()));
        }

        if (currentUser.getAttendee() == null) {
            throw new NotFoundException("No attendee profile is linked to the current user");
        }

        return currentUser.getAttendee();
    }
}
