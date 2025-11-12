package com.puchkov.urlshortener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puchkov.urlshortener.dto.UrlRequest;
import com.puchkov.urlshortener.dto.UrlResponse;
import com.puchkov.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Transactional
@DisplayName("Интеграционные тесты")
class UrlShortenerApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ShortUrlRepository repository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    @DisplayName("Создание короткой ссылки при валидном запросе")
    void shortenUrl_ShouldReturnShortUrl_WhenValidRequest() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("https://example.com");

        String content = mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortUrl").exists())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andExpect(jsonPath("$.shortCode").exists())
                .andReturn().getResponse().getContentAsString();

        UrlResponse response = objectMapper.readValue(content, UrlResponse.class);

        assertNotNull(response.getShortUrl());
        assertNotNull(response.getShortCode());
        assertEquals("https://example.com", response.getOriginalUrl());
        assertTrue(response.getShortUrl().contains(response.getShortCode()));
    }

    @Test
    @DisplayName("Возврат одинаковой короткой ссылки при повторном запросе того же URL")
    void shortenUrl_ShouldReturnSameShortUrl_WhenSameUrlRequested() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("https://example.com/same-url");

        String firstContent = mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UrlResponse firstResponse = objectMapper.readValue(firstContent, UrlResponse.class);

        String secondContent = mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UrlResponse secondResponse = objectMapper.readValue(secondContent, UrlResponse.class);

        assertEquals(firstResponse.getShortCode(), secondResponse.getShortCode());
        assertEquals(firstResponse.getShortUrl(), secondResponse.getShortUrl());
    }

    @Test
    @DisplayName("Редирект на оригинальный URL при валидном коротком коде")
    void redirect_ShouldRedirectToOriginalUrl_WhenValidShortCode() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("https://google.com");

        String shortenContent = mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UrlResponse response = objectMapper.readValue(shortenContent, UrlResponse.class);
        String shortCode = response.getShortCode();

        Long initialClickCount = repository.findByShortCode(shortCode)
                .orElseThrow()
                .getClickCount();

        mvc.perform(MockMvcRequestBuilders.get("/{shortCode}", shortCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://google.com"));

        Long updatedClickCount = repository.findByShortCode(shortCode)
                .orElseThrow()
                .getClickCount();
        assertEquals(initialClickCount + 1, updatedClickCount);
    }

    @Test
    @DisplayName("Возврат 404 при невалидном коротком коде")
    void redirect_ShouldReturnNotFound_WhenInvalidShortCode() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/{shortCode}", "nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Возврат 400 при пустом URL")
    void shortenUrl_ShouldReturnBadRequest_WhenUrlIsEmpty() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("");

        mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Возврат 400 при отсутствии URL в запросе")
    void shortenUrl_ShouldReturnBadRequest_WhenUrlIsMissing() throws Exception {
        String emptyJson = "{}";

        mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Создание короткой ссылки с сроком действия")
    void shortenUrl_WithExpiration_ShouldCreateUrlWithExpiration() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("https://example.com/expiring");
        request.setExpirationDays(7L);

        String content = mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UrlResponse response = objectMapper.readValue(content, UrlResponse.class);

        assertTrue(repository.findByShortCode(response.getShortCode()).isPresent());
        assertNotNull(repository.findByShortCode(response.getShortCode()).get().getExpiresAt());
    }


    @Test
    @DisplayName("Успешное создание короткой ссылки для длинного URL")
    void shortenUrl_ShouldWorkWithLongUrls() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("https://very-long-domain-name.com/very/long/path/with/many/parameters?param1=value1&param2=value2&param3=value3");

        mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").exists())
                .andExpect(jsonPath("$.originalUrl").value(request.getUrl()))
                .andExpect(jsonPath("$.shortCode").exists());
    }

    @Test
    @DisplayName("Короткий код должен состоять из правильных символов")
    void shortenUrl_ShouldGenerateValidShortCode() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setUrl("https://test.com");

        String content = mvc.perform(MockMvcRequestBuilders.post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UrlResponse response = objectMapper.readValue(content, UrlResponse.class);

        assertTrue(response.getShortCode().matches("[A-Za-z0-9]{6}"));
        assertEquals(6, response.getShortCode().length());
    }
}
