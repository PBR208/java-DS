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

  /**
   * Creates an empty stack using the default initial capacity.
   *
   * This is the constructor to use whenever the eventual number of elements is
   * unknown, which is the common case for stacks driving recursive or
   * backtracking algorithms. The chosen capacity is only a starting point; the
   * stack grows on demand and the caller never observes a capacity limit.
   *
   * Time complexity: O(1) - a single fixed-size allocation.
   * Space complexity: O(1) - the default capacity is a compile-time constant.
   */
  public ArrayStack() {
    // Delegate to the capacity-aware constructor so that the allocation rules
    // exist in exactly one place and cannot drift apart over time.
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * Creates an empty stack whose backing array is pre-sized for the expected
   * number of elements.
   *
   * Callers that already know how many elements they will push, for example when
   * reversing a collection of known length, can use this constructor to avoid
   * the intermediate copies that automatic growth would otherwise perform. The
   * request is treated as a performance hint only and never as a hard limit; the
   * stack still grows beyond it on demand.
   *
   * Time complexity: O(n) in the requested capacity n, because the JVM zeroes the
   * freshly allocated array.
   * Space complexity: O(n) in the requested capacity n.
   *
   * @param pInitialCapacity
   * Expected number of elements the stack should hold before its first growth
   * step. Values below the internal minimum capacity, including zero and
   * negative values, are silently raised to that minimum rather than rejected,
   * because an undersized hint is a performance detail and not a usage error
   * that should abort the caller.
   */
  public ArrayStack(int pInitialCapacity) {
    // Guard the allocation against degenerate hints: a zero-length array could
    // never be enlarged by the multiplicative growth strategy, which would leave
    // the stack permanently unable to accept elements.
    int capacity = Math.max(pInitialCapacity, MINIMUM_CAPACITY);

    // Allocate the backing storage as Object[] because generic array creation is
    // impossible under type erasure; element types are enforced by the API.
    elements = new Object[capacity];

    // A freshly allocated stack holds no elements, so the next push writes to
    // index zero.
    size = 0;
  }

  /**
   * Checks whether the stack currently holds any elements.
   *
   * This is the guard clause callers are expected to use before reading or
   * removing the top element, because both of those operations are defined to
   * fail silently on an empty stack rather than to raise an exception. Emptiness
   * is decided by the logical size alone and is independent of the capacity of
   * the backing array, which may still be large after many removals.
   *
   * Time complexity: O(1) - a single counter comparison.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * True when no element is stored, false as soon as at least one element has
   * been pushed and not yet removed.
   */
  public boolean isEmpty() {
    // The backing array is never null and retains capacity after removals, so
    // only the logical size is a valid emptiness criterion here.
    return size == 0;
  }

  /**
   * Returns the number of elements currently stored in the stack.
   *
   * The value is maintained incrementally by push and pop, so no traversal is
   * required to obtain it. This accessor mainly supports callers that must size
   * an output buffer or report progress; the core LIFO workflow does not depend
   * on it.
   *
   * Time complexity: O(1) - the count is maintained incrementally rather than
   * derived by traversal.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * Element count between zero and the current capacity, never negative.
   */
  public int size() {
    // The size counter is the single source of truth for the element count.
    return size;
  }

  /**
   * Returns the number of elements the backing array can hold before the next
   * growth step.
   *
   * This is diagnostic information rather than part of the stack contract: the
   * value changes on its own as the structure resizes, and callers must not
   * treat it as an upper bound on how many elements they may push. It is exposed
   * because observing the growth and shrink behaviour is a central learning goal
   * of this reference implementation.
   *
   * Time complexity: O(1) - a direct array length read.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * Current length of the backing array, always at least the internal minimum
   * capacity and always greater than or equal to the current size.
   */
  public int capacity() {
    // Report the physical storage size, which the resize strategy adjusts
    // independently of the logical element count.
    return elements.length;
  }

  /**
   * Pushes an element onto the top of the stack.
   *
   * The element becomes the one returned by the next call to top and the one
   * discarded by the next call to pop. If the backing array is exhausted it is
   * transparently replaced by a larger one before the write, so the caller never
   * encounters an overflow condition and never has to manage capacity manually.
   *
   * Null arguments leave the stack unchanged instead of raising an exception.
   * This mirrors the behaviour of the node-based {@link Stack} in this package
   * and keeps null out of the storage entirely, which is precisely what allows
   * top to use null unambiguously as its empty-stack signal.
   *
   * Time complexity: O(1) amortised, O(n) worst case. Almost every call is a
   * single indexed write; the call that exhausts the array copies all n stored
   * elements, but doubling makes that rare enough for the total cost of n pushes
   * to remain linear.
   * Space complexity: O(1) amortised per element. A growth step temporarily
   * holds both the old and the new array, so peak usage during that step is O(n).
   *
   * @param pContent
   * Element to place on top of the stack. May be any ContentType instance;
   * passing null is tolerated and silently ignored, so callers that must
   * distinguish "stored nothing" from "stored a value" have to check for null
   * before calling.
   */
  public void push(ContentType pContent) {
    // Reject null early: storing it would break the contract of top, which
    // reports an empty stack by returning null.
    if (pContent == null) {
      return;
    }

    // Grow before writing whenever the backing array is full, because the write
    // index below is the current size and must stay inside the array bounds.
    if (size == elements.length) {
      resize(elements.length * GROWTH_FACTOR);
    }

    // The top of the stack is the highest occupied index, so the new element is
    // appended at the first free slot and the counter moves up with it.
    elements[size] = pContent;
    size++;
  }

  /**
   * Replaces the backing array with one of the requested capacity, preserving
   * all stored elements and their order.
   *
   * This is the single point at which storage is reallocated; both the growth
   * path of push and the shrink path of pop delegate here so that the invariants
   * around capacity live in one place. The operation copies every stored element
   * and is therefore the expensive step that the doubling and quartering
   * thresholds are designed to keep rare.
   *
   * Time complexity: O(n) in the current element count n, dominated by the bulk
   * copy of the live elements.
   * Space complexity: O(pNewCapacity), with both arrays alive simultaneously
   * until the new reference is published.
   *
   * @param pNewCapacity
   * Desired length of the new backing array. Must be large enough to hold the
   * current element count, which every call site guarantees; values below the
   * internal minimum capacity are raised to it so that the array can always be
   * doubled again later.
   */
  private void resize(int pNewCapacity) {
    // Never fall below the minimum, otherwise a zero-length array would leave the
    // multiplicative growth strategy unable to produce any additional space.
    int targetCapacity = Math.max(pNewCapacity, MINIMUM_CAPACITY);

    // Allocate the replacement storage; Object[] is required for the same
    // erasure reason that applies to the original allocation.
    Object[] resizedElements = new Object[targetCapacity];

    // Transfer the live elements only. Indices at or above the current size hold
    // either nothing or already-cleared slots and must not be carried over.
    System.arraycopy(elements, 0, resizedElements, 0, size);

    // Publish the new storage; the previous array becomes unreachable and is
    // reclaimed by the garbage collector.
    elements = resizedElements;
  }
}
