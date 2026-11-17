package com.sankalp.fairexit.service.prices;

import com.sankalp.fairexit.service.PriceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuilds price stats every night at 02:30 India time, when nobody is auditing. */
@Component
@ConditionalOnProperty(name = "fairexit.prices.nightly-refresh-enabled", havingValue = "true", matchIfMissing = true)
public class PriceRefreshJob {

    private final PriceService prices;

    public PriceRefreshJob(PriceService prices) {
        this.prices = prices;
    }

    @Scheduled(cron = "${fairexit.prices.refresh-cron:0 30 2 * * *}", zone = "Asia/Kolkata")
    public void refresh() {
        prices.refreshStats();
    }
}
