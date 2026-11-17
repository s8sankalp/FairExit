package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.ReplyResponse;
import com.sankalp.fairexit.service.ReplyService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReplyController {

    private final ReplyService replyService;

    public ReplyController(ReplyService replyService) {
        this.replyService = replyService;
    }

    /** A GET: the drafts are worked out from the stored audit every time, nothing is saved. */
    @GetMapping("/api/audits/{auditId}/replies")
    public ReplyResponse replies(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID auditId) {
        return replyService.replies(user.id(), auditId);
    }
}
