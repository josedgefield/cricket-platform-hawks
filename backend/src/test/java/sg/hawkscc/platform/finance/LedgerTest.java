package sg.hawkscc.platform.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sg.hawkscc.platform.finance.domain.Ledger;
import sg.hawkscc.platform.finance.domain.LedgerRows;
import sg.hawkscc.platform.finance.domain.LedgerRows.Adjustment;
import sg.hawkscc.platform.finance.domain.LedgerRows.AdjustmentKind;
import sg.hawkscc.platform.finance.domain.LedgerRows.Allocation;
import sg.hawkscc.platform.finance.domain.LedgerRows.Charge;
import sg.hawkscc.platform.finance.domain.LedgerRows.Payment;

class LedgerTest {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private final List<Charge> charges = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<Adjustment> adjustments = new ArrayList<>();
    private final List<Allocation> allocations = new ArrayList<>();
    private int tick;

    private Instant next() {
        return Instant.parse("2026-11-01T00:00:00Z").plusSeconds(60L * tick++);
    }

    private UUID charge(long cents, LocalDate due) {
        UUID id = UUID.randomUUID();
        charges.add(new Charge(id, "HWK-C-" + charges.size(), "Fee", cents, due, next()));
        return id;
    }

    private UUID payment(long cents) {
        UUID id = UUID.randomUUID();
        payments.add(new Payment(id, "HWK-P-" + payments.size(), cents, "paynow", TODAY, null, null, next()));
        return id;
    }

    private UUID adjust(AdjustmentKind kind, long cents, UUID chargeId, UUID paymentId) {
        UUID id = UUID.randomUUID();
        adjustments.add(new Adjustment(id, kind, cents, chargeId, paymentId, "because", next()));
        return id;
    }

    private Ledger ledger() {
        return new Ledger(new LedgerRows(charges, payments, adjustments, allocations));
    }

    /** Applies the plan the way the service does. */
    private void apply(UUID prefer) {
        for (Ledger.PlannedAllocation p : ledger().plan(prefer)) {
            allocations.add(new Allocation(UUID.randomUUID(), p.credit() ? null : p.fundId(),
                    p.credit() ? p.fundId() : null, p.chargeId(), p.amountCents(), null));
        }
    }

    private void release(List<Allocation> some) {
        for (Allocation a : some) {
            allocations.add(new Allocation(UUID.randomUUID(), a.paymentId(), a.creditAdjustmentId(), a.chargeId(),
                    a.amountCents(), a.id()));
        }
    }

    /** The balance from the rows must equal what charges still need less unused money. */
    private void assertConsistent() {
        Ledger l = ledger();
        assertThat(l.balanceCents()).isEqualTo(l.owedCents() - l.unusedCents());
        List<Ledger.Entry> entries = l.statement(SGT);
        if (!entries.isEmpty()) {
            assertThat(entries.getLast().balanceCents()).isEqualTo(l.balanceCents());
        }
    }

    @Test
    void anEmptyLedgerOwesNothing() {
        Ledger l = new Ledger(LedgerRows.EMPTY);
        assertThat(l.balanceCents()).isZero();
        assertThat(l.statement(SGT)).isEmpty();
        assertThat(l.plan(null)).isEmpty();
    }

    @Test
    void paymentsGoToTheEarliestDueChargeFirstAndUndatedOnesLast() {
        UUID undated = charge(5_000, null);
        UUID later = charge(5_000, LocalDate.of(2026, 12, 31));
        UUID sooner = charge(5_000, LocalDate.of(2026, 11, 1));
        payment(7_500);
        apply(null);

        Ledger l = ledger();
        assertThat(l.charge(sooner).status()).isEqualTo(Ledger.ChargeStatus.PAID);
        assertThat(l.charge(later).status()).isEqualTo(Ledger.ChargeStatus.PART_PAID);
        assertThat(l.charge(later).outstandingCents()).isEqualTo(2_500);
        assertThat(l.charge(undated).status()).isEqualTo(Ledger.ChargeStatus.OPEN);
        assertThat(l.balanceCents()).isEqualTo(7_500);
        assertThat(l.unusedCents()).isZero();
        assertConsistent();
    }

    @Test
    void aNamedChargeIsPaidFirst() {
        UUID first = charge(5_000, LocalDate.of(2026, 11, 1));
        UUID named = charge(5_000, LocalDate.of(2026, 12, 1));
        payment(5_000);
        apply(named);
        assertThat(ledger().charge(named).status()).isEqualTo(Ledger.ChargeStatus.PAID);
        assertThat(ledger().charge(first).status()).isEqualTo(Ledger.ChargeStatus.OPEN);
    }

    @Test
    void overpaymentStaysAsUnusedMoneyAndPaysTheNextCharge() {
        charge(3_000, null);
        payment(5_000);
        apply(null);
        assertThat(ledger().unusedCents()).isEqualTo(2_000);
        assertThat(ledger().balanceCents()).isEqualTo(-2_000);

        UUID next = charge(4_000, null);
        apply(null);
        assertThat(ledger().charge(next).outstandingCents()).isEqualTo(2_000);
        assertThat(ledger().balanceCents()).isEqualTo(2_000);
        assertConsistent();
    }

    @Test
    void onlyUnpaidChargesPastTheirDueDateAreOverdue() {
        UUID past = charge(1_000, TODAY.minusDays(1));
        charge(2_000, TODAY);
        charge(4_000, null);
        UUID paidPast = charge(8_000, TODAY.minusDays(30));
        payment(8_000);
        apply(paidPast);
        Ledger l = ledger();
        assertThat(l.overdueCents(TODAY)).isEqualTo(1_000);
        assertThat(l.charge(past).overdue(TODAY)).isTrue();
        assertThat(l.charge(paidPast).overdue(TODAY)).isFalse();
    }

    @Test
    void waiversReduceWhatIsOwedAndAFullWaiverClosesTheCharge() {
        UUID c = charge(10_000, null);
        adjust(AdjustmentKind.WAIVER, 4_000, c, null);
        assertThat(ledger().charge(c).status()).isEqualTo(Ledger.ChargeStatus.PART_PAID);
        assertThat(ledger().charge(c).outstandingCents()).isEqualTo(6_000);
        adjust(AdjustmentKind.WAIVER, 6_000, c, null);
        assertThat(ledger().charge(c).status()).isEqualTo(Ledger.ChargeStatus.WAIVED);
        assertThat(ledger().balanceCents()).isZero();
        assertConsistent();
    }

    @Test
    void voidingAPaidChargeFreesTheMoneyForOtherCharges() {
        UUID wrong = charge(5_000, LocalDate.of(2026, 11, 1));
        UUID right = charge(5_000, LocalDate.of(2026, 12, 1));
        payment(5_000);
        apply(null);
        assertThat(ledger().charge(wrong).status()).isEqualTo(Ledger.ChargeStatus.PAID);

        release(ledger().activeOnCharge(wrong));
        adjust(AdjustmentKind.VOID, 5_000, wrong, null);
        apply(null);

        Ledger l = ledger();
        assertThat(l.charge(wrong).status()).isEqualTo(Ledger.ChargeStatus.VOID);
        assertThat(l.charge(wrong).outstandingCents()).isZero();
        assertThat(l.charge(right).status()).isEqualTo(Ledger.ChargeStatus.PAID);
        assertThat(l.balanceCents()).isZero();
        assertConsistent();
    }

    @Test
    void reversingAPaymentReopensWhatItPaid() {
        UUID c = charge(5_000, null);
        UUID p = payment(5_000);
        apply(null);
        release(ledger().activeFromPayment(p));
        adjust(AdjustmentKind.REVERSAL, 5_000, null, p);
        apply(null);

        Ledger l = ledger();
        assertThat(l.payment(p).reversed()).isTrue();
        assertThat(l.payment(p).availableCents()).isZero();
        assertThat(l.charge(c).status()).isEqualTo(Ledger.ChargeStatus.OPEN);
        assertThat(l.balanceCents()).isEqualTo(5_000);
        assertThat(l.collectedCents()).isZero();
        assertConsistent();
    }

    @Test
    void refundsComeOutOfUnusedMoney() {
        charge(3_000, null);
        UUID p = payment(5_000);
        apply(null);
        adjust(AdjustmentKind.REFUND, 2_000, null, p);
        Ledger l = ledger();
        assertThat(l.payment(p).availableCents()).isZero();
        assertThat(l.payment(p).refundedCents()).isEqualTo(2_000);
        assertThat(l.balanceCents()).isZero();
        assertThat(l.collectedCents()).isEqualTo(3_000);
        assertConsistent();
    }

    @Test
    void aCreditPaysChargesLikeMoneyButIsNotCollected() {
        UUID c = charge(5_000, null);
        adjust(AdjustmentKind.CREDIT, 2_000, null, null);
        apply(null);
        Ledger l = ledger();
        assertThat(l.charge(c).outstandingCents()).isEqualTo(3_000);
        assertThat(l.balanceCents()).isEqualTo(3_000);
        assertThat(l.collectedCents()).isZero();
        assertConsistent();
    }

    @Test
    void theStatementListsEveryRowInOrderWithARunningBalance() {
        UUID c = charge(5_000, null);
        payment(2_000);
        adjust(AdjustmentKind.WAIVER, 1_000, c, null);
        List<Ledger.Entry> entries = ledger().statement(SGT);
        assertThat(entries).extracting(Ledger.Entry::kind)
                .containsExactly(Ledger.EntryKind.CHARGE, Ledger.EntryKind.PAYMENT, Ledger.EntryKind.WAIVER);
        assertThat(entries).extracting(Ledger.Entry::amountCents).containsExactly(5_000L, -2_000L, -1_000L);
        assertThat(entries).extracting(Ledger.Entry::balanceCents).containsExactly(5_000L, 3_000L, 2_000L);
        assertThat(entries.get(2).reference()).isEqualTo("HWK-C-0");
        assertThat(entries.get(2).description()).isEqualTo("Waived: because");
    }

    @Test
    void moneyIsFormattedForMessages() {
        assertThat(Money.format(123_450)).isEqualTo("S$1,234.50");
        assertThat(Money.format(5)).isEqualTo("S$0.05");
        assertThat(Money.format(-2_000)).isEqualTo("-S$20.00");
    }
}
