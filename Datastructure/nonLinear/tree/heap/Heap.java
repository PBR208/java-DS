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
     * Number of children each node of the implicit tree has.
     *
     * This is the branching factor of the binary heap and it appears in the
     * index formulas that relate a node to its parent and children. It is
     * deliberately a separate constant from GROWTH_FACTOR even though both
     * currently hold the value two: the two express unrelated facts, one about
     * the shape of the tree and one about the array resizing policy, and sharing
     * a single constant would silently corrupt the index arithmetic if the
     * resizing policy were ever retuned.
     */
    private static final int CHILDREN_PER_NODE = 2;

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

    /**
     * Returns the array index of the parent of the node at the specified index.
     *
     * Detailed explanation of:
     * - Purpose: Encodes one half of the implicit tree structure. Because the
     *   heap is a complete binary tree laid out in level order, the parent of a
     *   node can be computed arithmetically instead of being stored, which is
     *   the entire reason this structure needs no child or parent references.
     * - Business context: Concentrating the arithmetic in named helpers rather
     *   than inlining the expressions is what keeps the sift procedures
     *   readable; an off-by-one in these formulas is otherwise extremely hard to
     *   spot, since a subtly wrong index still addresses a valid array slot.
     * - Processing steps: Subtracts one from the index before halving, which is
     *   what accounts for the root living at index zero rather than at index
     *   one. Integer division then discards the remainder, so both children of a
     *   node map back to the same parent.
     * - Assumptions: Assumes the supplied index is greater than zero. The root
     *   has no parent, and the formula would yield a meaningless result for it,
     *   so every call site guards the root case explicitly.
     * - Side effects: None; this is a pure computation.
     *
     * Time complexity: O(1); one subtraction and one shift-equivalent division.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pIndex
     * Index of the node whose parent is wanted. Must be greater than zero and
     * less than the current size.
     *
     * @return
     * Index of the parent node, always strictly less than pIndex, which is what
     * guarantees the upward sift terminates.
     */
    private int parentIndex(int pIndex) {
        // The minus one compensates for the zero-based root; without it the two
        // children of a node would map to different parents.
        return (pIndex - 1) / CHILDREN_PER_NODE;
    }

    /**
     * Returns the array index of the left child of the node at the specified
     * index.
     *
     * Detailed explanation of:
     * - Purpose: Encodes the downward half of the implicit tree structure, used
     *   by the downward sift to decide where a demoted element should travel.
     * - Business context: See parentIndex; the same reasoning about naming the
     *   arithmetic applies.
     * - Processing steps: Doubles the index and adds one, which is the level
     *   order position of the left child when the root sits at index zero.
     * - Assumptions: Assumes the caller checks the returned index against the
     *   current size before using it. The formula is defined for any index, but
     *   a result at or beyond the size denotes a child that does not exist.
     * - Side effects: None; this is a pure computation.
     *
     * Time complexity: O(1); one multiplication and one addition.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pIndex
     * Index of the node whose left child is wanted. Must be non-negative.
     *
     * @return
     * Index at which the left child would reside. May be at or beyond the
     * current size, which the caller must interpret as "no such child".
     */
    private int leftChildIndex(int pIndex) {
        return CHILDREN_PER_NODE * pIndex + 1;
    }

    /**
     * Returns the array index of the right child of the node at the specified
     * index.
     *
     * Detailed explanation of:
     * - Purpose: Completes the downward half of the implicit tree structure.
     * - Business context: See parentIndex.
     * - Processing steps: Doubles the index and adds two, placing the right
     *   child immediately after the left one in level order.
     * - Assumptions: As for leftChildIndex, the caller must check the result
     *   against the current size before treating it as an existing node.
     * - Side effects: None; this is a pure computation.
     *
     * Time complexity: O(1); one multiplication and one addition.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pIndex
     * Index of the node whose right child is wanted. Must be non-negative.
     *
     * @return
     * Index at which the right child would reside. May be at or beyond the
     * current size, which the caller must interpret as "no such child".
     */
    private int rightChildIndex(int pIndex) {
        return CHILDREN_PER_NODE * pIndex + 2;
    }

    /**
     * Reads the element stored at the specified index.
     *
     * Detailed explanation of:
     * - Purpose: Confines the unchecked cast from the erased backing array to a
     *   single accessor, so that the type-safety argument has to be made and
     *   audited in exactly one place rather than at every read site.
     * - Business context: The heap performs a great many reads during sifting,
     *   and scattering casts across those call sites would obscure both the
     *   algorithm and the one assumption that makes the casts sound.
     * - Processing steps: Reads the slot and casts it to the content type.
     * - Assumptions: Assumes every occupied slot below the current size holds a
     *   ContentType instance. This holds because insert is the only method that
     *   writes payloads into the array and it accepts ContentType exclusively;
     *   the bulk constructor writes elements taken from a ContentType array.
     * - Side effects: None; this method only reads.
     *
     * Time complexity: O(1); a single indexed read.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pIndex
     * Index to read. Must be non-negative and less than the current size; slots
     * at or beyond the size are cleared and would yield null.
     *
     * @return
     * The element stored at that index, never null for an index below the
     * current size.
     */
    @SuppressWarnings("unchecked")
    private ContentType elementAt(int pIndex) {
        // The cast is sound because insert and the bulk constructor are the only
        // writers of payload slots and both accept ContentType exclusively.
        return (ContentType) elements[pIndex];
    }

    /**
     * Determines whether one element takes precedence over another under this
     * heap's ordering direction.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single point at which the minimum-first and
     *   maximum-first behaviours diverge. Every other line of this class is
     *   written in terms of "takes precedence" and is therefore direction
     *   agnostic, which is what allows one implementation to serve both
     *   variants.
     * - Business context: Isolating the direction here means a reader verifying
     *   the sift procedures never has to reason about two orderings at once, and
     *   a future third ordering could be added without touching the algorithms.
     * - Processing steps: Consults the direction recorded at construction and
     *   delegates to the corresponding ComparableContent comparison.
     * - Assumptions: Assumes the comparison methods are consistent with one
     *   another and transitive. An inconsistent ordering does not corrupt the
     *   array, but it makes the resulting element positions arbitrary, and the
     *   root would then no longer be the extreme element.
     * - Side effects: None; this method only reads.
     *
     * Time complexity: O(1) plus the cost of the content type's own comparison.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pCandidate
     * The element being tested for precedence. Must not be null.
     *
     * @param pReference
     * The element it is being tested against. Must not be null.
     *
     * @return
     * True when pCandidate must sit above pReference in the heap. Elements that
     * compare equal yield false, which is deliberate: equal elements may be
     * stored in either order, and reporting no precedence avoids the pointless
     * swaps that an inclusive comparison would perform.
     */
    private boolean hasPriority(ContentType pCandidate, ContentType pReference) {
        if (order == Order.MIN) {
            // A minimum-heap promotes the smaller element towards the root.
            return pCandidate.isLess(pReference);
        }

        // A maximum-heap promotes the larger element towards the root.
        return pCandidate.isGreater(pReference);
    }

    /**
     * Exchanges the elements stored at two indices.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single mutation primitive used by both sift
     *   procedures, so that the movement of elements through the array happens
     *   in one auditable place.
     * - Business context: A heap repairs itself entirely by exchanging a node
     *   with a neighbour along one root-to-leaf path; naming that step makes the
     *   sift procedures read as the walks they are.
     * - Processing steps: Holds one slot in a temporary reference while the
     *   other is copied across, then writes the held value back.
     * - Assumptions: Assumes both indices are within the occupied region. The
     *   two indices may be equal, in which case the exchange is a harmless
     *   no-operation.
     * - Side effects: Mutates two slots of the backing array.
     *
     * Time complexity: O(1); three reference assignments.
     * Space complexity: O(1); one temporary reference.
     *
     * @param pFirstIndex
     * Index of the first slot. Must be non-negative and less than the size.
     *
     * @param pSecondIndex
     * Index of the second slot. Must be non-negative and less than the size.
     */
    private void swap(int pFirstIndex, int pSecondIndex) {
        // Hold one value so that neither is lost while the slots are rewritten.
        Object held = elements[pFirstIndex];
        elements[pFirstIndex] = elements[pSecondIndex];
        elements[pSecondIndex] = held;
    }

    /**
     * Determines whether this heap currently contains no elements.
     *
     * Detailed explanation of:
     * - Purpose: Provides the guard clause callers are expected to use before
     *   inspecting or removing the root, since both of those operations are
     *   defined to fail silently on an empty heap rather than to raise an
     *   exception.
     * - Business context: This is also the natural loop condition for draining a
     *   heap, which is how it is consumed by heap sort and by any algorithm that
     *   processes a frontier until it is exhausted.
     * - Processing steps: Compares the size counter against zero.
     * - Assumptions: Assumes the size counter is kept in step with the occupied
     *   region of the backing array, which every mutating operation preserves.
     * - Side effects: None; this method does not modify internal state.
     *
     * Time complexity: O(1); a single counter comparison.
     * Space complexity: O(1); no auxiliary storage.
     *
     * @return
     * True when no element is stored, false as soon as at least one element has
     * been inserted and not yet removed.
     */
    public boolean isEmpty() {
        // Capacity may remain after removals, so only the logical size is a
        // valid emptiness criterion.
        return size == 0;
    }

    /**
     * Returns the number of elements currently stored in this heap.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the element count without a traversal, and provides the
     *   quantity against which this structure's logarithmic bounds are stated.
     * - Business context: Callers commonly use the count to report the size of a
     *   pending frontier or backlog, or to size an output buffer before draining
     *   the heap into a sorted sequence.
     * - Processing steps: Returns the incrementally maintained counter.
     * - Assumptions: Assumes insert and remove adjust the counter exactly on the
     *   paths that genuinely add or discard an element.
     * - Side effects: None; this method does not modify internal state.
     *
     * Time complexity: O(1); the count is maintained incrementally.
     * Space complexity: O(1); no auxiliary storage.
     *
     * @return
     * Element count, zero for an empty heap and never negative. Duplicates are
     * counted individually, since a heap stores them as distinct elements.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the number of elements the backing array can hold before the next
     * growth step.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the physical storage size as diagnostic information,
     *   parallel to the getHeight and getBlackHeight accessors of the trees in
     *   this package, so that the growth and shrink behaviour can be observed
     *   rather than merely trusted.
     * - Business context: Observing how capacity evolves is a central learning
     *   goal of this reference implementation; it is not part of the heap
     *   contract, and callers must not treat the value as an upper bound on how
     *   many elements they may insert.
     * - Processing steps: Returns the length of the backing array.
     * - Assumptions: None.
     * - Side effects: None; this method does not modify internal state.
     *
     * Time complexity: O(1); a direct array length read.
     * Space complexity: O(1); no auxiliary storage.
     *
     * @return
     * Current length of the backing array, always at least the internal minimum
     * capacity and always greater than or equal to the current size.
     */
    public int capacity() {
        return elements.length;
    }

    /**
     * Returns the highest-precedence element without removing it.
     *
     * Detailed explanation of:
     * - Purpose: Provides the constant-time access to the extreme element that
     *   is the entire reason for choosing a heap over a sorted structure.
     * - Business context: This is the read half of the read-then-discard pattern
     *   the API imposes: because remove returns nothing, consumers inspect the
     *   root here and only afterwards discard it. It is also the operation an
     *   algorithm uses to decide whether the extreme element is worth processing
     *   at all, for example when comparing the smallest pending distance against
     *   a bound before committing to the work.
     * - Processing steps: Reports null for an empty heap, otherwise reads index
     *   zero, which the heap property guarantees holds the extreme element.
     * - Assumptions: Assumes the heap property currently holds, which every
     *   mutating operation re-establishes before returning.
     * - Side effects: None; the heap is left completely unmodified, so repeated
     *   invocations yield the same element until it is removed.
     *
     * Time complexity: O(1); a single indexed read, with no sifting involved.
     * Space complexity: O(1); no auxiliary storage.
     *
     * @return
     * The smallest stored element for a minimum-heap or the largest for a
     * maximum-heap, or null when the heap is empty. Null is unambiguous as an
     * empty-heap marker because insert refuses to store null elements. When
     * several elements compare equal, which of them is returned is unspecified,
     * since the heap property does not order equal elements against each other.
     */
    public ContentType peek() {
        // An empty heap has no extreme element; report that through null rather
        // than reading a cleared slot.
        if (isEmpty()) {
            return null;
        }

        // The root of the implicit tree lives at index zero, and the heap
        // property guarantees it takes precedence over everything below it.
        return elementAt(0);
    }

    /**
     * Inserts an element into the heap, restoring the heap property afterwards.
     *
     * Detailed explanation of:
     * - Purpose: Adds a value and repositions it so that every node again takes
     *   precedence over its children, which is what keeps the extreme element at
     *   the root for the next inspection.
     * - Business context: Serves as the primary entry point for growing the
     *   heap. Unlike an insertion into a search tree, no position has to be
     *   located first: the element is appended at the only place a complete tree
     *   can grow, and the repair then moves it to where it belongs.
     * - Processing steps:
     *   1. Reject null, consistent with the rest of this library.
     *   2. Grow the backing array when it is exhausted.
     *   3. Append at the first free slot, which is the next position in level
     *      order and therefore the only one that keeps the tree complete.
     *   4. Sift the new element upwards until its parent takes precedence over
     *      it or it reaches the root.
     * - Assumptions: Assumes the comparison methods of the content type are
     *   consistent and transitive; an inconsistent ordering leaves elements in
     *   arbitrary positions rather than corrupting the array.
     * - Side effects: Mutates the backing array, increments the element count,
     *   and may replace the backing array entirely when growth is required.
     *
     * Time complexity: O(log n) worst case, bounded by the height of a complete
     * binary tree over n elements, which is exactly floor(log2(n)). The
     * occasional growth copy is O(n) but amortises to O(1) through doubling, so
     * the sift remains the dominant cost.
     * Space complexity: O(1) amortised; a growth step temporarily holds both the
     * old and the new array, making peak usage during that step O(n).
     *
     * @param pContent
     * Element to insert. May be any ContentType instance; passing null is
     * tolerated and silently ignored, so callers that must distinguish "stored
     * nothing" from "stored a value" have to check for null before calling.
     * Duplicates are permitted and stored as distinct elements, which differs
     * deliberately from the search trees in this package: a heap imposes no
     * uniqueness constraint, and a priority queue routinely holds many items of
     * equal priority.
     */
    public void insert(ContentType pContent) {
        // Reject null early: storing it would break the contract of peek, which
        // reports an empty heap by returning null, and would additionally fail
        // the first comparison the sift performs.
        if (pContent == null) {
            return;
        }

        // Grow before writing whenever the array is full, because the append
        // index below is the current size and must stay inside the bounds.
        if (size == elements.length) {
            resize(elements.length * GROWTH_FACTOR);
        }

        /*
         * Append at the end of the level-order sequence. This is the only
         * position at which a complete binary tree can gain a node without
         * leaving a gap, which is precisely the property that lets the tree be
         * addressed arithmetically instead of through stored references.
         */
        elements[size] = pContent;
        size++;

        // The appended element may take precedence over its ancestors, so move
        // it upwards until the heap property holds again.
        siftUp(size - 1);
    }

    /**
     * Moves the element at the specified index towards the root until its parent
     * takes precedence over it.
     *
     * Detailed explanation of:
     * - Purpose: Repairs the single class of violation that an append can
     *   introduce. An element added at a leaf can only be out of place with
     *   respect to its ancestors, never with respect to descendants it does not
     *   have, so the repair travels in one direction only.
     * - Business context: This procedure and its downward counterpart are the
     *   entire balancing machinery of a heap. Their existence in place of the
     *   rotations used by the search trees is the reason a heap is so much
     *   cheaper to maintain, and equally the reason it cannot answer arbitrary
     *   lookups.
     * - Processing steps: Repeatedly compares the element against its parent and
     *   exchanges the two when it takes precedence, following the path from the
     *   starting index towards the root. The walk stops as soon as the parent
     *   wins, because at that point every ancestor above also takes precedence
     *   by transitivity, so no further comparison could fail.
     * - Assumptions: Assumes the heap property holds everywhere except possibly
     *   between the starting element and its ancestors.
     * - Side effects: Exchanges elements along one root-to-node path of the
     *   backing array.
     *
     * Time complexity: O(log n) worst case, when the element travels from a leaf
     * to the root; O(1) when its parent already takes precedence.
     * Space complexity: O(1); the walk is iterative.
     *
     * @param pStartIndex
     * Index of the element to move upwards. Must be non-negative and less than
     * the current size.
     */
    private void siftUp(int pStartIndex) {
        // Tracks the travelling element's current position.
        int currentIndex = pStartIndex;

        // The root has no parent, so reaching index zero always terminates the
        // walk regardless of any comparison.
        while (currentIndex > 0) {
            int parent = parentIndex(currentIndex);

            /*
             * Stop as soon as the parent takes precedence. Transitivity of the
             * ordering guarantees that every ancestor further up also takes
             * precedence at this point, so continuing could not find another
             * violation.
             */
            if (!hasPriority(elementAt(currentIndex), elementAt(parent))) {
                return;
            }

            // The element outranks its parent, so the two exchange places and
            // the walk continues from the parent's former position.
            swap(currentIndex, parent);
            currentIndex = parent;
        }
    }

    /**
     * Replaces the backing array with one of the requested capacity, preserving
     * every stored element and its position.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single point at which storage is reallocated, so
     *   that both the growth path of insert and the shrink path of remove share
     *   one implementation of the capacity invariants.
     * - Business context: Resizing is orthogonal to the heap property. Because
     *   the tree structure is encoded in the indices rather than in references,
     *   copying the occupied prefix to a larger or smaller array preserves the
     *   entire structure automatically; no element changes its position and no
     *   sift is required afterwards.
     * - Processing steps: Raises the request to the internal minimum, allocates
     *   the replacement array, copies the occupied prefix, and publishes the new
     *   reference.
     * - Assumptions: Assumes the requested capacity is large enough to hold the
     *   current element count, which every call site guarantees.
     * - Side effects: Allocates a new array and abandons the previous one, which
     *   becomes eligible for garbage collection.
     *
     * Time complexity: O(n) in the current element count, dominated by the bulk
     * copy.
     * Space complexity: O(pNewCapacity), with both arrays alive simultaneously
     * until the new reference is published.
     *
     * @param pNewCapacity
     * Desired length of the new backing array. Values below the internal minimum
     * capacity are raised to it so that the array can always be doubled again.
     */
    private void resize(int pNewCapacity) {
        // Never fall below the minimum, otherwise a zero-length array would
        // leave the multiplicative growth strategy unable to produce space.
        int targetCapacity = Math.max(pNewCapacity, MINIMUM_CAPACITY);

        // Allocate the replacement storage; Object[] is required for the same
        // erasure reason that applies to the original allocation.
        Object[] resizedElements = new Object[targetCapacity];

        // Copy the occupied prefix. Positions are preserved exactly, which is
        // what leaves the implicit tree structure intact across the move.
        System.arraycopy(elements, 0, resizedElements, 0, size);

        // Publish the new storage.
        elements = resizedElements;
    }

    /**
     * Removes the highest-precedence element from the heap, restoring the heap
     * property afterwards.
     *
     * Detailed explanation of:
     * - Purpose: Discards the root and repairs the structure so that the next
     *   most extreme element takes its place, which is what allows a heap to be
     *   drained into a sorted sequence one element at a time.
     * - Business context: Calling this on an empty heap performs no action
     *   rather than raising an underflow exception, matching the defensive
     *   contract used throughout this library. Callers that need the element
     *   must read it through peek beforehand, because this method intentionally
     *   does not return the discarded value.
     * - Processing steps:
     *   1. Do nothing when the heap is empty.
     *   2. Move the last element in level order into the root position. This is
     *      the only element whose removal from its own position keeps the tree
     *      complete, which is why the root is overwritten rather than its
     *      children being promoted.
     *   3. Clear the vacated tail slot and shrink the element count.
     *   4. Sift the relocated element downwards, since it came from a leaf and
     *      will almost certainly be outranked by its new children.
     *   5. Shrink the backing array once it is mostly empty.
     * - Assumptions: Assumes the heap property holds on entry, which every other
     *   mutating operation guarantees.
     * - Side effects: Mutates the backing array, decrements the element count,
     *   and may replace the backing array entirely when shrinking.
     *
     * Time complexity: O(log n) worst case, dominated by the downward sift,
     * which may travel the full height of the tree. The occasional shrink copy
     * is O(n) but amortises to O(1) through the quarter-capacity threshold.
     * Space complexity: O(1) amortised; a shrink step briefly holds both arrays,
     * making peak usage during that step O(n).
     */
    public void remove() {
        // Underflow is treated as a no-op so that callers can drain the heap in
        // a loop without wrapping every call in an emptiness check.
        if (isEmpty()) {
            return;
        }

        // Index of the last element in level order, which is the one that will
        // take over the root position.
        int lastIndex = size - 1;

        /*
         * Overwrite the root with the last element rather than promoting one of
         * the root's children. Promoting a child would leave a gap in the middle
         * of the level-order sequence and destroy the completeness that the
         * index arithmetic depends on; moving the last element preserves it.
         */
        elements[0] = elements[lastIndex];

        // Clear the vacated tail slot so that the array does not retain a
        // reference to an element that is no longer part of the heap, which
        // would otherwise prevent its collection.
        elements[lastIndex] = null;

        // Account for the removal before sifting, so that the sift sees the
        // correct boundary when testing whether a child exists.
        size--;

        // The relocated element came from a leaf and is very unlikely to
        // outrank its new children, so push it back down to its proper level.
        siftDown(0);

        // Shrink only once the array is mostly empty; reacting at half capacity
        // would let an alternating insert and remove sequence resize on every
        // call.
        if (size > 0 && size <= elements.length / SHRINK_THRESHOLD_DIVISOR) {
            resize(elements.length / GROWTH_FACTOR);
        }
    }

    /**
     * Moves the element at the specified index away from the root until it takes
     * precedence over both of its children.
     *
     * Detailed explanation of:
     * - Purpose: Repairs the violation introduced by placing an arbitrary
     *   element at an internal position, which happens when the last element is
     *   moved to the root during removal and when the bulk construction sifts
     *   each internal node in turn.
     * - Business context: This is the counterpart of siftUp and the more
     *   intricate of the two, because a node has two children and the repair
     *   must choose between them rather than following a single parent link.
     * - Processing steps: At each level, identify which of the current node and
     *   its existing children takes precedence over the other two. Stop when
     *   that is the current node, since the heap property then holds locally and
     *   therefore, by transitivity, throughout the subtree below. Otherwise
     *   exchange with the winning child and continue from that child's position.
     *   Choosing the stronger of the two children is essential: exchanging with
     *   the weaker one would place it above its sibling and create a fresh
     *   violation rather than repairing the existing one.
     * - Assumptions: Assumes the heap property holds everywhere except possibly
     *   between the starting element and its descendants. Assumes the size
     *   counter already reflects the current element population, since child
     *   existence is decided by comparing indices against it.
     * - Side effects: Exchanges elements along one node-to-leaf path of the
     *   backing array.
     *
     * Time complexity: O(log n) worst case, when the element travels from the
     * root to a leaf; O(1) when it already outranks both children.
     * Space complexity: O(1); the walk is iterative.
     *
     * @param pStartIndex
     * Index of the element to move downwards. Must be non-negative and less than
     * the current size.
     */
    private void siftDown(int pStartIndex) {
        // Tracks the travelling element's current position.
        int currentIndex = pStartIndex;

        while (true) {
            int left = leftChildIndex(currentIndex);
            int right = rightChildIndex(currentIndex);

            // Index of whichever of the three nodes examined at this level takes
            // precedence; it starts as the current node and is displaced only by
            // a child that genuinely outranks it.
            int strongestIndex = currentIndex;

            // Compare against the left child when it exists. An index at or
            // beyond the size denotes a position outside the occupied region and
            // therefore a child that is not present.
            if (left < size && hasPriority(elementAt(left), elementAt(strongestIndex))) {
                strongestIndex = left;
            }

            // Compare against the right child on the same terms. Testing it
            // against the running winner rather than against the current node is
            // what selects the stronger of the two children.
            if (right < size && hasPriority(elementAt(right), elementAt(strongestIndex))) {
                strongestIndex = right;
            }

            /*
             * The current node outranks both of its children, so the heap
             * property holds at this level. Everything below was already valid,
             * so the repair is complete and the walk stops.
             */
            if (strongestIndex == currentIndex) {
                return;
            }

            // Exchange with the stronger child and continue the descent from
            // that child's former position.
            swap(currentIndex, strongestIndex);
            currentIndex = strongestIndex;
        }
    }

    /**
     * Constructs a heap containing every non-null element of the supplied array,
     * built in linear rather than logarithmic-per-element time.
     *
     * Detailed explanation of:
     * - Purpose: Provides the bulk construction path for the common case in
     *   which the entire population is known up front, such as when seeding a
     *   graph algorithm with every vertex or when heap sort takes ownership of
     *   an unsorted array.
     * - Business context: The distinction between this constructor and a loop of
     *   individual insertions is one of the standard results about heaps and the
     *   main reason this path is worth providing. Inserting n elements one at a
     *   time costs O(n log n), because each insertion may sift a leaf all the way
     *   to the root. Building bottom-up costs only O(n): the sift performed at
     *   each node is bounded by the height of that node's subtree, and a
     *   complete tree has very few tall nodes and very many short ones. Summing
     *   those heights yields a total that is linear in n rather than n log n.
     * - Processing steps:
     *   1. Validate the ordering direction and allocate storage large enough for
     *      the supplied population.
     *   2. Copy across the non-null elements, silently skipping nulls so that a
     *      partially populated array is accepted on the same terms that insert
     *      accepts a null argument.
     *   3. Sift down every internal node, starting from the last one and moving
     *      towards the root. Processing in that order guarantees that when a
     *      node is sifted, both of its subtrees are already valid heaps, which is
     *      the precondition siftDown requires.
     *   4. Leaf nodes are skipped entirely, since a subtree consisting of a
     *      single node is trivially a valid heap. That is why the loop starts at
     *      the parent of the last element rather than at the last element.
     * - Assumptions: Assumes the supplied array is not modified concurrently.
     *   The array itself is not retained: its contents are copied, so later
     *   changes by the caller do not affect the heap.
     * - Side effects: Allocates a backing array sized to the supplied population
     *   and rearranges the copied elements into heap order.
     *
     * Time complexity: O(n) in the number of supplied elements, as argued above.
     * This is strictly better than the O(n log n) of repeated insertion and is
     * the reason to prefer this constructor whenever the population is known.
     * Space complexity: O(n); one backing array sized to the population.
     *
     * @param pOrder
     * Direction of the ordering to maintain. Must not be null.
     *
     * @param pInitialElements
     * Elements to populate the heap with. May be null or empty, both of which
     * produce an empty heap rather than an error. Individual null entries are
     * skipped, so the resulting size may be smaller than the array length.
     * Duplicate values are permitted and are stored as distinct elements.
     *
     * @throws IllegalArgumentException
     * Thrown when pOrder is null, for the same reason as in the capacity-aware
     * constructor: no comparison this heap performs would be meaningful without
     * an ordering direction.
     */
    public Heap(Order pOrder, ContentType[] pInitialElements) {
        // Reuse the validation and allocation rules of the primary constructor,
        // sizing the storage for the supplied population where one was given.
        this(pOrder, pInitialElements == null
                ? DEFAULT_INITIAL_CAPACITY
                : pInitialElements.length);

        // A missing or empty array simply yields the empty heap that the
        // delegated constructor already produced.
        if (pInitialElements == null) {
            return;
        }

        // Copy the supplied elements into the backing array, skipping nulls on
        // the same terms that insert ignores a null argument. The elements land
        // in arbitrary order; establishing the heap property is the job of the
        // bottom-up pass below.
        for (ContentType element : pInitialElements) {
            if (element != null) {
                elements[size] = element;
                size++;
            }
        }

        // Rearrange the copied elements into heap order in linear time.
        buildHeap();
    }

    /**
     * Establishes the heap property across the entire backing array by sifting
     * every internal node downwards, from the last one towards the root.
     *
     * Detailed explanation of:
     * - Purpose: Converts an arbitrarily ordered array into a valid heap in
     *   linear time, which is the operation that makes bulk construction cheaper
     *   than repeated insertion.
     * - Business context: This is the classic build-heap procedure, and the order
     *   in which it visits nodes is the whole subtlety. Working from the end
     *   towards the root means that by the time any node is processed, both of
     *   its subtrees are already valid heaps; that is exactly the precondition
     *   siftDown needs in order to repair a violation in a single downward walk.
     *   Running the same loop in the opposite direction would not produce a valid
     *   heap, because siftDown would be operating on subtrees that are themselves
     *   still unordered.
     * - Processing steps: Computes the index of the last internal node, which is
     *   the parent of the final element, and sifts every node from there down to
     *   index zero.
     * - Assumptions: Assumes the size counter already reflects the populated
     *   region of the backing array.
     * - Side effects: Rearranges the occupied region of the backing array.
     *
     * Time complexity: O(n). Although siftDown is O(log n) in the worst case, the
     * bound that applies here is the height of each individual node's subtree,
     * and the sum of those heights over a complete tree is linear in n.
     * Space complexity: O(1); the procedure works in place.
     */
    private void buildHeap() {
        // An empty or single-element array is already a valid heap, and the
        // index computed below would be meaningless for it.
        if (size < CHILDREN_PER_NODE) {
            return;
        }

        /*
         * The last internal node is the parent of the final element; every index
         * beyond it addresses a leaf. Leaves need no work, because a single-node
         * subtree trivially satisfies the heap property, and skipping them is
         * what removes roughly half of the nodes from this loop.
         */
        int lastInternalIndex = parentIndex(size - 1);

        // Descending order guarantees that both subtrees of the node being
        // processed have already been made valid heaps.
        for (int index = lastInternalIndex; index >= 0; index--) {
            siftDown(index);
        }
    }
}
