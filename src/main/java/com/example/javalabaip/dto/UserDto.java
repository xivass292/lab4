package com.example.javalabaip.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserDto {
    private Long id;
    private String username;
    private List<LocationResponseDto> locations = new ArrayList<>();
}