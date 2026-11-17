package com.sankalp.fairexit.service;

import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.dto.ReplyResponse;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.UserRepository;
import com.sankalp.fairexit.service.reply.ReplyGenerator;
import com.sankalp.fairexit.service.reply.ReplyInput;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplyService {

    private final AuditService auditService;
    private final BillService billService;
    private final TenancyService tenancyService;
    private final UserRepository users;
    private final ReplyGenerator generator;

    public ReplyService(AuditService auditService, BillService billService, TenancyService tenancyService,
                        UserRepository users, ReplyGenerator generator) {
        this.auditService = auditService;
        this.billService = billService;
        this.tenancyService = tenancyService;
        this.users = users;
        this.generator = generator;
    }

    /** Builds the drafts from the stored audit, so they always match what the tenant saw. */
    @Transactional(readOnly = true)
    public ReplyResponse replies(UUID userId, UUID auditId) {
        AuditResponse audit = auditService.get(userId, auditId); // 404 if not this user's
        UUID tenancyId = billService.findOwned(userId, audit.billId()).getTenancyId();
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        String name = users.findById(userId).map(User::getName).orElse(null);

        ReplyInput input = new ReplyInput(name, tenancy.getCity(), tenancy.getDeposit(), audit.tenancyMonths(),
                audit.verdicts().stream()
                        .map(v -> new ReplyInput.Line(v.item(), v.category(), v.amount(), v.verdict(), v.ruleIds()))
                        .toList(),
                audit.totals().fair(), audit.totals().questionable(), audit.totals().needsInfo(),
                audit.totals().suggestedRefund());

        ReplyGenerator.Replies replies = generator.generate(input);
        return new ReplyResponse(audit.id(), replies.polite(), replies.firm(), audit.disclaimer());
    }
}
