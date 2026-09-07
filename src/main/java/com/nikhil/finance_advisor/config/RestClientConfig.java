package com.nikhil.finance_advisor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * One RestTemplate for the whole application, with timeouts.
     *
     * Without a read timeout a stalled upstream call holds its request thread
     * indefinitely, and enough of them exhaust the pool and take the API down.
     */
    @Bean
    public RestTemplate groqRestTemplate(
            @Value("${groq.api.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${groq.api.read-timeout-seconds:60}") long readTimeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return new RestTemplate(factory);
    }
}
