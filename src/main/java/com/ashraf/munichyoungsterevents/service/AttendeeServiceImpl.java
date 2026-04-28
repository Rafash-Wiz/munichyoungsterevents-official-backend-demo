package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.AttendeeDTO;
import com.ashraf.munichyoungsterevents.entity.Attendee;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.mapper.AttendeeMapper;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttendeeServiceImpl implements AttendeeService{

    private final AttendeeRepository attendeeRepository;
    private final AttendeeMapper attendeeMapper;
    private final UserRepository userRepository;

    public AttendeeServiceImpl(AttendeeRepository attendeeRepository, AttendeeMapper attendeeMapper,
                               UserRepository userRepository) {
        this.attendeeRepository = attendeeRepository;
        this.attendeeMapper = attendeeMapper;
        this.userRepository = userRepository;
    }

    @Override
    public AttendeeDTO createAttendee(AttendeeDTO attendeeDTO) {

        if (attendeeRepository.existsByEmail(attendeeDTO.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        Attendee attendee = attendeeMapper.toEntity(attendeeDTO);
        Attendee saved = attendeeRepository.save(attendee);

        return attendeeMapper.toDTO(saved);
    }

    @Override
    public List<AttendeeDTO> getAllAttendees() {
        return attendeeMapper.toDTOList(attendeeRepository.findAll());
    }

    @Override
    public AttendeeDTO getAttendeeById(Long id) {
        Attendee attendee = attendeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Attendee not found with id: " + id));

        return attendeeMapper.toDTO(attendee);
    }

    @Override
    public AttendeeDTO getMyAttendee() {
        return attendeeMapper.toDTO(getCurrentUserAttendee());
    }

    @Override
    public AttendeeDTO updateAttendee(Long id, AttendeeDTO attendeeDTO) {
        Attendee existing = attendeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Attendee not found with id: " + id));

        if (!existing.getEmail().equals(attendeeDTO.getEmail()) &&
                attendeeRepository.existsByEmail(attendeeDTO.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        existing.setFirstName(attendeeDTO.getFirstName());
        existing.setLastName(attendeeDTO.getLastName());
        existing.setEmail(attendeeDTO.getEmail());

        Attendee updated = attendeeRepository.save(existing);

        return attendeeMapper.toDTO(updated);
    }

    @Override
    public AttendeeDTO updateMyAttendee(AttendeeDTO attendeeDTO) {
        Attendee attendee = getCurrentUserAttendee();

        if (!attendee.getEmail().equals(attendeeDTO.getEmail())
                && attendeeRepository.existsByEmail(attendeeDTO.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        attendee.setFirstName(attendeeDTO.getFirstName());
        attendee.setLastName(attendeeDTO.getLastName());
        attendee.setEmail(attendeeDTO.getEmail());

        Attendee updated = attendeeRepository.save(attendee);
        return attendeeMapper.toDTO(updated);
    }

    @Override
    public void deleteAttendee(Long id) {
        if (!attendeeRepository.existsById(id)) {
            throw new NotFoundException("Attendee not found with id: " + id);
        }

        attendeeRepository.deleteById(id);
    }

    private Attendee getCurrentUserAttendee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));

        if (user.getAttendee() == null) {
            throw new NotFoundException("No attendee profile is linked to the current user");
        }

        return user.getAttendee();
    }
}
