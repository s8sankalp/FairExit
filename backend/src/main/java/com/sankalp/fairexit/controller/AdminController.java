package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.service.PriceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (checked in SecurityConfig): load real price quotes. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PriceService prices;

    public AdminController(PriceService prices) {
        this.prices = prices;
    }

    /** Body is the CSV itself, sent with Content-Type: text/csv. */
    @PostMapping(value = "/price-points", consumes = "text/csv")
    public PriceService.ImportResult importPrices(@RequestBody String csv) {
        PriceService.ImportResult result = prices.importCsv(csv);
        if (!result.errors().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing imported. " + String.join("; ",
                    result.errors().subList(0, Math.min(20, result.errors().size()))));
        }
        return result;
    }

    /** Runs the nightly rebuild now, for example right after an import. */
    @PostMapping("/price-stats/refresh")
    public PriceService.RefreshResult refresh() {
        return prices.refreshStats();
    }
}
