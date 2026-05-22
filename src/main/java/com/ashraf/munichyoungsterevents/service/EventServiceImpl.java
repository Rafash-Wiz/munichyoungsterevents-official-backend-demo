package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.EventDTO;
import com.ashraf.munichyoungsterevents.entity.Booking;
import com.ashraf.munichyoungsterevents.entity.BookingCancellationReason;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.entity.EventStatus;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.mapper.EventMapper;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final BookingRepository bookingRepository;

    public EventServiceImpl(EventRepository eventRepository, EventMapper eventMapper, BookingRepository bookingRepository) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public EventDTO createEvent(EventDTO eventDTO) {
        Event event = eventMapper.toEntity(eventDTO);
        Event savedEvent = eventRepository.save(event);
        return toEventDTOWithAvailableSpots(savedEvent);
    }

    @Override
    public Page<EventDTO> getAllEvents(Pageable pageable) {
        LocalDateTime cancelledVisibilityCutoff = LocalDateTime.now().minusDays(7);
        return eventRepository.findVisibleForListing(EventStatus.CANCELLED, cancelledVisibilityCutoff, pageable)
                .map(this::toEventDTOWithAvailableSpots);

    }

    @Override
    public EventDTO getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + id));

        return toEventDTOWithAvailableSpots(event);
    }

    @Override
    public EventDTO updateEvent(Long id, EventDTO eventDTO) {
        Event existingEvent = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + id));

        if (eventDTO.getStatus() != existingEvent.getStatus()) {
            throw new ConflictException("Event status cannot be changed through event update");
        }

        existingEvent.setTitle(eventDTO.getTitle());
        existingEvent.setDescription(eventDTO.getDescription());
        existingEvent.setLongDescription(eventDTO.getLongDescription());
        existingEvent.setImageUrl(eventDTO.getImageUrl());
        existingEvent.setDateTime(eventDTO.getDateTime());
        existingEvent.setLocation(eventDTO.getLocation());
        existingEvent.setCapacity(eventDTO.getCapacity());
        existingEvent.setPrice(eventDTO.getPrice());

        Event updatedEvent = eventRepository.save(existingEvent);
        return toEventDTOWithAvailableSpots(updatedEvent);
    }

    @Override
    @Transactional
    public EventDTO cancelEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + id));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Event is already cancelled");
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        List<Booking> eventBookings = bookingRepository.findByEventIdOrderByBookedAtDescIdDesc(event.getId());
        for (Booking booking : eventBookings) {
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }

            booking.setCancelledFromStatus(booking.getStatus());
            booking.setCancellationReason(BookingCancellationReason.EVENT_CANCELLED);
            booking.setCancelledAt(cancelledAt);
            booking.setStatus(BookingStatus.CANCELLED);
        }
        bookingRepository.saveAll(eventBookings);

        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(cancelledAt);
        Event cancelledEvent = eventRepository.save(event);
        return toEventDTOWithAvailableSpots(cancelledEvent);
    }

    @Override
    public EventDTO openEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + id));

        if (event.getStatus() == EventStatus.OPEN) {
            throw new ConflictException("Event is already open");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Cancelled events cannot be reopened");
        }

        if (event.getStatus() != EventStatus.COMING_SOON && event.getStatus() != EventStatus.CLOSED) {
            throw new ConflictException("Only coming soon or closed events can be opened");
        }

        event.setStatus(EventStatus.OPEN);
        Event openedEvent = eventRepository.save(event);
        return toEventDTOWithAvailableSpots(openedEvent);
    }

    @Override
    @Transactional
    public EventDTO closeEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + id));

        if (event.getStatus() == EventStatus.CLOSED) {
            throw new ConflictException("Event is already closed");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Cancelled events cannot be closed");
        }

        if (event.getStatus() != EventStatus.OPEN) {
            throw new ConflictException("Only open events can be closed");
        }

        LocalDateTime closedAt = LocalDateTime.now();
        List<Booking> eventBookings = bookingRepository.findByEventIdOrderByBookedAtDescIdDesc(event.getId());
        for (Booking booking : eventBookings) {
            if (booking.getStatus() != BookingStatus.PENDING) {
                continue;
            }

            booking.setCancelledFromStatus(BookingStatus.PENDING);
            booking.setCancellationReason(BookingCancellationReason.EVENT_CLOSED);
            booking.setCancelledAt(closedAt);
            booking.setStatus(BookingStatus.CANCELLED);
        }
        bookingRepository.saveAll(eventBookings);

        event.setStatus(EventStatus.CLOSED);
        Event closedEvent = eventRepository.save(event);
        return toEventDTOWithAvailableSpots(closedEvent);
    }

    @Override
    public void deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new NotFoundException("Event not found with id: " + id);
        }

        eventRepository.deleteById(id);
    }

    private EventDTO toEventDTOWithAvailableSpots(Event event) {
        EventDTO eventDTO = eventMapper.toDTO(event);
        long activeBookings = bookingRepository.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED);
        long confirmedBookings = bookingRepository.countByEventIdAndStatus(event.getId(), BookingStatus.CONFIRMED);
        long pendingBookings = bookingRepository.countByEventIdAndStatus(event.getId(), BookingStatus.PENDING);
        long cancelledConfirmedBookings = bookingRepository.countByEventIdAndStatusAndCancellationReasonAndCancelledFromStatus(
                event.getId(),
                BookingStatus.CANCELLED,
                BookingCancellationReason.EVENT_CANCELLED,
                BookingStatus.CONFIRMED
        );
        eventDTO.setAvailableSpots((int) (event.getCapacity() - activeBookings));
        eventDTO.setBookedCount((int) activeBookings);
        eventDTO.setConfirmedCount((int) confirmedBookings);
        eventDTO.setPendingCount((int) pendingBookings);
        eventDTO.setCancelledConfirmedCount((int) cancelledConfirmedBookings);
        return eventDTO;
    }
}
