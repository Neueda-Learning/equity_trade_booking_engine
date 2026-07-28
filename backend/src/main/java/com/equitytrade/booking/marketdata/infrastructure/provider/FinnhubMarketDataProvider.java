package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class FinnhubMarketDataProvider implements MarketDataProvider {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FinnhubMarketDataProvider.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final URI baseUrl;
    private final String apiKey;
    private final Duration readTimeout;
    private final int maxAttempts;

    public FinnhubMarketDataProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock,
            URI baseUrl,
            String apiKey,
            Duration readTimeout,
            int maxAttempts) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.readTimeout = readTimeout;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public MarketQuote fetch(String ticker) {
        MarketDataProviderException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return fetchOnce(ticker);
            } catch (MarketDataProviderException exception) {
                failure = exception;
                if (!exception.retryable() || attempt == maxAttempts) {
                    throw exception;
                }
                LOGGER.warn(
                        "Finnhub request failed with {}; retrying attempt {} of {}",
                        exception.category(),
                        attempt + 1,
                        maxAttempts);
            }
        }
        throw failure == null
                ? failure(
                        MarketDataFailureCategory.UNKNOWN,
                        "Finnhub request failed")
                : failure;
    }

    private MarketQuote fetchOnce(String ticker) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(quoteUri(ticker))
                .timeout(readTimeout)
                .header("Accept", "application/json")
                .header("X-Finnhub-Token", apiKey)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw failure(
                    MarketDataFailureCategory.TIMEOUT,
                    "Finnhub request timed out",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(
                    MarketDataFailureCategory.CONNECTION,
                    "Finnhub request was interrupted",
                    exception);
        } catch (IOException exception) {
            throw failure(
                    MarketDataFailureCategory.CONNECTION,
                    "Finnhub connection failed",
                    exception);
        }
        return handleResponse(ticker, response);
    }

    private MarketQuote handleResponse(
            String ticker,
            HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 200) {
            return parse(ticker, response.body());
        }
        if (status == 429) {
            throw failure(
                    MarketDataFailureCategory.RATE_LIMIT,
                    "Finnhub rate limit reached");
        }
        if (status == 401 || status == 403) {
            LOGGER.warn(
                    "Finnhub authentication failed with HTTP {}; "
                            + "verify provider configuration",
                    status);
            throw failure(
                    MarketDataFailureCategory.AUTHENTICATION,
                    "Finnhub authentication failed");
        }
        if (status == 400 || status == 404) {
            throw failure(
                    MarketDataFailureCategory.NOT_FOUND,
                    "Finnhub has no quote for ticker");
        }
        if (status >= 500) {
            throw failure(
                    MarketDataFailureCategory.SERVER_ERROR,
                    "Finnhub service failed");
        }
        throw failure(
                MarketDataFailureCategory.CLIENT_ERROR,
                "Finnhub rejected the request");
    }

    private MarketQuote parse(String ticker, String body) {
        FinnhubQuoteResponse response;
        try {
            response = objectMapper.readValue(
                    body,
                    FinnhubQuoteResponse.class);
        } catch (JsonProcessingException exception) {
            throw failure(
                    MarketDataFailureCategory.MALFORMED_RESPONSE,
                    "Finnhub returned malformed JSON",
                    exception);
        }
        if (response.c() == null
                || response.pc() == null
                || response.c().signum() <= 0
                || response.pc().signum() <= 0) {
            throw failure(
                    MarketDataFailureCategory.NOT_FOUND,
                    "Finnhub returned no usable quote");
        }
        Instant marketTimestamp;
        try {
            if (response.t() == null || response.t() <= 0) {
                throw new IllegalArgumentException();
            }
            marketTimestamp = Instant.ofEpochSecond(response.t());
        } catch (RuntimeException exception) {
            throw failure(
                    MarketDataFailureCategory.MALFORMED_RESPONSE,
                    "Finnhub returned an invalid timestamp",
                    exception);
        }
        return new MarketQuote(
                ticker,
                response.c(),
                response.pc(),
                marketTimestamp,
                clock.instant(),
                "FINNHUB",
                false);
    }

    private URI quoteUri(String ticker) {
        String separator = baseUrl.toString().endsWith("/") ? "" : "/";
        return URI.create(
                baseUrl
                        + separator
                        + "quote?symbol="
                        + URLEncoder.encode(
                                ticker,
                                StandardCharsets.UTF_8));
    }

    private MarketDataProviderException failure(
            MarketDataFailureCategory category,
            String message) {
        return new MarketDataProviderException(category, message);
    }

    private MarketDataProviderException failure(
            MarketDataFailureCategory category,
            String message,
            Throwable cause) {
        return new MarketDataProviderException(category, message, cause);
    }
}
