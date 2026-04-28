package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.AttendeeDTO;

import java.util.List;

public interface AttendeeService {

    AttendeeDTO createAttendee(AttendeeDTO attendeeDTO);

    List<AttendeeDTO> getAllAttendees();

    AttendeeDTO getAttendeeById(Long id);

    AttendeeDTO getMyAttendee();

    AttendeeDTO updateAttendee(Long id, AttendeeDTO attendeeDTO);

    AttendeeDTO updateMyAttendee(AttendeeDTO attendeeDTO);

    void deleteAttendee(Long id);
}
