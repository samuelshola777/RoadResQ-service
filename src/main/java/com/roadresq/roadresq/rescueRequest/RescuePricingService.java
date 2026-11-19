package com.roadresq.roadresq.rescueRequest;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RescuePricingService {

    private static final BigDecimal THIRTY_PERCENT = new BigDecimal("0.30");

    public RescueQuote quote() {
        // Development prices in NGN until service rates and route pricing are configured.
        BigDecimal total = BigDecimal.valueOf(ThreadLocalRandom.current().nextLong(5_000, 25_001))
                .setScale(2);
        BigDecimal amount30Percent = total.multiply(THIRTY_PERCENT).setScale(2, RoundingMode.HALF_UP);
        return new RescueQuote("NGN", total, amount30Percent, total.subtract(amount30Percent), true);
    }
}