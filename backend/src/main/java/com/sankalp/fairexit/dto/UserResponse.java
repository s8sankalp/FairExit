package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, String name) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
