package com.ashraf.munichyoungsterevents.mapper;

import com.ashraf.munichyoungsterevents.dto.BookingDTO;
import com.ashraf.munichyoungsterevents.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(source = "attendee.id", target = "attendeeId")
    @Mapping(source = "event.id", target = "eventId")
    @Mapping(source = "event.title", target = "eventTitle")
    @Mapping(source = "event.imageUrl", target = "eventImageUrl")
    @Mapping(source = "event.dateTime", target = "eventDateTime")
    @Mapping(source = "event.location", target = "eventLocation")
    BookingDTO toDTO(Booking booking);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "attendee", ignore = true)
    @Mapping(target = "event", ignore = true)
    Booking toEntity(BookingDTO bookingDTO);

    List<BookingDTO> toDTOList(List<Booking> bookings);
}
