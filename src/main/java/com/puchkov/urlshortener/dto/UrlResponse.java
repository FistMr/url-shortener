package com.puchkov.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class UrlResponse {
    private String shortUrl;
    private String originalUrl;
    private String shortCode;
}
