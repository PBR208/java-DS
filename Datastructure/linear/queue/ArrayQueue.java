package linear.queue;

/**
 * Generic array-backed queue implementation following the First-In, First-Out
 * (FIFO) principle.
 *
 * This class is the contiguous-memory counterpart to the node-based
 * {@link Queue} in the same package and exposes an identical contract. The two
 * differ in how they pay for a removal: the linked variant unlinks a node and
 * lets the garbage collector reclaim it, whereas this variant advances an index
 * into a shared array and therefore avoids one allocation per element entirely.
 * Keeping both side by side makes the memory-versus-locality trade-off concrete,
 * which is a recurring discussion point when queues are chosen for breadth-first
 * traversals or producer/consumer buffers.
 *
 * The central design decision is that the backing array is treated as a ring:
 * the front of the queue may sit at any index, and the region occupied by the
 * elements is allowed to wrap around the end of the array back to index zero.
 * Without that treatment, every dequeue would have to shift all remaining
 * elements one position towards the front, which would make removal linear
 * instead of constant. All index arithmetic in this class is therefore modular.
 *
 * The ring grows automatically when it is exhausted and shrinks again once it is
 * mostly empty, so callers never have to reason about capacity. Capacity
 * management is an internal concern and is not part of the public contract
 * beyond the informational {@link #capacity()} accessor.
 *
 * This class is not thread-safe. Concurrent modification from multiple threads
 * corrupts the head index and the size counter independently of each other,
 * which can expose elements that were already removed; external synchronisation
 * is required whenever instances are shared across threads.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class ArrayQueue<ContentType> {

  /**
   * Capacity used when the caller does not state an expected element count.
   *
   * The value is deliberately small: a queue that is created but barely used
   * should not reserve a large block of memory, and the growth strategy reaches
   * larger capacities within a few doubling steps anyway.
   */
  private static final int DEFAULT_INITIAL_CAPACITY = 16;

  /**
   * Multiplier applied to the current capacity whenever the ring is exhausted.
   *
   * Doubling is what makes the amortised cost of enqueue constant: the cost of
   * all copy operations performed up to the n-th enqueue forms a geometric
   * series and therefore stays linear in n overall. A constant additive growth
   * step would degrade enqueue to linear amortised cost.
   */
  private static final int GROWTH_FACTOR = 2;

  /**
   * Divisor that defines how empty the ring must be before it is shrunk.
   *
   * Shrinking is only performed once the queue holds a quarter of the capacity
   * or less. Reacting already at half capacity would allow an alternating
   * enqueue/dequeue sequence at the boundary to trigger a copy on every single
   * call, which is the classic thrashing pitfall of naive resizing.
   */
  private static final int SHRINK_THRESHOLD_DIVISOR = 4;

  /**
   * Lower bound for the length of the backing array.
   *
   * Repeated shrinking must never reach zero, because a zero-length array can no
   * longer be doubled and, more immediately, the modulo operations that drive
   * the ring arithmetic would divide by zero.
   */
  private static final int MINIMUM_CAPACITY = 1;

  /**
   * Backing storage holding the queue elements as a ring buffer.
   *
   * Elements occupy the logical range starting at the head index and spanning
   * the current size, wrapping around the end of the array when necessary. Slots
   * outside that range are always null so that removed elements do not stay
   * reachable. The field is typed as Object[] rather than ContentType[] because
   * Java erases generic types at compile time and therefore forbids creating an
   * array of a type parameter; every element written here is a ContentType
   * instance, so the read path can cast safely.
   */
  private Object[] elements;

  /**
   * Index of the element at the front of the queue, which is the next one to be
   * returned by front and removed by dequeue.
   *
   * The value is meaningful only while the queue is non-empty. It advances
   * modulo the array length on every removal and is reset to zero whenever the
   * ring is reallocated, because a resize is the natural opportunity to unwrap
   * the elements back into their natural order.
   */
  private int head;

  /**
   * Number of elements currently stored in the queue.
   *
   * This is the logical size and is always less than or equal to the length of
   * the backing array. Together with the head index it fully determines which
   * slots are occupied, which is why the tail position is derived rather than
   * stored: a separate tail index could drift out of sync with the size and
   * would make a full ring indistinguishable from an empty one.
   */
  private int size;

  /**
   * Creates an empty queue using the default initial capacity.
   *
   * This is the constructor to use whenever the eventual number of elements is
   * unknown, which is the common case for queues that buffer work items or hold
   * the frontier of a breadth-first traversal. The chosen capacity is only a
   * starting point; the ring grows on demand and the caller never observes a
   * capacity limit.
   *
   * Time complexity: O(1) - a single fixed-size allocation.
   * Space complexity: O(1) - the default capacity is a compile-time constant.
   */
  public ArrayQueue() {
    // Delegate to the capacity-aware constructor so that the allocation rules
    // exist in exactly one place and cannot drift apart over time.
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * Creates an empty queue whose ring buffer is pre-sized for the expected
   * number of elements.
   *
   * Callers that already know how many elements will be buffered, for example
   * when the queue mirrors a collection of known length, can use this
   * constructor to avoid the intermediate copies that automatic growth would
   * otherwise perform. The request is treated as a performance hint only and
   * never as a hard limit; the queue still grows beyond it on demand.
   *
   * Time complexity: O(n) in the requested capacity n, because the JVM zeroes the
   * freshly allocated array.
   * Space complexity: O(n) in the requested capacity n.
   *
   * @param pInitialCapacity
   * Expected number of elements the queue should hold before its first growth
   * step. Values below the internal minimum capacity, including zero and
   * negative values, are silently raised to that minimum rather than rejected,
   * because an undersized hint is a performance detail and not a usage error
   * that should abort the caller.
   */
  public ArrayQueue(int pInitialCapacity) {
    // Guard the allocation against degenerate hints. A zero-length array would
    // not only be impossible to enlarge by multiplication, it would also make
    // the modulo arithmetic of the ring divide by zero on the first enqueue.
    int capacity = Math.max(pInitialCapacity, MINIMUM_CAPACITY);

    // Allocate the ring as Object[] because generic array creation is impossible
    // under type erasure; element types are enforced by the API instead.
    elements = new Object[capacity];

    // An empty ring has no wrap-around yet, so the front starts at the natural
    // beginning of the array.
    head = 0;

    // A freshly allocated queue holds no elements.
    size = 0;
  }

  /**
   * Checks whether the queue currently holds any elements.
   *
   * This is the guard clause callers are expected to use before reading or
   * removing the front element, because both of those operations are defined to
   * fail silently on an empty queue rather than to raise an exception. Emptiness
   * is decided by the size counter alone; the head index cannot serve that
   * purpose, since in a ring buffer the same head position occurs for both an
   * empty and a completely full queue.
   *
   * Time complexity: O(1) - a single counter comparison.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * True when no element is stored, false as soon as at least one element has
   * been enqueued and not yet removed.
   */
  public boolean isEmpty() {
    // The size counter is the only unambiguous emptiness criterion for a ring.
    return size == 0;
  }

  /**
   * Returns the number of elements currently stored in the queue.
   *
   * The value is maintained incrementally by enqueue and dequeue, so no
   * traversal is required to obtain it. This accessor mainly supports callers
   * that must size an output buffer, enforce a backlog limit, or report how much
   * work is still pending; the core FIFO workflow does not depend on it.
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
   * Returns the number of elements the ring buffer can hold before the next
   * growth step.
   *
   * This is diagnostic information rather than part of the queue contract: the
   * value changes on its own as the structure resizes, and callers must not
   * treat it as an upper bound on how many elements they may enqueue. It is
   * exposed because observing the growth and shrink behaviour is a central
   * learning goal of this reference implementation.
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
   * Appends an element to the end of the queue.
   *
   * The element takes its place behind all elements that are already waiting and
   * will only be returned by front once every one of them has been removed. If
   * the ring is exhausted it is transparently replaced by a larger one before
   * the write, so the caller never encounters an overflow condition and never
   * has to manage capacity manually.
   *
   * Null arguments leave the queue unchanged instead of raising an exception.
   * This mirrors the behaviour of the node-based {@link Queue} in this package
   * and keeps null out of the storage entirely, which is precisely what allows
   * front to use null unambiguously as its empty-queue signal.
   *
   * Time complexity: O(1) amortised, O(n) worst case. Almost every call is a
   * single indexed write; the call that exhausts the ring copies all n stored
   * elements, but doubling makes that rare enough for the total cost of n
   * enqueues to remain linear.
   * Space complexity: O(1) amortised per element. A growth step temporarily
   * holds both the old and the new array, so peak usage during that step is O(n).
   *
   * @param pContent
   * Element to append to the queue. May be any ContentType instance; passing
   * null is tolerated and silently ignored, so callers that must distinguish
   * "stored nothing" from "stored a value" have to check for null before
   * calling.
   */
  public void enqueue(ContentType pContent) {
    // Reject null early: storing it would break the contract of front, which
    // reports an empty queue by returning null.
    if (pContent == null) {
      return;
    }

    // Grow before writing whenever the ring is full. A full ring is the one case
    // in which the derived tail position below would collide with the head and
    // silently overwrite the oldest element.
    if (size == elements.length) {
      resize(elements.length * GROWTH_FACTOR);
    }

    // Derive the first free slot from the front position and the element count.
    // The modulo is what lets the occupied region wrap past the end of the array
    // instead of forcing the elements to be shifted back to index zero.
    int tail = (head + size) % elements.length;

    // Place the element at the back of the queue and account for it.
    elements[tail] = pContent;
    size++;
  }

  /**
   * Replaces the ring buffer with one of the requested capacity, preserving all
   * stored elements and their FIFO order.
   *
   * This is the single point at which storage is reallocated; both the growth
   * path of enqueue and the shrink path of dequeue delegate here so that the
   * invariants around capacity live in one place. Besides resizing, the method
   * normalises the ring: the elements are rewritten starting at index zero and
   * the head is reset accordingly, so a queue that had wrapped around the end of
   * the old array comes out contiguous again.
   *
   * Time complexity: O(n) in the current element count n, dominated by the
   * element-by-element transfer.
   * Space complexity: O(pNewCapacity), with both arrays alive simultaneously
   * until the new reference is published.
   *
   * @param pNewCapacity
   * Desired length of the new ring buffer. Must be large enough to hold the
   * current element count, which every call site guarantees; values below the
   * internal minimum capacity are raised to it so that the array can always be
   * doubled again later and the modulo arithmetic never divides by zero.
   */
  private void resize(int pNewCapacity) {
    // Never fall below the minimum, otherwise a zero-length array would leave the
    // multiplicative growth strategy unable to produce any additional space.
    int targetCapacity = Math.max(pNewCapacity, MINIMUM_CAPACITY);

    // Allocate the replacement ring; Object[] is required for the same erasure
    // reason that applies to the original allocation.
    Object[] resizedElements = new Object[targetCapacity];

    /*
     * Transfer in logical order rather than by a bulk array copy:
     * 1. The occupied region may wrap past the end of the old array, so a single
     *    contiguous copy cannot express it.
     * 2. Reading through the modulo expression walks the elements from front to
     *    back regardless of where the wrap point sits.
     * 3. Writing them to ascending indices from zero unwraps the ring, which is
     *    what allows the head to be reset below.
     */
    for (int offset = 0; offset < size; offset++) {
      resizedElements[offset] = elements[(head + offset) % elements.length];
    }

    // Publish the new storage; the previous array becomes unreachable and is
    // reclaimed by the garbage collector.
    elements = resizedElements;

    // The elements were just written contiguously from index zero, so the front
    // of the queue now sits at the natural beginning of the array again.
    head = 0;
  }
}
