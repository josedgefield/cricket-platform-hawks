package sg.hawkscc.platform.finance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sg.hawkscc.platform.audit.AuditLog;
import sg.hawkscc.platform.club.ClubContext;
import sg.hawkscc.platform.finance.FinanceRepository.MemberRef;
import sg.hawkscc.platform.finance.domain.Ledger;
import sg.hawkscc.platform.finance.domain.LedgerRows;
import sg.hawkscc.platform.finance.domain.LedgerRows.AdjustmentKind;
import sg.hawkscc.platform.finance.domain.LedgerRows.Allocation;
import sg.hawkscc.platform.identity.CurrentMember;

/**
 * The club ledger (docs/05). Every change is a new row inside one transaction, with its audit
 * row, while the member is locked so two admins can't change the same member's ledger at once.
 * After each change, unused money is applied to open charges (oldest due first). Balances are
 * never stored. A request repeated with the same {@code requestKey} changes nothing.
 */
@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    /** S$100,000: far above any club charge; catches a slipped decimal point. */
    static final long MAX_CENTS = 10_000_000L;
    private static final Set<String> METHODS = Set.of("paynow", "bank", "cash", "other");

    private final FinanceRepository repo;
    private final AuditLog audit;
    private final ClubContext club;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public FinanceService(FinanceRepository repo, AuditLog audit, ClubContext club, TransactionTemplate tx,
                          Clock clock, @Value("${hawks.timezone:Asia/Singapore}") String zone) {
        this.repo = repo;
        this.audit = audit;
        this.club = club;
        this.tx = tx;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** @param memberIds who to charge; ignored when {@code everyone} is true (all members not deactivated) */
    public record NewCharges(List<UUID> memberIds, boolean everyone, String description, long amountCents,
                             LocalDate dueDate, String requestKey) {
    }

    /** @param chargeId optional: pay this charge first, then the oldest */
    public record NewPayment(UUID memberId, long amountCents, String method, LocalDate receivedOn,
                             String externalRef, String note, UUID chargeId, String requestKey) {
    }

    /**
     * @param chargeId    for void and waiver
     * @param paymentId   for refund and reversal
     * @param memberId    for credit
     * @param amountCents required for credit; optional for waiver and refund (default: all that is left);
     *                    not used for void and reversal, which cancel the whole row
     */
    public record NewAdjustment(AdjustmentKind kind, UUID chargeId, UUID paymentId, UUID memberId, Long amountCents,
                                String reason, String requestKey) {
    }

    // ---------- reading ----------

    public FinanceViews.Statement myStatement(CurrentMember me) {
        MemberRef member = repo.findMember(club.clubId(), me.memberId()).orElseThrow(() -> FinanceException.notFound("member"));
        return statement(member, repo.ledger(club.clubId(), member.id()), false);
    }

    public FinanceViews.Statement statement(UUID memberId) {
        MemberRef member = repo.findMember(club.clubId(), memberId).orElseThrow(() -> FinanceException.notFound("member"));
        return statement(member, repo.ledger(club.clubId(), memberId), true);
    }

    public FinanceViews.Overview overview() {
        LocalDate today = today();
        Map<UUID, LedgerRows> ledgers = repo.ledgers(club.clubId());
        List<FinanceViews.MemberBalance> rows = new ArrayList<>();
        long owed = 0;
        long overdue = 0;
        long unused = 0;
        long collected = 0;
        for (MemberRef m : repo.members(club.clubId())) {
            Ledger ledger = new Ledger(ledgers.getOrDefault(m.id(), LedgerRows.EMPTY));
            int open = (int) ledger.charges().stream().filter(c -> c.outstandingCents() > 0).count();
            rows.add(new FinanceViews.MemberBalance(m.id(), m.displayName(), m.status(), ledger.balanceCents(),
                    ledger.owedCents(), ledger.unusedCents(), ledger.overdueCents(today), open));
            owed += ledger.owedCents();
            overdue += ledger.overdueCents(today);
            unused += ledger.unusedCents();
            collected += ledger.collectedCents();
        }
        return new FinanceViews.Overview(owed, overdue, unused, collected, rows);
    }

    // ---------- changes ----------

    public FinanceViews.ChargesRaised raiseCharges(CurrentMember actor, NewCharges request) {
        String description = text(request.description(), "description", 140);
        long amount = amount(request.amountCents());
        String key = requestKey(request.requestKey());
        UUID clubId = club.clubId();

        var done = repo.findBatch(clubId, key);
        if (done.isPresent()) {
            return new FinanceViews.ChargesRaised(done.get().id(), done.get().count());
        }
        // A sorted set: no member twice, and locks are always taken in the same order.
        Set<UUID> targets = new TreeSet<>();
        if (request.everyone()) {
            repo.members(clubId).stream().filter(m -> !m.status().equals("deactivated")).forEach(m -> targets.add(m.id()));
        } else if (request.memberIds() != null) {
            targets.addAll(request.memberIds());
        }
        if (targets.isEmpty()) {
            throw FinanceException.badRequest("Choose at least one member to charge.");
        }
        try {
            return raise(actor, request, description, amount, key, targets);
        } catch (DuplicateKeyException e) {
            // The same form arrived twice at once: the other request did the work.
            var other = repo.findBatch(clubId, key).orElseThrow(() -> e);
            return new FinanceViews.ChargesRaised(other.id(), other.count());
        }
    }

    private FinanceViews.ChargesRaised raise(CurrentMember actor, NewCharges request, String description, long amount,
                                             String key, Set<UUID> targets) {
        UUID clubId = club.clubId();
        return tx.execute(s -> {
            UUID batch = repo.insertBatch(clubId, description, amount, request.dueDate(), key, actor.memberId());
            for (UUID memberId : targets) {
                MemberRef member = repo.lockMember(clubId, memberId).orElseThrow(() -> FinanceException.notFound("member"));
                if (member.status().equals("deactivated")) {
                    throw FinanceException.conflict(member.displayName() + " is deactivated. Reactivate them before charging them.");
                }
                repo.insertCharge(clubId, memberId, batch, description, amount, request.dueDate(), actor.memberId());
                applyUnusedMoney(clubId, memberId, null, actor.memberId());
            }
            audit.record(clubId, actor.memberId(), "finance.charges_raised", "charge_batch", batch,
                    details("description", description, "amountCents", amount, "members", targets.size(),
                            "dueDate", request.dueDate()));
            log.info("Charges raised: batch {} for {} members by {}", batch, targets.size(), actor.memberId());
            return new FinanceViews.ChargesRaised(batch, targets.size());
        });
    }

    public FinanceViews.Statement recordPayment(CurrentMember actor, NewPayment request) {
        long amount = amount(request.amountCents());
        String key = requestKey(request.requestKey());
        String method = request.method() == null ? "" : request.method().strip().toLowerCase();
        if (!METHODS.contains(method)) {
            throw FinanceException.badRequest("Method must be paynow, bank, cash or other.");
        }
        LocalDate received = request.receivedOn() == null ? today() : request.receivedOn();
        if (received.isAfter(today())) {
            throw FinanceException.badRequest("The date received can't be in the future.");
        }
        String externalRef = optional(request.externalRef(), "bank reference", 80);
        String note = optional(request.note(), "note", 300);
        UUID clubId = club.clubId();

        var done = repo.paymentMemberByRequestKey(clubId, key);
        if (done.isPresent()) {
            return statement(done.get());
        }
        if (request.memberId() == null) {
            throw FinanceException.badRequest("Choose the member who paid.");
        }
        try {
            tx.executeWithoutResult(s -> {
            MemberRef member = repo.lockMember(clubId, request.memberId()).orElseThrow(() -> FinanceException.notFound("member"));
            if (request.chargeId() != null
                    && !member.id().equals(repo.chargeOwner(clubId, request.chargeId()).orElse(null))) {
                throw FinanceException.badRequest("That charge doesn't belong to " + member.displayName() + ".");
            }
            UUID payment = repo.insertPayment(clubId, member.id(), amount, method, received, externalRef, note, key,
                    actor.memberId());
            applyUnusedMoney(clubId, member.id(), request.chargeId(), actor.memberId());
            audit.record(clubId, actor.memberId(), "finance.payment_recorded", "payment", payment,
                    details("memberId", member.id(), "amountCents", amount, "method", method, "receivedOn", received));
            log.info("Payment {} recorded for member {} by {}", payment, member.id(), actor.memberId());
            });
        } catch (DuplicateKeyException e) {
            return statement(repo.paymentMemberByRequestKey(clubId, key).orElseThrow(() -> e));
        }
        return statement(request.memberId());
    }

    public FinanceViews.Statement adjust(CurrentMember actor, NewAdjustment request) {
        if (request.kind() == null) {
            throw FinanceException.badRequest("kind must be void, waiver, credit, refund or reversal");
        }
        String reason = text(request.reason(), "reason", 300);
        String key = requestKey(request.requestKey());
        UUID clubId = club.clubId();

        var done = repo.adjustmentMemberByRequestKey(clubId, key);
        if (done.isPresent()) {
            return statement(done.get());
        }
        UUID memberId = switch (request.kind()) {
            case VOID, WAIVER -> repo.chargeOwner(clubId, required(request.chargeId(), "chargeId"))
                    .orElseThrow(() -> FinanceException.notFound("charge"));
            case REFUND, REVERSAL -> repo.paymentOwner(clubId, required(request.paymentId(), "paymentId"))
                    .orElseThrow(() -> FinanceException.notFound("payment"));
            case CREDIT -> required(request.memberId(), "memberId");
        };
        try {
            tx.executeWithoutResult(s -> {
            repo.lockMember(clubId, memberId).orElseThrow(() -> FinanceException.notFound("member"));
            Ledger ledger = new Ledger(repo.ledger(clubId, memberId));
            long amount = switch (request.kind()) {
                case VOID -> voidCharge(clubId, memberId, ledger, request.chargeId(), actor.memberId());
                case WAIVER -> waiverAmount(ledger, request.chargeId(), request.amountCents());
                case CREDIT -> amount(request.amountCents() == null ? 0 : request.amountCents());
                case REFUND -> refundAmount(ledger, request.paymentId(), request.amountCents());
                case REVERSAL -> reversePayment(clubId, memberId, ledger, request.paymentId(), actor.memberId());
            };
            UUID id = repo.insertAdjustment(clubId, memberId, request.kind(), amount, request.chargeId(),
                    request.paymentId(), reason, key, actor.memberId());
            applyUnusedMoney(clubId, memberId, null, actor.memberId());
            audit.record(clubId, actor.memberId(), "finance.adjustment_" + request.kind().code(), "adjustment", id,
                    details("memberId", memberId, "amountCents", amount, "chargeId", request.chargeId(),
                            "paymentId", request.paymentId(), "reason", reason));
            log.info("Adjustment {} ({}) for member {} by {}", id, request.kind().code(), memberId, actor.memberId());
            });
        } catch (DuplicateKeyException e) {
            if (repo.adjustmentMemberByRequestKey(clubId, key).isEmpty()) {
                throw FinanceException.conflict("Someone else just changed this. Reload and check before trying again.");
            }
        }
        return statement(memberId);
    }

    // ---------- rules for each adjustment ----------

    /** Takes back anything paid towards the charge, and returns the amount the void cancels. */
    private long voidCharge(UUID clubId, UUID memberId, Ledger ledger, UUID chargeId, UUID by) {
        Ledger.ChargeState charge = ledger.charge(chargeId);
        if (charge.status() == Ledger.ChargeStatus.VOID) {
            throw FinanceException.conflict("This charge has already been cancelled.");
        }
        long amount = charge.charge().amountCents() - charge.writtenOffCents();
        if (amount <= 0) {
            throw FinanceException.conflict("This charge has already been waived in full.");
        }
        release(clubId, memberId, ledger.activeOnCharge(chargeId), by);
        return amount;
    }

    private long waiverAmount(Ledger ledger, UUID chargeId, Long requested) {
        Ledger.ChargeState charge = ledger.charge(chargeId);
        if (charge.outstandingCents() == 0) {
            throw FinanceException.conflict("Nothing is left to waive on this charge.");
        }
        long amount = requested == null ? charge.outstandingCents() : amount(requested);
        if (amount > charge.outstandingCents()) {
            throw FinanceException.conflict("Only " + Money.format(charge.outstandingCents())
                    + " is still unpaid on this charge.");
        }
        return amount;
    }

    private long refundAmount(Ledger ledger, UUID paymentId, Long requested) {
        Ledger.Fund payment = ledger.payment(paymentId);
        if (payment.availableCents() == 0) {
            throw FinanceException.conflict(payment.reversed()
                    ? "This payment was cancelled, so there is nothing to refund."
                    : "All of this payment has been used on charges. Cancel a charge first if it should be refunded.");
        }
        long amount = requested == null ? payment.availableCents() : amount(requested);
        if (amount > payment.availableCents()) {
            throw FinanceException.conflict("Only " + Money.format(payment.availableCents())
                    + " of this payment is unused and can be refunded.");
        }
        return amount;
    }

    /** Takes the payment back off every charge it paid, and returns the amount the reversal cancels. */
    private long reversePayment(UUID clubId, UUID memberId, Ledger ledger, UUID paymentId, UUID by) {
        Ledger.Fund payment = ledger.payment(paymentId);
        if (payment.reversed()) {
            throw FinanceException.conflict("This payment has already been cancelled.");
        }
        if (payment.effectiveCents() <= 0) {
            throw FinanceException.conflict("This payment has already been refunded in full.");
        }
        release(clubId, memberId, ledger.activeFromPayment(paymentId), by);
        return payment.effectiveCents();
    }

    private void release(UUID clubId, UUID memberId, List<Allocation> allocations, UUID by) {
        for (Allocation a : allocations) {
            repo.insertAllocation(clubId, memberId, a.paymentId(), a.creditAdjustmentId(), a.chargeId(), a.amountCents(),
                    a.id(), by);
        }
    }

    /** Re-reads the member's ledger and applies any unused money to charges that still need it. */
    private void applyUnusedMoney(UUID clubId, UUID memberId, UUID preferChargeId, UUID by) {
        Ledger ledger = new Ledger(repo.ledger(clubId, memberId));
        for (Ledger.PlannedAllocation p : ledger.plan(preferChargeId)) {
            repo.insertAllocation(clubId, memberId, p.credit() ? null : p.fundId(), p.credit() ? p.fundId() : null,
                    p.chargeId(), p.amountCents(), null, by);
        }
    }

    // ---------- views ----------

    private FinanceViews.Statement statement(MemberRef member, LedgerRows rows, boolean forAdmin) {
        Ledger ledger = new Ledger(rows);
        LocalDate today = today();
        List<FinanceViews.ChargeLine> charges = ledger.charges().stream()
                .map(c -> new FinanceViews.ChargeLine(c.charge().id(), c.charge().reference(), c.charge().description(),
                        c.charge().amountCents(), c.paidCents(), c.writtenOffCents(), c.outstandingCents(),
                        c.charge().dueDate(), c.status().code(), c.overdue(today), c.charge().createdAt()))
                .toList();
        List<FinanceViews.PaymentLine> payments = ledger.funds().stream().filter(f -> !f.credit())
                .map(f -> new FinanceViews.PaymentLine(f.id(), f.payment().reference(), f.payment().amountCents(),
                        f.payment().method(), f.payment().receivedOn(), f.payment().externalRef(),
                        forAdmin ? f.payment().note() : null, f.refundedCents(), f.availableCents(), f.reversed(),
                        f.createdAt()))
                .toList();
        List<FinanceViews.Entry> entries = ledger.statement(zone).stream()
                .map(e -> new FinanceViews.Entry(e.at(), e.date(), e.kind().code(), e.reference(), e.description(),
                        e.amountCents(), e.balanceCents()))
                .toList();
        return new FinanceViews.Statement(member.id(), member.displayName(), ledger.balanceCents(), ledger.owedCents(),
                ledger.unusedCents(), ledger.overdueCents(today), charges, payments, entries);
    }

    // ---------- validation ----------

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    private static long amount(long cents) {
        if (cents <= 0) {
            throw FinanceException.badRequest("The amount must be more than zero.");
        }
        if (cents > MAX_CENTS) {
            throw FinanceException.badRequest("The amount can't be more than " + Money.format(MAX_CENTS) + ".");
        }
        return cents;
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw FinanceException.badRequest("Enter a " + name + ".");
        }
        return optional(value, name, max);
    }

    private static String optional(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max) {
            throw FinanceException.badRequest("The " + name + " can be at most " + max + " characters.");
        }
        return v;
    }

    private static String requestKey(String key) {
        if (key == null || key.isBlank() || key.length() > 80) {
            throw FinanceException.badRequest("requestKey is required: a new random value for each form you submit.");
        }
        return key.strip();
    }

    private static UUID required(UUID id, String name) {
        if (id == null) {
            throw FinanceException.badRequest(name + " is required for this kind of adjustment.");
        }
        return id;
    }

    private static Map<String, String> details(Object... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put((String) pairs[i], String.valueOf(pairs[i + 1]));
            }
        }
        return out;
    }
}
