package com.puchkov.urlshortener.service;

import com.puchkov.urlshortener.entity.ShortUrl;
import com.puchkov.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты для UrlShortenerService")
class UrlShortenerServiceTest {

    @Mock
    private ShortUrlRepository repository;

    @InjectMocks
    private UrlShortenerService urlShortenerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlShortenerService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(urlShortenerService, "characters",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        ReflectionTestUtils.setField(urlShortenerService, "codeLength", 6);
    }

    @Test
    @DisplayName("Должен вернуть существующую короткую ссылку, когда URL уже существует")
    void shortenUrl_ShouldReturnExistingShortUrl_WhenUrlAlreadyExists() {
        String originalUrl = "https://example.com";
        ShortUrl existingUrl = new ShortUrl(originalUrl, "abc123");
        when(repository.findByOriginalUrl(originalUrl)).thenReturn(Optional.of(existingUrl));

        ShortUrl result = urlShortenerService.shortenUrl(originalUrl, null);

        assertEquals(existingUrl, result);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Должен создать новую короткую ссылку, когда URL новый")
    void shortenUrl_ShouldCreateNewShortUrl_WhenUrlIsNew() {
        String originalUrl = "https://example.com";
        when(repository.findByOriginalUrl(originalUrl)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(any())).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = urlShortenerService.shortenUrl(originalUrl, null);

        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNotNull(result.getShortCode());
        assertEquals(6, result.getShortCode().length());
        verify(repository).save(any(ShortUrl.class));
    }

    @Test
    @DisplayName("Должен вернуть пустой Optional, когда короткий код не найден")
    void getOriginalUrl_ShouldReturnEmpty_WhenShortCodeNotFound() {
        String shortCode = "nonexistent";
        when(repository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        Optional<ShortUrl> result = urlShortenerService.getOriginalUrl(shortCode);

        assertTrue(result.isEmpty());
    }
    @Test
    @DisplayName("Должен увеличить счетчик кликов при получении оригинального URL")
    void getOriginalUrl_ShouldIncrementClickCount_WhenShortCodeExists() {
        String shortCode = "abc123";
        ShortUrl shortUrl = new ShortUrl("https://example.com", shortCode);
        when(repository.findByShortCode(shortCode)).thenReturn(Optional.of(shortUrl));

        Optional<ShortUrl> result = urlShortenerService.getOriginalUrl(shortCode);

        assertTrue(result.isPresent());
        verify(repository).incrementClickCount(shortCode);
    }

    @Test
    @DisplayName("Должен удалить просроченную ссылку и вернуть пустой Optional")
    void getOriginalUrl_ShouldDeleteAndReturnEmpty_WhenUrlExpired() {
        String shortCode = "expired";
        ShortUrl expiredUrl = new ShortUrl("https://example.com", shortCode);
        expiredUrl.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        when(repository.findByShortCode(shortCode)).thenReturn(Optional.of(expiredUrl));

        Optional<ShortUrl> result = urlShortenerService.getOriginalUrl(shortCode);

        assertTrue(result.isEmpty());
        verify(repository).delete(expiredUrl);
    }

    @Test
    @DisplayName("Должен создать короткую ссылку с сроком действия")
    void shortenUrl_ShouldCreateUrlWithExpiration_WhenExpirationDaysProvided() {
        String originalUrl = "https://example.com";
        Long expirationDays = 7L;
        when(repository.findByOriginalUrl(originalUrl)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(any())).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = urlShortenerService.shortenUrl(originalUrl, expirationDays);

        assertNotNull(result);
        assertNotNull(result.getExpiresAt());
        verify(repository).save(any(ShortUrl.class));
    }

    @Test
    @DisplayName("Должен сгенерировать корректный короткий URL")
    void buildShortUrl_ShouldReturnCorrectFormat() {
        String shortCode = "test123";

        String result = urlShortenerService.buildShortUrl(shortCode);

        assertEquals("http://localhost:8080/test123", result);
    }
}