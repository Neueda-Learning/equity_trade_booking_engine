package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.InstrumentSearchProvider;
import com.equitytrade.booking.marketdata.domain.InstrumentSearchResult;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketTicker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FinnhubInstrumentSearchProvider
        implements InstrumentSearchProvider {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FinnhubInstrumentSearchProvider.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUrl;
    private final String apiKey;
    private final Duration readTimeout;
    private final int maxAttempts;

    public FinnhubInstrumentSearchProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI baseUrl,
            String apiKey,
            Duration readTimeout,
            int maxAttempts) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.readTimeout = readTimeout;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public List<InstrumentSearchResult> search(
            String query,
            int limit) {
        MarketDataProviderException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return searchOnce(query, limit);
            } catch (MarketDataProviderException exception) {
                failure = exception;
                if (!exception.retryable() || attempt == maxAttempts) {
                    throw exception;
                }
                LOGGER.warn(
                        "Finnhub instrument search failed with {}; "
                                + "retrying attempt {} of {}",
                        exception.category(),
                        attempt + 1,
                        maxAttempts);
            }
        }
        throw failure == null
                ? failure(
                        MarketDataFailureCategory.UNKNOWN,
                        "Finnhub instrument search failed")
                : failure;
    }

    private List<InstrumentSearchResult> searchOnce(
            String query,
            int limit) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(searchUri(query))
                .timeout(readTimeout)
                .header("Accept", "application/json")
                .header("X-Finnhub-Token", apiKey)
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw failure(
                    MarketDataFailureCategory.TIMEOUT,
                    "Finnhub instrument search timed out",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(
                    MarketDataFailureCategory.CONNECTION,
                    "Finnhub instrument search was interrupted",
                    exception);
        } catch (IOException exception) {
            throw failure(
                    MarketDataFailureCategory.CONNECTION,
                    "Finnhub instrument search connection failed",
                    exception);
        }
        return handleResponse(response, limit);
    }

    private List<InstrumentSearchResult> handleResponse(
            HttpResponse<String> response,
            int limit) {
        int status = response.statusCode();
        if (status == 200) {
            return parse(response.body(), limit);
        }
        if (status == 429) {
            throw failure(
                    MarketDataFailureCategory.RATE_LIMIT,
                    "Finnhub rate limit reached");
        }
        if (status == 401 || status == 403) {
            LOGGER.warn(
                    "Finnhub instrument search authentication failed with "
                            + "HTTP {}; verify provider configuration",
                    status);
            throw failure(
                    MarketDataFailureCategory.AUTHENTICATION,
                    "Finnhub authentication failed");
        }
        if (status == 400 || status == 404) {
            throw failure(
                    MarketDataFailureCategory.NOT_FOUND,
                    "Finnhub has no matching instrument");
        }
        if (status >= 500) {
            throw failure(
                    MarketDataFailureCategory.SERVER_ERROR,
                    "Finnhub instrument search failed");
        }
        throw failure(
                MarketDataFailureCategory.CLIENT_ERROR,
                "Finnhub rejected instrument search");
    }

    private List<InstrumentSearchResult> parse(
            String body,
            int limit) {
        final FinnhubInstrumentSearchResponse response;
        try {
            response = objectMapper.readValue(
                    body,
                    FinnhubInstrumentSearchResponse.class);
        } catch (JsonProcessingException exception) {
            throw failure(
                    MarketDataFailureCategory.MALFORMED_RESPONSE,
                    "Finnhub returned malformed instrument search JSON",
                    exception);
        }
        if (response.result() == null) {
            return List.of();
        }
        LinkedHashMap<String, InstrumentSearchResult> byTicker =
                response.result().stream()
                .filter(this::supported)
                .map(result -> new InstrumentSearchResult(
                        MarketTicker.normalize(result.symbol()),
                        result.description().strip(),
                        "US",
                        normalizedType(result.type())))
                .collect(Collectors.toMap(
                        InstrumentSearchResult::ticker,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return byTicker.values().stream().limit(limit).toList();
    }

    private boolean supported(
            FinnhubInstrumentSearchResponse.Result result) {
        if (result.symbol() == null
                || result.description() == null
                || result.type() == null) {
            return false;
        }
        try {
            MarketTicker.normalize(result.symbol());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String type = result.type().toUpperCase(Locale.ROOT);
        return type.contains("COMMON STOCK")
                || type.equals("ADR")
                || type.equals("ETF")
                || type.equals("ETP");
    }

    private String normalizedType(String type) {
        String normalized = type.strip();
        return "ETP".equalsIgnoreCase(normalized) ? "ETF" : normalized;
    }

    private URI searchUri(String query) {
        String separator = baseUrl.toString().endsWith("/") ? "" : "/";
        return URI.create(
                baseUrl
                        + separator
                        + "search?q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&exchange=US");
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
