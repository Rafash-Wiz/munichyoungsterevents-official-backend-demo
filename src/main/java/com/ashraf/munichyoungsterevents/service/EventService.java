package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.EventDTO;

import java.util.List;

public interface EventService {

    EventDTO createEvent(EventDTO eventDTO);

    List<EventDTO> getAllEvents();

    EventDTO getEventById(Long id);

    EventDTO updateEvent(Long id, EventDTO eventDTO);

    void deleteEvent(Long id);
}
