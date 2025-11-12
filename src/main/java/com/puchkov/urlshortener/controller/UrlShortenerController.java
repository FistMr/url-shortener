package com.puchkov.urlshortener.controller;

import com.puchkov.urlshortener.dto.UrlRequest;
import com.puchkov.urlshortener.dto.UrlResponse;
import com.puchkov.urlshortener.entity.ShortUrl;
import com.puchkov.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    @PostMapping("/shorten")
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody UrlRequest request) {
        try {
            ShortUrl shortUrl = urlShortenerService.shortenUrl(
                    request.getUrl(),
                    request.getExpirationDays()
            );

            UrlResponse response = new UrlResponse(
                    urlShortenerService.buildShortUrl(shortUrl.getShortCode()),
                    shortUrl.getOriginalUrl(),
                    shortUrl.getShortCode()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String shortCode) {
        Optional<ShortUrl> shortUrl = urlShortenerService.getOriginalUrl(shortCode);

        if (shortUrl.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", shortUrl.get().getOriginalUrl())
                    .build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
