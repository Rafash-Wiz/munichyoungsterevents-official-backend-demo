package com.ashraf.munichyoungsterevents.mapper;

import com.ashraf.munichyoungsterevents.dto.AttendeeDTO;
import com.ashraf.munichyoungsterevents.entity.Attendee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AttendeeMapper {

    AttendeeDTO toDTO(Attendee attendee);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    Attendee toEntity(AttendeeDTO attendeeDTO);

    List<AttendeeDTO> toDTOList(List<Attendee> attendees);
}
