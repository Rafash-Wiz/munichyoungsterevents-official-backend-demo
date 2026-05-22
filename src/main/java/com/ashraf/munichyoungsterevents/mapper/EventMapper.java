package com.ashraf.munichyoungsterevents.mapper;

import com.ashraf.munichyoungsterevents.dto.EventDTO;
import com.ashraf.munichyoungsterevents.entity.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "availableSpots", ignore = true)
    @Mapping(target = "bookedCount", ignore = true)
    @Mapping(target = "confirmedCount", ignore = true)
    @Mapping(target = "pendingCount", ignore = true)
    @Mapping(target = "cancelledConfirmedCount", ignore = true)
    EventDTO toDTO(Event event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    Event toEntity(EventDTO eventDTO);

    List<EventDTO> toDTOList(List<Event> events);
}
