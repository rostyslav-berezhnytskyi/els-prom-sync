package com.els.promsync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@Slf4j
@Service
public class CurrencyRateService {

    private static final int USD = 840;
    private static final int UAH = 980;
    private static final Duration CACHE_TTL = Duration.ofDays(1);

    private final RestClient restClient = RestClient.create("https://api.monobank.ua");

    @Value("${app.exchange-rate:44.5}")
    private BigDecimal fallbackExchangeRate;

    @Value("${app.use-monobank-rate:true}")
    private boolean useMonobankRate;

    private BigDecimal cachedUsdRate;
    private Instant lastUpdatedAt;

    /**
     * Returns USD/UAH sell rate.
     *
     * Uses Monobank if enabled and available.
     * Falls back to application.yml rate if Monobank is unavailable.
     */
    public synchronized BigDecimal getUsdSellRate() {
        if (!useMonobankRate) {
            return fallbackExchangeRate;
        }

        if (isCacheValid()) {
            return cachedUsdRate;
        }

        try {
            MonoCurrencyRate[] rates = restClient.get()
                    .uri("/bank/currency")
                    .retrieve()
                    .body(MonoCurrencyRate[].class);

            BigDecimal monoRate = findUsdUahRate(rates);

            if (monoRate != null) {
                cachedUsdRate = monoRate;
                lastUpdatedAt = Instant.now();

//                log.info("Loaded USD/UAH rate from Monobank: {}", cachedUsdRate);
                System.out.println("Loaded USD/UAH rate from Monobank: " + cachedUsdRate);
                return cachedUsdRate;
            }

            log.warn("USD/UAH rate was not found in Monobank response. Using fallback rate: {}", fallbackExchangeRate);
            return fallbackExchangeRate;

        } catch (Exception e) {
            log.warn("Cannot load USD/UAH rate from Monobank. Using fallback rate: {}", fallbackExchangeRate);
            return fallbackExchangeRate;
        }
    }

    private boolean isCacheValid() {
        if (cachedUsdRate == null || lastUpdatedAt == null) {
            return false;
        }

        return Instant.now().isBefore(lastUpdatedAt.plus(CACHE_TTL));
    }

    private BigDecimal findUsdUahRate(MonoCurrencyRate[] rates) {
        if (rates == null) {
            return null;
        }

        return Arrays.stream(rates)
                .filter(rate -> rate.currencyCodeA() == USD)
                .filter(rate -> rate.currencyCodeB() == UAH)
                .map(this::selectBestRate)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal selectBestRate(MonoCurrencyRate rate) {
        if (rate.rateSell() != null) {
            return rate.rateSell();
        }

        if (rate.rateCross() != null) {
            return rate.rateCross();
        }

        return rate.rateBuy();
    }

    private record MonoCurrencyRate(
            int currencyCodeA,
            int currencyCodeB,
            Long date,
            BigDecimal rateBuy,
            BigDecimal rateSell,
            BigDecimal rateCross
    ) {
    }
}
