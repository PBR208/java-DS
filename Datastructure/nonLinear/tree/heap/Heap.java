package nonLinear.tree.heap;

import nonLinear.tree.base.ComparableContent;

/**
 * Purpose:
 * Implements a binary heap, a complete binary tree in which every node takes
 * precedence over both of its children, stored implicitly inside a contiguous
 * array rather than through node objects and child references. The structure
 * answers a narrower question than the search trees elsewhere in this package:
 * it cannot look up an arbitrary value efficiently, but it surrenders the most
 * extreme stored element in constant time and restores its own ordering in
 * logarithmic time after every change. That trade is what makes it the standard
 * backing structure for priority queues, for the frontier of Dijkstra's
 * algorithm, and for heap sort. A single class covers both the minimum-first and
 * the maximum-first variant, because the two differ solely in the direction of
 * one comparison and duplicating several hundred lines to invert it would
 * guarantee the copies drift apart.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Array-backed binary heap, generic over a bounded comparable content type and
 * configurable at construction as either a minimum-heap or a maximum-heap.
 *
 * Responsibility: Encapsulates insertion, inspection and removal of the
 * highest-precedence element while continuously maintaining the heap property,
 * namely that every node takes precedence over both of its children under the
 * ordering selected at construction. Note that this is a strictly weaker
 * invariant than the ordering maintained by a binary search tree: it constrains
 * each node only against its own descendants, which is why the heap can be
 * repaired along a single root-to-leaf path instead of requiring rotations.
 *
 * Scope: Used within the nonLinear.tree package wherever repeated access to the
 * most extreme element of a changing collection is required. It is the
 * structural counterpart to the PriorityQueue in the linear package: that class
 * keeps a fully sorted chain and therefore pays O(n) per insertion for O(1)
 * removal, whereas this one keeps a partial order and balances both operations
 * at O(log n).
 *
 * Dependencies: Depends on the ComparableContent interface to establish ordering
 * semantics (isLess, isGreater, isEqual) for the stored content type; relies
 * otherwise only on its own nested Order enum.
 *
 * Thread-safety: This class is not thread-safe. A sift operation temporarily
 * leaves one array slot holding a value that violates the heap property, so
 * concurrent mutation or even concurrent inspection during a mutation from
 * multiple threads without external synchronization may return a wrong result or
 * corrupt the ordering.
 *
 * Lifecycle: A Heap instance begins empty, or pre-populated from a supplied
 * array, and grows and shrinks dynamically through repeated insert and remove
 * operations, with each mutating operation re-establishing the heap property
 * before returning. The ordering direction is fixed at construction and cannot
 * change afterwards, since reversing it would invalidate every stored position.
 *
 * Architectural role: Serves as a foundational, generic priority structure that
 * may be consumed by higher-level algorithms requiring efficient repeated access
 * to extremes, and as the reference implementation demonstrating the implicit
 * array representation of a complete binary tree.
 *
 * @param <ContentType>
 * The type of content stored in the heap. Must implement ComparableContent to
 * provide the ordering comparisons that determine precedence between elements.
 */
public class Heap<ContentType extends ComparableContent<ContentType>> {

    /**
     * Purpose:
     * Selects which extreme of the stored ordering the heap surrenders first.
     * The direction is expressed as a dedicated type rather than a boolean flag
     * so that construction sites read as a statement of intent instead of an
     * opaque true or false, and so that the two directions cannot be transposed
     * by a caller who misremembers which boolean means which.
     *
     * Owner:
     * PBR208 - https://github.com/PBR208/
     *
     * Version:
     * 1.0
     */
    public enum Order {

        /**
         * Orders the heap so that the smallest stored element sits at the root
         * and is the one returned by peek and discarded by remove. This is the
         * direction required by shortest-path and scheduling algorithms, where
         * the priority is a cost, a distance or a timestamp.
         */
        MIN,

        /**
         * Orders the heap so that the largest stored element sits at the root.
         * This is the direction required whenever the most significant item is
         * wanted first, such as when maintaining a running set of top results.
         */
        MAX
    }

    /**
     * Capacity used when the caller states no expected element count.
     *
     * The value is deliberately small: a heap that is created but barely used
     * should not reserve a large block of memory, and the growth strategy
     * reaches larger capacities within a few doubling steps anyway.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * Multiplier applied to the current capacity whenever the backing array is
     * exhausted.
     *
     * Doubling is what keeps the amortised cost of the array management at O(1)
     * per insertion, leaving the logarithmic sift as the genuine cost of the
     * operation. A constant additive growth step would add a linear amortised
     * term that would dominate the sift entirely.
     */
    private static final int GROWTH_FACTOR = 2;

    /**
     * Divisor that defines how empty the backing array must be before it is
     * shrunk.
     *
     * Shrinking is only performed once the heap holds a quarter of the capacity
     * or less. Reacting already at half capacity would allow an alternating
     * insert and remove sequence at the boundary to trigger a copy on every
     * single call, which is the classic thrashing pitfall of naive resizing.
     */
    private static final int SHRINK_THRESHOLD_DIVISOR = 4;

    /**
     * Lower bound for the length of the backing array.
     *
     * Repeated shrinking must never reach zero, because a zero-length array can
     * no longer be doubled and the growth logic would stop making progress.
     */
    private static final int MINIMUM_CAPACITY = 1;

    /**
     * Direction of the ordering maintained by this heap.
     *
     * Fixed at construction and never reassigned, because every element position
     * in the backing array was determined by comparisons under this direction;
     * reversing it would leave the entire structure inconsistent without a full
     * rebuild.
     */
    private final Order order;

    /**
     * Backing storage holding the complete binary tree in level order.
     *
     * The tree structure is implicit rather than stored: the children of the
     * node at index i live at indices 2i+1 and 2i+2, and its parent at
     * (i-1)/2. This is what removes the per-node child references that the
     * search trees in this package carry, and it is only possible because a heap
     * is always a complete tree, so the level-order sequence has no gaps. The
     * field is typed as Object[] rather than ContentType[] because Java erases
     * generic types and therefore forbids creating an array of a type parameter;
     * every element written here is a ContentType instance, so the read path can
     * cast safely.
     */
    private Object[] elements;

    /**
     * Number of elements currently stored in the heap.
     *
     * This is the logical size and is always less than or equal to the length of
     * the backing array. It doubles as the index of the first free slot, which
     * is where an insertion appends before sifting the new element upwards.
     */
    private int size;

    /**
     * Constructs an empty heap with the specified ordering direction and the
     * default initial capacity.
     *
     * Detailed explanation of:
     * - Purpose: Provides the constructor to use whenever the eventual number of
     *   elements is unknown, which is the common case for a heap driving an
     *   algorithm whose frontier grows and shrinks unpredictably.
     * - Business context: The ordering direction is the one decision a caller
     *   must make about a heap, and requiring it here rather than defaulting to
     *   one direction prevents the silent construction of a maximum-heap where a
     *   minimum-heap was intended, a mistake that produces plausible but wrong
     *   results rather than an error.
     * - Processing steps: Delegates to the capacity-aware constructor so that
     *   the allocation rules exist in exactly one place.
     * - Assumptions: Assumes the caller has chosen the direction deliberately;
     *   there is no default because neither direction is more natural.
     * - Side effects: Allocates the backing array at the default capacity.
     *
     * Time complexity: O(1); a single fixed-size allocation.
     * Space complexity: O(1); the default capacity is a compile-time constant.
     *
     * @param pOrder
     * Direction of the ordering to maintain. Must not be null, since every
     * comparison performed by this heap consults it; a null direction is
     * rejected by the delegated constructor.
     */
    public Heap(Order pOrder) {
        // Delegate so that both the validation and the allocation rules live in
        // a single place and cannot drift apart.
        this(pOrder, DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Constructs an empty heap with the specified ordering direction and a
     * backing array pre-sized for the expected number of elements.
     *
     * Detailed explanation of:
     * - Purpose: Allows a caller who already knows how many elements will be
     *   stored to avoid the intermediate copies that automatic growth would
     *   otherwise perform.
     * - Business context: Sizing the array up front matters most when the heap
     *   is loaded in one burst, for example when seeding the frontier of a graph
     *   algorithm with every vertex.
     * - Processing steps: Rejects a null direction, raises a degenerate capacity
     *   hint to the internal minimum, allocates the backing array and records the
     *   direction.
     * - Assumptions: Assumes the capacity hint is meant as a performance
     *   optimisation rather than a limit; the heap grows past it on demand.
     * - Side effects: Allocates the backing array at the requested capacity.
     *
     * Time complexity: O(n) in the requested capacity n, because the JVM zeroes
     * the freshly allocated array.
     * Space complexity: O(n) in the requested capacity n.
     *
     * @param pOrder
     * Direction of the ordering to maintain. Must not be null.
     *
     * @param pInitialCapacity
     * Expected number of elements the heap should hold before its first growth
     * step. Values below the internal minimum capacity, including zero and
     * negative values, are silently raised to that minimum rather than rejected,
     * because an undersized hint is a performance detail and not a usage error.
     *
     * @throws IllegalArgumentException
     * Thrown when pOrder is null. Unlike an undersized capacity hint, a missing
     * ordering direction cannot be defaulted to anything sensible: every
     * comparison the heap performs depends on it, so continuing would silently
     * produce a structure that orders elements arbitrarily. This is the one
     * condition in this class that is reported rather than tolerated.
     */
    public Heap(Order pOrder, int pInitialCapacity) {
        // Reject a missing ordering direction outright. Every precedence
        // decision consults this field, so there is no state in which the heap
        // could behave meaningfully without it.
        if (pOrder == null) {
            throw new IllegalArgumentException(
                    "pOrder must not be null; a heap requires an explicit "
                            + "ordering direction (Order.MIN or Order.MAX).");
        }

        // Record the direction for the lifetime of this instance.
        order = pOrder;

        // Guard the allocation against degenerate hints: a zero-length array
        // could never be enlarged by the multiplicative growth strategy.
        int capacity = Math.max(pInitialCapacity, MINIMUM_CAPACITY);

        // Allocate the backing storage as Object[] because generic array
        // creation is impossible under type erasure.
        elements = new Object[capacity];

        // A freshly allocated heap holds no elements, so the next insertion
        // writes to index zero, which is the root of the implicit tree.
        size = 0;
    }
}
