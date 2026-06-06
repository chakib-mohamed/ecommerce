package the.chak.ecommerce.products.control;

/**
 * Internal post-commit signal fired after an outbox row is appended within a business transaction.
 * It is observed at {@code AFTER_SUCCESS} to wake the relay so the row is published without waiting
 * for the next scheduled tick. It carries no data - the relay reads the committed rows itself - and
 * is best-effort: if the signal is lost, the scheduled tick still delivers the row.
 */
public final class OutboxAppended {

    public static final OutboxAppended INSTANCE = new OutboxAppended();

    private OutboxAppended() {
    }
}
