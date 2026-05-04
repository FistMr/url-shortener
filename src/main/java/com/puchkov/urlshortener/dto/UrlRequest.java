package com.puchkov.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UrlRequest {

    @NotBlank(message = "URL is required")
    private String url;

    private Long expirationDays;
}
