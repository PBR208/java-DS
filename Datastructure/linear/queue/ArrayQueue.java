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
}
