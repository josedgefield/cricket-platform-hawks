package sg.hawkscc.platform.finance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** One member's ledger rows, exactly as stored. Amounts are whole cents and always positive. */
public record LedgerRows(List<Charge> charges, List<Payment> payments, List<Adjustment> adjustments,
                         List<Allocation> allocations) {

    public static final LedgerRows EMPTY = new LedgerRows(List.of(), List.of(), List.of(), List.of());

    public record Charge(UUID id, String reference, String description, long amountCents, LocalDate dueDate,
                         Instant createdAt) {
    }

    public record Payment(UUID id, String reference, long amountCents, String method, LocalDate receivedOn,
                          String externalRef, String note, Instant createdAt) {
    }

    public enum AdjustmentKind {
        VOID, WAIVER, CREDIT, REFUND, REVERSAL;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static AdjustmentKind fromCode(String code) {
            for (AdjustmentKind k : values()) {
                if (k.code().equalsIgnoreCase(code == null ? "" : code.strip())) {
                    return k;
                }
            }
            throw new IllegalArgumentException("kind must be void, waiver, credit, refund or reversal");
        }
    }

    public record Adjustment(UUID id, AdjustmentKind kind, long amountCents, UUID chargeId, UUID paymentId,
                             String reason, Instant createdAt) {
    }

    /**
     * Money (a payment, or a credit adjustment) applied to a charge. A row with {@code releases}
     * set takes back the allocation it names; neither row counts after that.
     */
    public record Allocation(UUID id, UUID paymentId, UUID creditAdjustmentId, UUID chargeId, long amountCents,
                             UUID releases) {

        public UUID fundId() {
            return paymentId != null ? paymentId : creditAdjustmentId;
        }
    }
}
