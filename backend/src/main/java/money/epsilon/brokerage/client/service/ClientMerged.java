package money.epsilon.brokerage.client.service;

/**
 * Published (synchronously, inside the merge transaction) before {@code sourceId} is removed: other modules move or drop
 * their own rows of that client. A listener that throws aborts the merge.
 */
public record ClientMerged(long sourceId, String sourceName, long targetId, String targetName, String reason) {
}
