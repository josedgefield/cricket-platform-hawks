package sg.hawkscc.platform.finance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import sg.hawkscc.platform.finance.domain.LedgerRows.Adjustment;
import sg.hawkscc.platform.finance.domain.LedgerRows.AdjustmentKind;
import sg.hawkscc.platform.finance.domain.LedgerRows.Allocation;
import sg.hawkscc.platform.finance.domain.LedgerRows.Charge;
import sg.hawkscc.platform.finance.domain.LedgerRows.Payment;

/**
 * Everything derived from one member's ledger rows (docs/05): what each charge still needs,
 * what money is unused, the balance, the statement, and where unused money should go next.
 * Nothing here is stored; it is recalculated from the rows every time.
 */
public final class Ledger {

    public enum ChargeStatus {
        OPEN, PART_PAID, PAID, WAIVED, VOID;

        public String code() {
            return name().toLowerCase();
        }
    }

    /** A charge with what has happened to it. */
    public record ChargeState(Charge charge, long paidCents, long writtenOffCents, long outstandingCents,
                              ChargeStatus status) {

        public boolean overdue(LocalDate today) {
            return outstandingCents > 0 && charge.dueDate() != null && charge.dueDate().isBefore(today);
        }
    }

    /** Money the member has put in: a payment, or a credit the club gave them. */
    public record Fund(UUID id, boolean credit, Payment payment, Adjustment creditAdjustment, long effectiveCents,
                       long refundedCents, boolean reversed, long allocatedCents, long availableCents,
                       Instant createdAt) {
    }

    public enum EntryKind {
        CHARGE, PAYMENT, VOID, WAIVER, CREDIT, REFUND, REVERSAL;

        public String code() {
            return name().toLowerCase();
        }
    }

    /** One statement line. A positive amount increases what the member owes. */
    public record Entry(Instant at, LocalDate date, EntryKind kind, String reference, String description,
                        long amountCents, long balanceCents) {
    }

    public record PlannedAllocation(UUID fundId, boolean credit, UUID chargeId, long amountCents) {
    }

    private final LedgerRows rows;
    private final List<ChargeState> charges = new ArrayList<>();
    private final List<Fund> funds = new ArrayList<>();
    private final List<Allocation> active;

    public Ledger(LedgerRows rows) {
        this.rows = rows;
        Set<UUID> released = new HashSet<>();
        for (Allocation a : rows.allocations()) {
            if (a.releases() != null) {
                released.add(a.releases());
            }
        }
        this.active = rows.allocations().stream()
                .filter(a -> a.releases() == null && !released.contains(a.id())).toList();

        Map<UUID, Long> paidByCharge = new HashMap<>();
        Map<UUID, Long> usedByFund = new HashMap<>();
        for (Allocation a : active) {
            paidByCharge.merge(a.chargeId(), a.amountCents(), Long::sum);
            usedByFund.merge(a.fundId(), a.amountCents(), Long::sum);
        }
        Map<UUID, Long> writtenOff = new HashMap<>();
        Set<UUID> voided = new HashSet<>();
        Map<UUID, Long> refunded = new HashMap<>();
        Map<UUID, Long> reversedAmount = new HashMap<>();
        for (Adjustment adj : rows.adjustments()) {
            switch (adj.kind()) {
                case VOID -> {
                    voided.add(adj.chargeId());
                    writtenOff.merge(adj.chargeId(), adj.amountCents(), Long::sum);
                }
                case WAIVER -> writtenOff.merge(adj.chargeId(), adj.amountCents(), Long::sum);
                case REFUND -> refunded.merge(adj.paymentId(), adj.amountCents(), Long::sum);
                case REVERSAL -> reversedAmount.merge(adj.paymentId(), adj.amountCents(), Long::sum);
                case CREDIT -> {
                    long used = usedByFund.getOrDefault(adj.id(), 0L);
                    funds.add(new Fund(adj.id(), true, null, adj, adj.amountCents(), 0, false, used,
                            adj.amountCents() - used, adj.createdAt()));
                }
            }
        }
        for (Charge c : rows.charges()) {
            long paid = paidByCharge.getOrDefault(c.id(), 0L);
            long off = writtenOff.getOrDefault(c.id(), 0L);
            long outstanding = Math.max(0, c.amountCents() - off - paid);
            ChargeStatus status;
            if (voided.contains(c.id())) {
                status = ChargeStatus.VOID;
            } else if (outstanding == 0) {
                status = paid > 0 ? ChargeStatus.PAID : ChargeStatus.WAIVED;
            } else {
                status = paid > 0 || off > 0 ? ChargeStatus.PART_PAID : ChargeStatus.OPEN;
            }
            charges.add(new ChargeState(c, paid, off, outstanding, status));
        }
        for (Payment p : rows.payments()) {
            long back = refunded.getOrDefault(p.id(), 0L);
            long reversal = reversedAmount.getOrDefault(p.id(), 0L);
            long effective = p.amountCents() - back - reversal;
            long used = usedByFund.getOrDefault(p.id(), 0L);
            funds.add(new Fund(p.id(), false, p, null, effective, back, reversedAmount.containsKey(p.id()), used,
                    Math.max(0, effective - used), p.createdAt()));
        }
        funds.sort(Comparator.comparing(Fund::createdAt));
    }

    public List<ChargeState> charges() {
        return charges;
    }

    public List<Fund> funds() {
        return funds;
    }

    public ChargeState charge(UUID id) {
        return charges.stream().filter(c -> c.charge().id().equals(id)).findFirst().orElse(null);
    }

    public Fund payment(UUID id) {
        return funds.stream().filter(f -> !f.credit() && f.id().equals(id)).findFirst().orElse(null);
    }

    /** Allocations still in force on a charge. */
    public List<Allocation> activeOnCharge(UUID chargeId) {
        return active.stream().filter(a -> a.chargeId().equals(chargeId)).toList();
    }

    /** Allocations still in force from a payment. */
    public List<Allocation> activeFromPayment(UUID paymentId) {
        return active.stream().filter(a -> paymentId.equals(a.paymentId())).toList();
    }

    /** Total still to pay across charges. */
    public long owedCents() {
        return charges.stream().mapToLong(ChargeState::outstandingCents).sum();
    }

    /** Money received or credited that isn't applied to any charge yet. */
    public long unusedCents() {
        return funds.stream().mapToLong(Fund::availableCents).sum();
    }

    public long overdueCents(LocalDate today) {
        return charges.stream().filter(c -> c.overdue(today)).mapToLong(ChargeState::outstandingCents).sum();
    }

    /** Money actually kept from payments: received, less refunds and reversals. */
    public long collectedCents() {
        return funds.stream().filter(f -> !f.credit()).mapToLong(Fund::effectiveCents).sum();
    }

    /**
     * What the member owes (positive) or is in credit by (negative), straight from the rows:
     * charges − voids and waivers − payments + refunds and reversals − credits.
     */
    public long balanceCents() {
        long balance = rows.charges().stream().mapToLong(Charge::amountCents).sum()
                - rows.payments().stream().mapToLong(Payment::amountCents).sum();
        for (Adjustment a : rows.adjustments()) {
            balance += signed(a);
        }
        return balance;
    }

    private static long signed(Adjustment a) {
        return switch (a.kind()) {
            case VOID, WAIVER, CREDIT -> -a.amountCents();
            case REFUND, REVERSAL -> a.amountCents();
        };
    }

    /** Every row in the order it was entered, with the balance after each. */
    public List<Entry> statement(java.time.ZoneId zone) {
        record Line(Instant at, LocalDate date, EntryKind kind, String reference, String description, long amount) {
        }
        Map<UUID, Charge> chargeById = new HashMap<>();
        rows.charges().forEach(c -> chargeById.put(c.id(), c));
        Map<UUID, Payment> paymentById = new HashMap<>();
        rows.payments().forEach(p -> paymentById.put(p.id(), p));

        List<Line> lines = new ArrayList<>();
        for (Charge c : rows.charges()) {
            lines.add(new Line(c.createdAt(), LocalDate.ofInstant(c.createdAt(), zone), EntryKind.CHARGE,
                    c.reference(), c.description(), c.amountCents()));
        }
        for (Payment p : rows.payments()) {
            lines.add(new Line(p.createdAt(), p.receivedOn(), EntryKind.PAYMENT, p.reference(),
                    "Payment received (" + p.method() + ")", -p.amountCents()));
        }
        for (Adjustment a : rows.adjustments()) {
            String about = switch (a.kind()) {
                case VOID, WAIVER -> chargeById.containsKey(a.chargeId()) ? chargeById.get(a.chargeId()).reference() : null;
                case REFUND, REVERSAL -> paymentById.containsKey(a.paymentId()) ? paymentById.get(a.paymentId()).reference() : null;
                case CREDIT -> null;
            };
            String what = switch (a.kind()) {
                case VOID -> "Charge cancelled";
                case WAIVER -> "Waived";
                case CREDIT -> "Credit";
                case REFUND -> "Refund";
                case REVERSAL -> "Payment cancelled";
            };
            lines.add(new Line(a.createdAt(), LocalDate.ofInstant(a.createdAt(), zone),
                    EntryKind.valueOf(a.kind().name()), about, what + ": " + a.reason(), signed(a)));
        }
        lines.sort(Comparator.comparing(Line::at));
        List<Entry> out = new ArrayList<>();
        long balance = 0;
        for (Line l : lines) {
            balance += l.amount();
            out.add(new Entry(l.at(), l.date(), l.kind(), l.reference(), l.description(), l.amount(), balance));
        }
        return out;
    }

    /**
     * Where unused money should go: the charge named first (if any), then charges by due date
     * (those without one last) and then by age, using the oldest money first.
     */
    public List<PlannedAllocation> plan(UUID preferChargeId) {
        Map<UUID, Long> need = new HashMap<>();
        Map<UUID, Long> have = new HashMap<>();
        charges.forEach(c -> need.put(c.charge().id(), c.outstandingCents()));
        funds.forEach(f -> have.put(f.id(), f.availableCents()));
        List<ChargeState> order = new ArrayList<>(charges);
        order.sort(Comparator
                .comparing((ChargeState c) -> !c.charge().id().equals(preferChargeId))
                .thenComparing(c -> c.charge().dueDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.charge().createdAt()));
        List<PlannedAllocation> plan = new ArrayList<>();
        for (ChargeState c : order) {
            long remaining = need.get(c.charge().id());
            for (Fund f : funds) {
                if (remaining == 0) {
                    break;
                }
                long available = have.get(f.id());
                if (available == 0) {
                    continue;
                }
                long take = Math.min(remaining, available);
                plan.add(new PlannedAllocation(f.id(), f.credit(), c.charge().id(), take));
                have.put(f.id(), available - take);
                remaining -= take;
            }
        }
        return plan;
    }
}
