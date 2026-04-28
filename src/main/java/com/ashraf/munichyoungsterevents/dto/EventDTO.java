package com.ashraf.munichyoungsterevents.dto;

import com.ashraf.munichyoungsterevents.entity.EventStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {

    private Long id;

    @NotBlank
    private String title;

    private String description;

    private String longDescription;

    private String imageUrl;

    @NotNull
    private LocalDateTime dateTime;

    @NotBlank
    private String location;

    @NotNull
    @Min(1)
    private Integer capacity;

    private Integer availableSpots;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    @NotNull
    private EventStatus status;
}
