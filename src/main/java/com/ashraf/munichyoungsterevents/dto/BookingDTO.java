package com.ashraf.munichyoungsterevents.dto;

import com.ashraf.munichyoungsterevents.entity.BookingStatus;
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
public class BookingDTO {

    private Long id;

    @NotNull
    private Long attendeeId;

    @NotNull
    private Long eventId;

    private String eventTitle;
    private String eventImageUrl;
    private LocalDateTime eventDateTime;
    private String eventLocation;

    private LocalDateTime bookedAt;
    private BookingStatus status;
    private BigDecimal priceAtBooking;
}
