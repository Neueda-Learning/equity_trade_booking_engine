package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.DailyMarketPrice;
import com.equitytrade.booking.marketdata.domain.HistoricalMarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class FinnhubHistoricalMarketDataProvider
        implements HistoricalMarketDataProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUrl;
    private final String apiKey;
    private final Duration readTimeout;
    private final int maxAttempts;

    public FinnhubHistoricalMarketDataProvider(
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
    public List<DailyMarketPrice> fetchDailyCloses(
            String ticker,
            LocalDate fromInclusive,
            LocalDate toInclusive) {
        if (fromInclusive.isAfter(toInclusive)) {
            throw new IllegalArgumentException(
                    "Historical price start must not follow end");
        }
        MarketDataProviderException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return fetchOnce(ticker, fromInclusive, toInclusive);
            } catch (MarketDataProviderException exception) {
                failure = exception;
                if (!exception.retryable() || attempt == maxAttempts) {
                    throw exception;
                }
            }
        }
        throw failure == null
                ? failure(
                        MarketDataFailureCategory.UNKNOWN,
                        "Finnhub historical request failed")
                : failure;
    }

    private List<DailyMarketPrice> fetchOnce(
            String ticker,
            LocalDate fromInclusive,
            LocalDate toInclusive) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(candleUri(ticker, fromInclusive, toInclusive))
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
                    "Finnhub historical request timed out",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(
                    MarketDataFailureCategory.CONNECTION,
                    "Finnhub historical request was interrupted",
                    exception);
        } catch (IOException exception) {
            throw failure(
                    MarketDataFailureCategory.CONNECTION,
                    "Finnhub historical connection failed",
                    exception);
        }
        return handleResponse(ticker, response);
    }

    private List<DailyMarketPrice> handleResponse(
            String ticker,
            HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 200) {
            return parse(ticker, response.body());
        }
        if (status == 429) {
            throw failure(
                    MarketDataFailureCategory.RATE_LIMIT,
                    "Finnhub historical rate limit reached");
        }
        if (status == 401 || status == 403) {
            throw failure(
                    MarketDataFailureCategory.AUTHENTICATION,
                    "Finnhub historical data is not authorized");
        }
        if (status == 400 || status == 404) {
            throw failure(
                    MarketDataFailureCategory.NOT_FOUND,
                    "Finnhub has no historical data for ticker");
        }
        if (status >= 500) {
            throw failure(
                    MarketDataFailureCategory.SERVER_ERROR,
                    "Finnhub historical service failed");
        }
        throw failure(
                MarketDataFailureCategory.CLIENT_ERROR,
                "Finnhub rejected the historical request");
    }

    private List<DailyMarketPrice> parse(String ticker, String body) {
        FinnhubCandleResponse response;
        try {
            response = objectMapper.readValue(
                    body,
                    FinnhubCandleResponse.class);
        } catch (JsonProcessingException exception) {
            throw failure(
                    MarketDataFailureCategory.MALFORMED_RESPONSE,
                    "Finnhub returned malformed historical JSON",
                    exception);
        }
        if ("no_data".equals(response.s())) {
            return List.of();
        }
        if (!"ok".equals(response.s())
                || response.c() == null
                || response.t() == null
                || response.c().size() != response.t().size()) {
            throw failure(
                    MarketDataFailureCategory.MALFORMED_RESPONSE,
                    "Finnhub returned invalid historical data");
        }
        List<DailyMarketPrice> prices = new ArrayList<>();
        for (int index = 0; index < response.c().size(); index++) {
            if (response.c().get(index) == null
                    || response.c().get(index).signum() <= 0
                    || response.t().get(index) == null
                    || response.t().get(index) <= 0) {
                throw failure(
                        MarketDataFailureCategory.MALFORMED_RESPONSE,
                        "Finnhub returned an invalid historical candle");
            }
            LocalDate date = Instant.ofEpochSecond(response.t().get(index))
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
            prices.add(new DailyMarketPrice(
                    ticker,
                    date,
                    response.c().get(index),
                    "FINNHUB",
                    false));
        }
        return List.copyOf(prices);
    }

    private URI candleUri(
            String ticker,
            LocalDate fromInclusive,
            LocalDate toInclusive) {
        long from = fromInclusive.atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond();
        long to = toInclusive.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .minusSeconds(1)
                .toEpochSecond();
        String separator = baseUrl.toString().endsWith("/") ? "" : "/";
        return URI.create(
                baseUrl
                        + separator
                        + "stock/candle?symbol="
                        + URLEncoder.encode(
                                ticker,
                                StandardCharsets.UTF_8)
                        + "&resolution=D&from="
                        + from
                        + "&to="
                        + to);
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
