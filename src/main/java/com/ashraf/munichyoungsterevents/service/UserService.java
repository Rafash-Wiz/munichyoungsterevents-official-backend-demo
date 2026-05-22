package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.UserDTO;
import com.ashraf.munichyoungsterevents.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserDTO> getAllUsers(Pageable pageable);

    Page<UserDTO> getAllUsers(Role role, Pageable pageable);

    Page<UserDTO> getAllUsers(Role role, Long id, String firstName, String lastName, Pageable pageable);

    UserDTO getUserById(Long id);

    UserDTO getMyUser();

    UserDTO updateUser(Long id, UserDTO userDTO);

    UserDTO updateMyUser(UserDTO userDTO);

    void deleteUser(Long id);
}
