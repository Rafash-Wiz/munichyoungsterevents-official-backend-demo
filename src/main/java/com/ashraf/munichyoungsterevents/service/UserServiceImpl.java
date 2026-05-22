package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.UserDTO;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.mapper.UserMapper;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toDTO);
    }

    @Override
    public Page<UserDTO> getAllUsers(Role role, Pageable pageable) {
        return getAllUsers(role, null, null, null, pageable);
    }

    @Override
    public Page<UserDTO> getAllUsers(Role role, Long id, String firstName, String lastName, Pageable pageable) {
        if (role == null && id == null && isBlank(firstName) && isBlank(lastName)) {
            return getAllUsers(pageable);
        }

        Specification<User> specification = (root, query, cb) -> cb.conjunction();

        if (role != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }

        if (id != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("id"), id));
        }

        if (!isBlank(firstName)) {
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("firstName")), "%" + firstName.trim().toLowerCase() + "%"));
        }

        if (!isBlank(lastName)) {
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("lastName")), "%" + lastName.trim().toLowerCase() + "%"));
        }

        return userRepository.findAll(specification, pageable)
                .map(userMapper::toDTO);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public UserDTO getUserById(Long id) {
        return userMapper.toDTO(findUserById(id));
    }

    @Override
    public UserDTO getMyUser() {
        return userMapper.toDTO(getCurrentUser());
    }

    @Override
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User existing = findUserById(id);
        validateEmailUniqueness(userDTO.getEmail(), existing.getId());

        existing.setFirstName(userDTO.getFirstName());
        existing.setLastName(userDTO.getLastName());
        existing.setEmail(userDTO.getEmail());

        return userMapper.toDTO(userRepository.save(existing));
    }

    @Override
    public UserDTO updateMyUser(UserDTO userDTO) {
        User currentUser = getCurrentUser();
        validateEmailUniqueness(userDTO.getEmail(), currentUser.getId());

        currentUser.setFirstName(userDTO.getFirstName());
        currentUser.setLastName(userDTO.getLastName());
        currentUser.setEmail(userDTO.getEmail());

        return userMapper.toDTO(userRepository.save(currentUser));
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + id));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));
    }

    private void validateEmailUniqueness(String email, Long currentUserId) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentUserId)) {
                throw new ConflictException("Email already registered");
            }
        });
    }
}
