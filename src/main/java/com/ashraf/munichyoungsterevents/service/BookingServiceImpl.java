package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.*;
import com.ashraf.munichyoungsterevents.exception.BadRequestException;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.mapper.BookingMapper;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final EventRepository eventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    // User = both security identity and booking owner in the domain.

    public BookingServiceImpl(BookingRepository bookingRepository,
                              BookingMapper bookingMapper, EventRepository eventRepository,
                              JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.eventRepository = eventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public BookingDTO createBooking(BookingDTO bookingDTO) {
        validateBookingRequest(bookingDTO);
        User bookingUser = resolveBookingUser(bookingDTO);

        jdbcTemplate.execute("SET LOCAL lock_timeout = '3s'");

        Event event = eventRepository.findByIdForUpdate(bookingDTO.getEventId())
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + bookingDTO.getEventId()));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new ConflictException("Booking is not available for this event yet");
        }

        if (bookingRepository.existsByUserIdAndEventIdAndStatusNot(
                bookingUser.getId(), event.getId(), BookingStatus.CANCELLED)) {
            throw new ConflictException("Active booking already exists for this user and event");
        }

        long activeBookings = bookingRepository.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED);
        if (activeBookings >= event.getCapacity()) {
            throw new ConflictException("Event is fully booked");
        }

        Booking booking = new Booking();
        booking.setUser(bookingUser);
        booking.setEvent(event);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookedAt(LocalDateTime.now());
        booking.setPriceAtBooking(event.getPrice());

        try {
            return bookingMapper.toDTO(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Active booking already exists for this user and event");
        }
    }

    @Override
    public Page<BookingDTO> getAllBookings(Long userId, Long eventId, BookingStatus status, Pageable pageable) {
        if (userId != null && eventId != null && status != null) {
            return bookingRepository.findByUserIdAndEventIdAndStatusOrderByBookedAtDescIdDesc(userId, eventId, status, pageable)
                    .map(bookingMapper::toDTO);
        }

        if (userId != null && eventId != null) {
            return bookingRepository.findByUserIdAndEventIdOrderByBookedAtDescIdDesc(userId, eventId, pageable)
                    .map(bookingMapper::toDTO);
        }

        if (userId != null && status != null) {
            return bookingRepository.findByUserIdAndStatusOrderByBookedAtDescIdDesc(userId, status, pageable)
                    .map(bookingMapper::toDTO);
        }

        if (eventId != null && status != null) {
            return bookingRepository.findByEventIdAndStatusOrderByBookedAtDescIdDesc(eventId, status, pageable)
                    .map(bookingMapper::toDTO);
        }

        if (userId != null) {
            return bookingRepository.findByUserIdOrderByBookedAtDescIdDesc(userId, pageable)
                    .map(bookingMapper::toDTO);
        }

        if (eventId != null) {
            return bookingRepository.findByEventIdOrderByBookedAtDescIdDesc(eventId, pageable)
                    .map(bookingMapper::toDTO);
        }

        if (status != null) {
            return bookingRepository.findByStatusOrderByBookedAtDescIdDesc(status, pageable)
                    .map(bookingMapper::toDTO);
        }

        return bookingRepository.findAllByOrderByBookedAtDescIdDesc(pageable)
                .map(bookingMapper::toDTO);
    }

    @Override
    public BookingDTO getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));

        validateBookingOwnershipIfNeeded(booking);
        return bookingMapper.toDTO(booking);
    }

    @Override
    public Page<BookingDTO> getMyBookings(Pageable pageable) {
        User currentUser = getCurrentUserIfAuthenticated();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        // This endpoint is intentionally attendee-only so semantics stay clear.
        if (currentUser.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Admins cannot use /api/bookings/me");
        }

        return bookingRepository.findByUserIdOrderByBookedAtDescIdDesc(currentUser.getId(), pageable)
                .map(bookingMapper::toDTO);
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

        Booking booking = bookingRepository.findByUserIdAndEventIdAndStatus(
                        currentUser.getId(),
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

        User currentUser = getCurrentUserIfAuthenticated();

        booking.setCancelledFromStatus(booking.getStatus());
        booking.setCancelledAt(LocalDateTime.now());

        if (currentUser != null && currentUser.getRole() == Role.ADMIN) {
            booking.setCancellationReason(BookingCancellationReason.ADMIN_ACTION);
        } else {
            booking.setCancellationReason(BookingCancellationReason.USER_REQUEST);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingMapper.toDTO(bookingRepository.save(booking));
    }

    private void validateBookingRequest(BookingDTO bookingDTO) {
        if (bookingDTO.getEventId() == null) {
            throw new BadRequestException("Event id is required");
        }
    }

    private void validateBookingOwnershipIfNeeded(Booking booking) {
        User currentUser = getCurrentUserIfAuthenticated();
        if (currentUser == null || currentUser.getRole() == Role.ADMIN) {
            return;
        }

        if (booking.getUser() == null || !currentUser.getId().equals(booking.getUser().getId())) {
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

    // Resolve the booking owner from the authenticated user.
    // Attendee users must book only for themselves, so any client-supplied userId
    // is ignored for them. Admin users may still create bookings on behalf of any
    // attendee user by using the request userId.
    private User resolveBookingUser(BookingDTO bookingDTO) {
        User currentUser = getCurrentUserIfAuthenticated();

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        if (currentUser.getRole() == Role.ADMIN) {
            if (bookingDTO.getUserId() == null) {
                throw new BadRequestException("User id is required for admin-created bookings");
            }
            User targetUser = userRepository.findById(bookingDTO.getUserId())
                    .orElseThrow(() -> new NotFoundException("User not found with id: " + bookingDTO.getUserId()));
            if (targetUser.getRole() != Role.ATTENDEE) {
                throw new ConflictException("Bookings can only be created for attendee users");
            }
            return targetUser;
        }

        if (currentUser.getRole() != Role.ATTENDEE) {
            throw new AccessDeniedException("Only attendee users can create their own bookings");
        }

        return currentUser;
    }
}
