package sg.hawkscc.platform.finance.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.finance.FinanceService;
import sg.hawkscc.platform.finance.FinanceViews;
import sg.hawkscc.platform.finance.domain.LedgerRows.AdjustmentKind;
import sg.hawkscc.platform.identity.CurrentMember;

/**
 * Finance for admins and superusers (ROLE_ADMIN). Amounts are whole cents. Every write needs a
 * {@code requestKey}: a new random value per submitted form, so a repeat changes nothing.
 */
@RestController
@RequestMapping("/api/admin/finance")
class FinanceAdminController {

    private final FinanceService finance;

    FinanceAdminController(FinanceService finance) {
        this.finance = finance;
    }

    record Charges(List<UUID> memberIds, Boolean everyone, @NotBlank @Size(max = 140) String description,
                   @NotNull Long amountCents, LocalDate dueDate, @NotBlank @Size(max = 80) String requestKey) {
    }

    record Payment(@NotNull UUID memberId, @NotNull Long amountCents, @NotBlank String method, LocalDate receivedOn,
                   @Size(max = 80) String externalRef, @Size(max = 300) String note, UUID chargeId,
                   @NotBlank @Size(max = 80) String requestKey) {
    }

    record Adjustment(@NotBlank String kind, UUID chargeId, UUID paymentId, UUID memberId, Long amountCents,
                      @NotBlank @Size(max = 300) String reason, @NotBlank @Size(max = 80) String requestKey) {
    }

    /** Totals and every member's balance. */
    @GetMapping("/overview")
    FinanceViews.Overview overview() {
        return finance.overview();
    }

    @GetMapping("/members/{memberId}/statement")
    FinanceViews.Statement statement(@PathVariable UUID memberId) {
        return finance.statement(memberId);
    }

    /** Charge specific members ({@code memberIds}) or everyone who isn't deactivated ({@code everyone}). */
    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    FinanceViews.ChargesRaised raise(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody Charges body) {
        return finance.raiseCharges(me, new FinanceService.NewCharges(body.memberIds(), Boolean.TRUE.equals(body.everyone()),
                body.description(), body.amountCents(), body.dueDate(), body.requestKey()));
    }

    /** Record money received (PayNow, bank transfer, cash). Returns the member's updated statement. */
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    FinanceViews.Statement pay(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody Payment body) {
        return finance.recordPayment(me, new FinanceService.NewPayment(body.memberId(), body.amountCents(), body.method(),
                body.receivedOn(), body.externalRef(), body.note(), body.chargeId(), body.requestKey()));
    }

    /**
     * void or waiver (needs chargeId), refund or reversal (needs paymentId), credit (needs memberId
     * and amountCents). A reason is always required. Returns the member's updated statement.
     */
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    FinanceViews.Statement adjust(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody Adjustment body) {
        return finance.adjust(me, new FinanceService.NewAdjustment(AdjustmentKind.fromCode(body.kind()), body.chargeId(),
                body.paymentId(), body.memberId(), body.amountCents(), body.reason(), body.requestKey()));
    }
}
