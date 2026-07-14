package com.indivaragroup.ageninlite.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownlinerDto {

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("phone_number")
    private String phoneNumber;

}
