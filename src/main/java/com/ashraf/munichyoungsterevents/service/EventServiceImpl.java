package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.EventDTO;
import com.ashraf.munichyoungsterevents.entity.BookingStatus;
import com.ashraf.munichyoungsterevents.entity.Event;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.mapper.EventMapper;
import com.ashraf.munichyoungsterevents.repository.BookingRepository;
import com.ashraf.munichyoungsterevents.repository.EventRepository;
import org.springframework.stereotype.Service;

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
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(this::toEventDTOWithAvailableSpots)
                .toList();
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

        existingEvent.setTitle(eventDTO.getTitle());
        existingEvent.setDescription(eventDTO.getDescription());
        existingEvent.setLongDescription(eventDTO.getLongDescription());
        existingEvent.setImageUrl(eventDTO.getImageUrl());
        existingEvent.setDateTime(eventDTO.getDateTime());
        existingEvent.setLocation(eventDTO.getLocation());
        existingEvent.setCapacity(eventDTO.getCapacity());
        existingEvent.setPrice(eventDTO.getPrice());
        existingEvent.setStatus(eventDTO.getStatus());

        Event updatedEvent = eventRepository.save(existingEvent);
        return toEventDTOWithAvailableSpots(updatedEvent);
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
        eventDTO.setAvailableSpots((int) (event.getCapacity() - activeBookings));
        return eventDTO;
    }
}
