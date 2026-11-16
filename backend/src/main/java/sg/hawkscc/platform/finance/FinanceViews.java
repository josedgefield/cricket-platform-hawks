package sg.hawkscc.platform.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the finance API returns. Amounts are whole cents (SGD). {@code balanceCents} is what
 * the member owes when positive, and their credit when negative; it is always calculated
 * from the ledger rows.
 */
public final class FinanceViews {

    private FinanceViews() {
    }

    /**
     * @param owedCents     still to pay across charges
     * @param unusedCents   money received or credited that isn't applied to a charge yet
     * @param overdueCents  the part of owedCents that is past its due date
     */
    public record Statement(UUID memberId, String displayName, long balanceCents, long owedCents, long unusedCents,
                            long overdueCents, List<ChargeLine> charges, List<PaymentLine> payments,
                            List<Entry> entries) {
    }

    /** @param status open, part_paid, paid, waived or void */
    public record ChargeLine(UUID id, String reference, String description, long amountCents, long paidCents,
                             long writtenOffCents, long outstandingCents, LocalDate dueDate, String status,
                             boolean overdue, Instant createdAt) {
    }

    /** @param note only shown to admins */
    public record PaymentLine(UUID id, String reference, long amountCents, String method, LocalDate receivedOn,
                              String externalRef, String note, long refundedCents, long unallocatedCents,
                              boolean reversed, Instant createdAt) {
    }

    /** @param amountCents positive increases what the member owes; negative reduces it */
    public record Entry(Instant at, LocalDate date, String kind, String reference, String description,
                        long amountCents, long balanceCents) {
    }

    public record Overview(long owedCents, long overdueCents, long unusedCents, long collectedCents,
                           List<MemberBalance> members) {
    }

    public record MemberBalance(UUID memberId, String displayName, String memberStatus, long balanceCents,
                                long owedCents, long unusedCents, long overdueCents, int openCharges) {
    }

    public record ChargesRaised(UUID batchId, int count) {
    }
}
