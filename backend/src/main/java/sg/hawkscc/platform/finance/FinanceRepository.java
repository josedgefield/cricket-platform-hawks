package sg.hawkscc.platform.finance;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import sg.hawkscc.platform.finance.domain.LedgerRows;
import sg.hawkscc.platform.finance.domain.LedgerRows.Adjustment;
import sg.hawkscc.platform.finance.domain.LedgerRows.AdjustmentKind;
import sg.hawkscc.platform.finance.domain.LedgerRows.Allocation;
import sg.hawkscc.platform.finance.domain.LedgerRows.Charge;
import sg.hawkscc.platform.finance.domain.LedgerRows.Payment;

/**
 * SQL for the ledger (V5__finance.sql). Inserts only: the tables reject updates and deletes.
 * It reads the members table (owned by identity) for names and to lock a member while their
 * ledger changes; it never writes to it.
 */
@Repository
class FinanceRepository {

    private final JdbcClient jdbc;

    FinanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record MemberRef(UUID id, String displayName, String status) {
    }

    // ---------- members (read only) ----------

    /** Locks the member's row until the transaction ends, so their ledger changes one at a time. */
    Optional<MemberRef> lockMember(UUID clubId, UUID memberId) {
        return jdbc.sql("select id, display_name, status from members where club_id = :club and id = :id for update")
                .param("club", clubId).param("id", memberId).query(FinanceRepository::memberRef).optional();
    }

    Optional<MemberRef> findMember(UUID clubId, UUID memberId) {
        return jdbc.sql("select id, display_name, status from members where club_id = :club and id = :id")
                .param("club", clubId).param("id", memberId).query(FinanceRepository::memberRef).optional();
    }

    List<MemberRef> members(UUID clubId) {
        return jdbc.sql("select id, display_name, status from members where club_id = :club order by lower(display_name), id")
                .param("club", clubId).query(FinanceRepository::memberRef).list();
    }

    private static MemberRef memberRef(ResultSet rs, int n) throws SQLException {
        return new MemberRef(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("status"));
    }

    // ---------- reading the ledger ----------

    LedgerRows ledger(UUID clubId, UUID memberId) {
        return ledgers(clubId, memberId).getOrDefault(memberId, LedgerRows.EMPTY);
    }

    /** Every member's rows, for the admin overview. */
    Map<UUID, LedgerRows> ledgers(UUID clubId) {
        return ledgers(clubId, null);
    }

    private Map<UUID, LedgerRows> ledgers(UUID clubId, UUID memberId) {
        String filter = " where club_id = :club and (cast(:member as uuid) is null or member_id = cast(:member as uuid))";
        Map<UUID, List<Charge>> charges = new LinkedHashMap<>();
        Map<UUID, List<Payment>> payments = new LinkedHashMap<>();
        Map<UUID, List<Adjustment>> adjustments = new LinkedHashMap<>();
        Map<UUID, List<Allocation>> allocations = new LinkedHashMap<>();

        jdbc.sql("select id, member_id, reference, description, amount_cents, due_date, created_at from charges"
                        + filter + " order by created_at, reference")
                .param("club", clubId).param("member", memberId)
                .query((rs, n) -> {
                    charges.computeIfAbsent(rs.getObject("member_id", UUID.class), k -> new ArrayList<>())
                            .add(new Charge(rs.getObject("id", UUID.class), rs.getString("reference"),
                                    rs.getString("description"), rs.getLong("amount_cents"), date(rs, "due_date"),
                                    rs.getTimestamp("created_at").toInstant()));
                    return n;
                }).list();
        jdbc.sql("select id, member_id, reference, amount_cents, method, received_on, external_ref, note, created_at from payments"
                        + filter + " order by created_at, reference")
                .param("club", clubId).param("member", memberId)
                .query((rs, n) -> {
                    payments.computeIfAbsent(rs.getObject("member_id", UUID.class), k -> new ArrayList<>())
                            .add(new Payment(rs.getObject("id", UUID.class), rs.getString("reference"),
                                    rs.getLong("amount_cents"), rs.getString("method"), date(rs, "received_on"),
                                    rs.getString("external_ref"), rs.getString("note"),
                                    rs.getTimestamp("created_at").toInstant()));
                    return n;
                }).list();
        jdbc.sql("select id, member_id, kind, amount_cents, charge_id, payment_id, reason, created_at from adjustments"
                        + filter + " order by created_at, id")
                .param("club", clubId).param("member", memberId)
                .query((rs, n) -> {
                    adjustments.computeIfAbsent(rs.getObject("member_id", UUID.class), k -> new ArrayList<>())
                            .add(new Adjustment(rs.getObject("id", UUID.class), AdjustmentKind.fromCode(rs.getString("kind")),
                                    rs.getLong("amount_cents"), rs.getObject("charge_id", UUID.class),
                                    rs.getObject("payment_id", UUID.class), rs.getString("reason"),
                                    rs.getTimestamp("created_at").toInstant()));
                    return n;
                }).list();
        jdbc.sql("select id, member_id, payment_id, credit_adjustment_id, charge_id, amount_cents, releases from payment_allocations"
                        + filter + " order by created_at, id")
                .param("club", clubId).param("member", memberId)
                .query((rs, n) -> {
                    allocations.computeIfAbsent(rs.getObject("member_id", UUID.class), k -> new ArrayList<>())
                            .add(new Allocation(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                                    rs.getObject("credit_adjustment_id", UUID.class), rs.getObject("charge_id", UUID.class),
                                    rs.getLong("amount_cents"), rs.getObject("releases", UUID.class)));
                    return n;
                }).list();

        Map<UUID, LedgerRows> out = new LinkedHashMap<>();
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
        ids.addAll(charges.keySet());
        ids.addAll(payments.keySet());
        ids.addAll(adjustments.keySet());
        for (UUID id : ids) {
            out.put(id, new LedgerRows(charges.getOrDefault(id, List.of()), payments.getOrDefault(id, List.of()),
                    adjustments.getOrDefault(id, List.of()), allocations.getOrDefault(id, List.of())));
        }
        return out;
    }

    /** Which member a charge or payment belongs to. */
    Optional<UUID> chargeOwner(UUID clubId, UUID chargeId) {
        return jdbc.sql("select member_id from charges where club_id = :club and id = :id")
                .param("club", clubId).param("id", chargeId).query(UUID.class).optional();
    }

    Optional<UUID> paymentOwner(UUID clubId, UUID paymentId) {
        return jdbc.sql("select member_id from payments where club_id = :club and id = :id")
                .param("club", clubId).param("id", paymentId).query(UUID.class).optional();
    }

    // ---------- repeated requests ----------

    record Batch(UUID id, int count) {
    }

    Optional<Batch> findBatch(UUID clubId, String requestKey) {
        return jdbc.sql("""
                        select b.id, (select count(*) from charges c where c.batch_id = b.id) as n
                        from charge_batches b where b.club_id = :club and b.request_key = :key""")
                .param("club", clubId).param("key", requestKey)
                .query((rs, n) -> new Batch(rs.getObject("id", UUID.class), rs.getInt("n"))).optional();
    }

    Optional<UUID> paymentMemberByRequestKey(UUID clubId, String requestKey) {
        return jdbc.sql("select member_id from payments where club_id = :club and request_key = :key")
                .param("club", clubId).param("key", requestKey).query(UUID.class).optional();
    }

    Optional<UUID> adjustmentMemberByRequestKey(UUID clubId, String requestKey) {
        return jdbc.sql("select member_id from adjustments where club_id = :club and request_key = :key")
                .param("club", clubId).param("key", requestKey).query(UUID.class).optional();
    }

    // ---------- inserts ----------

    UUID insertBatch(UUID clubId, String description, long amountCents, LocalDate dueDate, String requestKey, UUID by) {
        return jdbc.sql("""
                        insert into charge_batches (club_id, description, amount_cents, due_date, request_key, created_by)
                        values (:club, :description, :amount, :due, :key, :by) returning id""")
                .param("club", clubId).param("description", description).param("amount", amountCents)
                .param("due", dueDate == null ? null : Date.valueOf(dueDate)).param("key", requestKey).param("by", by)
                .query(UUID.class).single();
    }

    UUID insertCharge(UUID clubId, UUID memberId, UUID batchId, String description, long amountCents,
                      LocalDate dueDate, UUID by) {
        return jdbc.sql("""
                        insert into charges (club_id, member_id, batch_id, reference, description, amount_cents, due_date, created_by)
                        values (:club, :member, :batch, 'HWK-C-' || lpad(nextval('charge_reference_seq')::text, 5, '0'),
                                :description, :amount, :due, :by)
                        returning id""")
                .param("club", clubId).param("member", memberId).param("batch", batchId)
                .param("description", description).param("amount", amountCents)
                .param("due", dueDate == null ? null : Date.valueOf(dueDate)).param("by", by)
                .query(UUID.class).single();
    }

    UUID insertPayment(UUID clubId, UUID memberId, long amountCents, String method, LocalDate receivedOn,
                       String externalRef, String note, String requestKey, UUID by) {
        return jdbc.sql("""
                        insert into payments (club_id, member_id, reference, amount_cents, method, received_on,
                                              external_ref, note, request_key, recorded_by)
                        values (:club, :member, 'HWK-P-' || lpad(nextval('payment_reference_seq')::text, 5, '0'),
                                :amount, :method, :received, :ref, :note, :key, :by)
                        returning id""")
                .param("club", clubId).param("member", memberId).param("amount", amountCents).param("method", method)
                .param("received", Date.valueOf(receivedOn)).param("ref", externalRef).param("note", note)
                .param("key", requestKey).param("by", by)
                .query(UUID.class).single();
    }

    UUID insertAdjustment(UUID clubId, UUID memberId, AdjustmentKind kind, long amountCents, UUID chargeId,
                          UUID paymentId, String reason, String requestKey, UUID by) {
        return jdbc.sql("""
                        insert into adjustments (club_id, member_id, kind, amount_cents, charge_id, payment_id, reason,
                                                 request_key, approved_by)
                        values (:club, :member, :kind, :amount, :charge, :payment, :reason, :key, :by)
                        returning id""")
                .param("club", clubId).param("member", memberId).param("kind", kind.code()).param("amount", amountCents)
                .param("charge", chargeId).param("payment", paymentId).param("reason", reason)
                .param("key", requestKey).param("by", by)
                .query(UUID.class).single();
    }

    void insertAllocation(UUID clubId, UUID memberId, UUID paymentId, UUID creditAdjustmentId, UUID chargeId,
                          long amountCents, UUID releases, UUID by) {
        jdbc.sql("""
                        insert into payment_allocations (club_id, member_id, payment_id, credit_adjustment_id, charge_id,
                                                         amount_cents, releases, created_by)
                        values (:club, :member, :payment, :credit, :charge, :amount, :releases, :by)""")
                .param("club", clubId).param("member", memberId).param("payment", paymentId)
                .param("credit", creditAdjustmentId).param("charge", chargeId).param("amount", amountCents)
                .param("releases", releases).param("by", by)
                .update();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }
}
