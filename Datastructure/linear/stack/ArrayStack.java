package linear.stack;

/**
 * Generic array-backed stack implementation following the Last-In, First-Out
 * (LIFO) principle.
 *
 * This class exists as the contiguous-memory counterpart to the node-based
 * {@link Stack} in the same package. Both expose an identical contract, but they
 * answer different engineering questions: the linked variant allocates one node
 * per element and therefore never copies data, whereas this variant stores all
 * elements in a single backing array and pays an occasional copy in order to
 * gain cache-friendly memory locality and a far smaller per-element footprint.
 * Weighing those two trade-offs against each other is a standard discussion
 * point in data structure interviews, which is why both implementations are kept
 * side by side in this reference library.
 *
 * The backing array grows automatically when it is exhausted and shrinks again
 * once the stack occupies only a small fraction of it, so callers never have to
 * reason about capacity. Capacity management is an internal concern and is not
 * part of the public contract beyond the informational {@link #capacity()}
 * accessor.
 *
 * This class is not thread-safe. Concurrent modification from multiple threads
 * corrupts the size counter and the backing array; external synchronisation is
 * required whenever instances are shared across threads.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class ArrayStack<ContentType> {

  /**
   * Capacity used when the caller does not state an expected element count.
   *
   * The value is deliberately small: a stack that is created but barely used
   * should not reserve a large block of memory, and the growth strategy reaches
   * larger capacities within a few doubling steps anyway.
   */
  private static final int DEFAULT_INITIAL_CAPACITY = 16;

  /**
   * Multiplier applied to the current capacity whenever the backing array is
   * exhausted.
   *
   * Doubling is what makes the amortised cost of push constant: the cost of all
   * copy operations performed up to the n-th push forms a geometric series and
   * therefore stays linear in n overall. A constant additive growth step would
   * degrade push to linear amortised cost.
   */
  private static final int GROWTH_FACTOR = 2;

  /**
   * Divisor that defines how empty the backing array must be before it is
   * shrunk.
   *
   * Shrinking is only performed once the stack holds a quarter of the capacity
   * or less. Reacting already at half capacity would allow an alternating
   * push/pop sequence at the boundary to trigger a copy on every single call,
   * which is the classic thrashing pitfall of naive resizing.
   */
  private static final int SHRINK_THRESHOLD_DIVISOR = 4;

  /**
   * Lower bound for the length of the backing array.
   *
   * Repeated shrinking must never reach zero, because a zero-length array can no
   * longer be doubled and the growth logic would stop making progress.
   */
  private static final int MINIMUM_CAPACITY = 1;

  /**
   * Backing storage holding the stack elements in insertion order, with the
   * bottom of the stack at index zero.
   *
   * The field is typed as Object[] rather than ContentType[] because Java erases
   * generic types at compile time and therefore forbids creating an array of a
   * type parameter. Every element written into this array is a ContentType
   * instance, so the read path can cast safely; that cast is confined to a single
   * accessor to keep the unchecked operation auditable.
   */
  private Object[] elements;

  /**
   * Number of elements currently stored in the stack.
   *
   * This is the logical size and is always less than or equal to the length of
   * the backing array. It doubles as the write index for the next push, since
   * the top element resides at index size - 1.
   */
  private int size;
}
