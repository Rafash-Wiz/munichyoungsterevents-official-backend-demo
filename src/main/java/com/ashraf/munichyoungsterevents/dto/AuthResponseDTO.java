package com.ashraf.munichyoungsterevents.dto;

import com.ashraf.munichyoungsterevents.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {

    private String token;

    private Long userId;

    private String email;

    private Role role;

    private String firstName;

    private String lastName;
}
