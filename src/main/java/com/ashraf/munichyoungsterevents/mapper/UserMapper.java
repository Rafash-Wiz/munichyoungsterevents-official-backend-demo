package com.ashraf.munichyoungsterevents.mapper;

import com.ashraf.munichyoungsterevents.dto.UserDTO;
import com.ashraf.munichyoungsterevents.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDTO toDTO(User user);

    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    User toEntity(UserDTO userDTO);

    List<UserDTO> toDTOList(List<User> users);
}
