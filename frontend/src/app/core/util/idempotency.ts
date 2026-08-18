/**
 * Generates the Idempotency-Key sent with an order.
 *
 * One key is minted per user submission and reused if that same submission is
 * retried, so a double-click or a retry after a flaky network cannot create two
 * positions — the backend returns the original order instead.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `key-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
