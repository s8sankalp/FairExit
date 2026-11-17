package com.sankalp.fairexit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.PricePoint;
import com.sankalp.fairexit.model.PriceStats;
import com.sankalp.fairexit.repository.PricePointRepository;
import com.sankalp.fairexit.repository.PriceStatsRepository;
import com.sankalp.fairexit.service.audit.AuditContext.PriceStat;
import com.sankalp.fairexit.service.prices.PriceCsvParser;
import com.sankalp.fairexit.service.prices.PriceStatsCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Price data for rules 5 and 6.
 *  - points: raw prices (admin CSV import now; real outcomes later)
 *  - stats:  p50 and p90 per (category, city, flat size), rebuilt by a nightly job
 *  - cache:  stats are read through Redis; the job rewrites the cache after each rebuild
 */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    static final String LOCK_KEY = "lock:price-stats-refresh";
    static final String CACHE_PREFIX = "price:stats:";
    /** A little over a day: the nightly rebuild always lands before the cache runs out. */
    static final Duration CACHE_TTL = Duration.ofHours(26);
    /** Stored for groups with no stats, so a miss does not hit the database on every audit. */
    private static final String NONE = "none";

    private final PricePointRepository points;
    private final PriceStatsRepository stats;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisLock lock;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PriceService(PricePointRepository points, PriceStatsRepository stats, StringRedisTemplate redis,
                        ObjectMapper objectMapper, RedisLock lock, PlatformTransactionManager transactionManager,
                        Clock clock) {
        this.points = points;
        this.stats = stats;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.lock = lock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public record ImportResult(int imported, List<String> errors) {
    }

    public record RefreshResult(boolean ran, int groups, int points) {
    }

    // ------------------------------------------------------------------ read

    /** The usual range for each category, for one city and flat size. Categories with no data are left out. */
    public Map<DeductionCategory, PriceStat> statsFor(String city, FlatSize flatSize,
                                                      Collection<DeductionCategory> categories) {
        Map<DeductionCategory, PriceStat> result = new EnumMap<>(DeductionCategory.class);
        for (DeductionCategory category : categories) {
            statFor(category, city, flatSize).ifPresent(s -> result.put(category, s));
        }
        return result;
    }

    private Optional<PriceStat> statFor(DeductionCategory category, String city, FlatSize flatSize) {
        String key = cacheKey(category, city, flatSize);
        try {
            String cached = redis.opsForValue().get(key);
            if (NONE.equals(cached)) {
                return Optional.empty();
            }
            if (cached != null) {
                return Optional.of(objectMapper.readValue(cached, PriceStat.class));
            }
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Price cache read failed, using the database: {}", e.getMessage());
        }
        Optional<PriceStat> fromDb = stats.findByCategoryAndCityAndFlatSize(category, city, flatSize)
                .map(s -> new PriceStat(s.getP50(), s.getP90(), s.getN()));
        cache(key, fromDb.orElse(null));
        return fromDb;
    }

    // ------------------------------------------------------------------ write

    /** All or nothing: one bad row and nothing is saved. Call refreshStats() after to use the new prices. */
    @Transactional
    public ImportResult importCsv(String csv) {
        PriceCsvParser.Result parsed = PriceCsvParser.parse(csv);
        if (!parsed.ok()) {
            return new ImportResult(0, parsed.errors());
        }
        points.saveAll(parsed.rows().stream()
                .map(r -> new PricePoint(r.category(), r.city(), r.flatSize(), r.amount(), r.source()))
                .toList());
        return new ImportResult(parsed.rows().size(), List.of());
    }

    /**
     * Rebuilds every group's stats. A Redis lock makes sure only one app instance runs it.
     * The old stats are replaced in one transaction, so an audit never sees half a rebuild.
     */
    public RefreshResult refreshStats() {
        Optional<String> token = lock.tryLock(LOCK_KEY, Duration.ofMinutes(10));
        if (token.isEmpty()) {
            log.info("Price stats refresh already running elsewhere; skipping");
            return new RefreshResult(false, 0, 0);
        }
        try {
            List<PriceStats> rebuilt = Objects.requireNonNull(transaction.execute(status -> rebuild()));
            rebuilt.forEach(s -> cache(cacheKey(s.getCategory(), s.getCity(), s.getFlatSize()),
                    new PriceStat(s.getP50(), s.getP90(), s.getN())));
            int used = rebuilt.stream().mapToInt(PriceStats::getN).sum();
            log.info("Price stats rebuilt: {} groups from {} points", rebuilt.size(), used);
            return new RefreshResult(true, rebuilt.size(), used);
        } finally {
            lock.unlock(LOCK_KEY, token.get());
        }
    }

    private List<PriceStats> rebuild() {
        Instant now = clock.instant();
        List<PriceStats> rebuilt = new ArrayList<>();
        // Streamed and sorted by group, so only one group's prices are in memory at a time
        try (Stream<PricePoint> all = points.streamAllByGroup()) {
            List<BigDecimal> group = new ArrayList<>();
            PricePoint[] first = new PricePoint[1];
            all.forEach(p -> {
                if (first[0] != null && !sameGroup(first[0], p)) {
                    summarise(first[0], group, now).ifPresent(rebuilt::add);
                    group.clear();
                }
                if (group.isEmpty()) {
                    first[0] = p;
                }
                group.add(p.getAmount());
            });
            if (first[0] != null) {
                summarise(first[0], group, now).ifPresent(rebuilt::add);
            }
        }
        stats.deleteAllStats();
        return stats.saveAll(rebuilt);
    }

    private static Optional<PriceStats> summarise(PricePoint key, List<BigDecimal> amounts, Instant now) {
        return PriceStatsCalculator.calculate(amounts).map(r ->
                new PriceStats(key.getCategory(), key.getCity(), key.getFlatSize(), r.p50(), r.p90(), r.n(), now));
    }

    private static boolean sameGroup(PricePoint a, PricePoint b) {
        return a.getCategory() == b.getCategory() && a.getCity().equals(b.getCity()) && a.getFlatSize() == b.getFlatSize();
    }

    private void cache(String key, PriceStat stat) {
        try {
            redis.opsForValue().set(key, stat == null ? NONE : objectMapper.writeValueAsString(stat), CACHE_TTL);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Price cache write failed: {}", e.getMessage());
        }
    }

    static String cacheKey(DeductionCategory category, String city, FlatSize flatSize) {
        return CACHE_PREFIX + category + ":" + flatSize + ":" + city.toLowerCase(java.util.Locale.ROOT);
    }
}
