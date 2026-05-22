package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.EventDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {

    EventDTO createEvent(EventDTO eventDTO);

    Page<EventDTO> getAllEvents(Pageable pageable);

    EventDTO getEventById(Long id);

    EventDTO updateEvent(Long id, EventDTO eventDTO);

    EventDTO cancelEvent(Long id);

    void deleteEvent(Long id);

    EventDTO openEvent(Long id);

    EventDTO closeEvent(Long id);
}
