package com.tester.dto;
import jakarta.validation.constraints.*;
public record RegisterRequestDTO(
 @NotBlank @Size(max=255) String name,
 @NotBlank @Email @Size(max=255) String email,
 @NotEmpty String password) {}
