package com.sankalp.fairexit.service;

import com.sankalp.fairexit.dto.CreateTenancyRequest;
import com.sankalp.fairexit.dto.TenancyResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.TenancyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenancyService {

    private final TenancyRepository tenancies;
    private final RuleCatalog ruleCatalog;

    public TenancyService(TenancyRepository tenancies, RuleCatalog ruleCatalog) {
        this.tenancies = tenancies;
        this.ruleCatalog = ruleCatalog;
    }

    @Transactional
    public TenancyResponse create(UUID userId, CreateTenancyRequest request) {
        Tenancy tenancy = new Tenancy(userId, normaliseCity(request.city()), request.state(),
                request.flatSize(), request.rent(), request.deposit(),
                request.startDate(), request.endDate(), request.noticeDays());
        return toResponse(tenancies.save(tenancy));
    }

    @Transactional(readOnly = true)
    public List<TenancyResponse> list(UUID userId) {
        return tenancies.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenancyResponse get(UUID userId, UUID tenancyId) {
        return toResponse(findOwned(userId, tenancyId));
    }

    /**
     * Loads a tenancy only if it belongs to the user. Another user's tenancy gives 404,
     * not 403, so the API does not even reveal that the id exists.
     * Later modules (bills, evidence) call this before touching anything of a tenancy.
     */
    @Transactional(readOnly = true)
    public Tenancy findOwned(UUID userId, UUID tenancyId) {
        return tenancies.findByIdAndUserId(tenancyId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenancy not found"));
    }

    private TenancyResponse toResponse(Tenancy tenancy) {
        return TenancyResponse.from(tenancy, ruleCatalog.coverageFor(tenancy.getState()));
    }

    static String normaliseCity(String city) {
        return Cities.normalise(city);
    }
}
