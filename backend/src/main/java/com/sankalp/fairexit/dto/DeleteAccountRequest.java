package com.sankalp.fairexit.dto;

import jakarta.validation.constraints.NotBlank;

/** The password again, so a stolen access token alone cannot delete an account. */
public record DeleteAccountRequest(@NotBlank String password) {
}
