package com.puchkov.urlshortener.service;

import com.puchkov.urlshortener.entity.ShortUrl;
import com.puchkov.urlshortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final ShortUrlRepository repository;
    private final Random random = new Random();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.short-code.characters:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789}")
    private String characters;

    @Value("${app.short-code.length:6}")
    private int codeLength;

    @Transactional
    public ShortUrl shortenUrl(String originalUrl, Long expirationDays) {
        Optional<ShortUrl> existing = repository.findByOriginalUrl(originalUrl);
        if (existing.isPresent()) {
            return existing.get();
        }

        String shortCode = generateUniqueShortCode();
        ShortUrl shortUrl = new ShortUrl(originalUrl, shortCode);

        if (expirationDays != null && expirationDays > 0) {
            shortUrl.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));
        }

        return repository.save(shortUrl);
    }

    @Transactional
    public Optional<ShortUrl> getOriginalUrl(String shortCode) {
        Optional<ShortUrl> shortUrl = repository.findByShortCode(shortCode);

        if (shortUrl.isPresent()) {
            ShortUrl url = shortUrl.get();

            if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
                repository.delete(url);
                return Optional.empty();
            }

            repository.incrementClickCount(shortCode);
            url.incrementClickCount();
        }

        return shortUrl;
    }

    public String buildShortUrl(String shortCode) {
        return baseUrl + "/" + shortCode;
    }

    private String generateShortCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }
        return code.toString();
    }

    private String generateUniqueShortCode() {
        String shortCode;
        do {
            shortCode = generateShortCode();
        } while (repository.existsByShortCode(shortCode));

        return shortCode;
    }
}
