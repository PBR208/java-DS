package nonLinear.tree.segmentTree;

/**
 * Purpose:
 * Implements a segment tree, a static-shape binary tree in which every node
 * caches the aggregate of one contiguous range of an underlying array, the root
 * covering the whole array and each leaf covering a single position. The problem
 * it solves is the one that a plain array and a table of prefix aggregates each
 * solve only halfway: an array answers "replace this element" instantly but has
 * to scan for "what is the sum, or the minimum, over this range", while a prefix
 * table answers the range question instantly but has to be rebuilt from the
 * point of change after every write. A segment tree refuses that trade and keeps
 * both operations logarithmic, which is what makes it the standard answer
 * whenever range queries and element updates are interleaved. It is also more
 * general than a prefix table in kind and not merely in cost, because
 * subtracting one prefix from another requires an invertible operation, whereas
 * merging two cached ranges requires only an associative one, and minimum,
 * maximum and greatest common divisor are associative without being invertible.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Array-backed segment tree over a fixed number of positions, generic in the
 * stored content type and in the aggregate maintained over ranges of it.
 *
 * Responsibility: Encapsulates the construction of the cached aggregates from an
 * initial array, the decomposition of an arbitrary query range into the fewest
 * cached ranges that exactly cover it, and the repair of the cache along one
 * root-to-leaf path after a position is overwritten. It maintains the invariant
 * that every node holds the merge of its two children, and therefore the
 * aggregate of precisely the range it covers.
 *
 * Scope: Used within the nonLinear.tree package wherever range aggregates over a
 * changing sequence are required. The aggregate itself is deliberately not fixed
 * here: it is supplied by the SegmentCombiner handed in at construction, so
 * sums, minima, maxima and order-sensitive merges are all served by this one
 * class.
 *
 * Dependencies: Depends on the SegmentCombiner interface of this package for the
 * merge semantics, and otherwise only on a plain Object array used as the node
 * store. It deliberately does not implement the ComparableContent-based contract
 * used by the search trees here, because it never compares two stored values
 * against one another and derives its shape from array positions alone.
 *
 * Thread-safety: This class is not thread-safe. An update rewrites the
 * aggregates on one root-to-leaf path from the bottom upwards, so a concurrent
 * query may read a node that has already been refreshed together with an
 * ancestor that has not, and return an aggregate that never existed at any point
 * in time. External synchronization is required whenever instances are shared
 * across threads; concurrent queries alone are safe as long as no update runs.
 *
 * Lifecycle: A SegmentTree is built once from an initial array and keeps that
 * number of positions for its entire lifetime. Values change freely through
 * update, but the tree neither grows nor shrinks, because its node layout is
 * derived from the position count and a change to it would invalidate every
 * cached range at once. Callers needing a different length build a new tree.
 *
 * Architectural role: Serves as the range-aggregation counterpart to the search
 * trees of this package, and as the reference implementation showing that a tree
 * need not organise data by value at all: this one organises it by position and
 * stores derived answers rather than the elements themselves.
 *
 * @param <ContentType>
 * The type of the stored elements and of the aggregates derived from them. No
 * ordering or comparison capability is required of this type; every operation on
 * it is delegated to the supplied SegmentCombiner.
 */
public class SegmentTree<ContentType> {

    /**
     * Number of node slots reserved per stored position.
     *
     * A segment tree over n positions holds fewer than 2n nodes, yet those nodes
     * are addressed arithmetically as in a heap, and that addressing leaves gaps
     * whenever n is not a power of two: the recursion splits ranges of odd width
     * unevenly, so one subtree may reach a level deeper than its sibling and the
     * index space has to accommodate the deeper one. Rounding the position count
     * up to the next power of two and allowing two node slots per position gives
     * a bound of four slots per position, which is the smallest simple bound that
     * is safe for every n. Computing the exact power of two would save memory at
     * the cost of an allocation rule that no longer reads off the array length,
     * which is not a worthwhile trade in a reference implementation.
     */
    private static final int NODES_PER_POSITION = 4;

    /**
     * Index of the root node inside the node array.
     *
     * The root covers the entire range of positions and is the entry point of
     * every descent. Fixing it at zero rather than at one keeps the child index
     * formulas identical to those used by the Heap in this package, at the price
     * of the slightly less elegant arithmetic those formulas require.
     */
    private static final int ROOT_INDEX = 0;

    /**
     * The merge operation defining which aggregate this tree maintains.
     *
     * Held final because every value cached in the node array was produced by
     * this combiner: exchanging it later would leave the tree full of aggregates
     * that no longer match the question it claims to answer.
     */
    private final SegmentCombiner<ContentType> combiner;

    /**
     * Implicit binary tree of cached range aggregates, in heap layout.
     *
     * Each occupied slot holds the aggregate of one contiguous range of
     * positions. The range a slot stands for is never stored, because it follows
     * from the path taken to reach the slot and is therefore recomputed during
     * every descent instead of being kept in memory. Slots left unused by the
     * generous NODES_PER_POSITION bound stay null and are never read, since no
     * descent reaches them. The array is declared as Object[] rather than
     * ContentType[] because Java forbids the creation of a generic array; every
     * read passes through nodeAt, which confines the resulting unchecked cast to
     * a single place.
     */
    private final Object[] nodes;

    /**
     * Number of positions the tree covers, that is, the length of the array it
     * was built from.
     *
     * Fixed at construction, since the node layout is derived from it. It bounds
     * every valid index and defines the range covered by the root.
     */
    private final int size;

    /**
     * Constructs a segment tree over the supplied elements, precomputing the
     * aggregate of every range the tree will ever need.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the fixed node layout for the given number of
     *   positions and fills it with the aggregates that later queries are
     *   assembled from.
     * - Business context: Building is the one genuinely linear operation of this
     *   structure and the price paid up front for logarithmic queries afterwards.
     *   Building bottom-up in a single recursion keeps that price at O(n) rather
     *   than the O(n log n) that inserting n elements individually would cost.
     * - Processing steps:
     *   1. Reject a null element array or a null combiner, either of which would
     *      leave the tree unable to answer any query at all.
     *   2. Reject a null element, because the combiner is contractually promised
     *      non-null arguments and because null marks an absent contribution
     *      inside the query descent.
     *   3. Allocate the node array from the position count.
     *   4. Recursively fill it, unless the tree covers no positions at all.
     * - Assumptions: Assumes the supplied combiner is associative, as its own
     *   contract demands; a non-associative merge produces a tree whose answers
     *   depend on the shape of the queried range rather than on its contents.
     * - Side effects: Allocates the node array and populates it. The element
     *   array is read but never retained, so later changes the caller makes to it
     *   do not reach the tree; update is the only supported way to change a
     *   stored value.
     *
     * @param pElements
     * The initial values, one per position, in the order the tree indexes them.
     * Must not be null and must not contain null. May be empty, which yields a
     * tree covering no positions that answers every query with null; the empty
     * case is accepted rather than rejected so that callers deriving the array
     * from a filtered data set need no special case.
     *
     * @param pCombiner
     * The merge operation defining the maintained aggregate. Must not be null and
     * must satisfy the associativity requirement stated by SegmentCombiner.
     *
     * @throws IllegalArgumentException
     * Thrown when pElements is null, when pCombiner is null, or when any element
     * is null. These are reported rather than tolerated because each of them
     * leaves the tree permanently incapable of serving its purpose, and because a
     * tree is built exactly once, so failing loudly at that single point is far
     * cheaper than discovering the defect during a later query.
     */
    public SegmentTree(ContentType[] pElements, SegmentCombiner<ContentType> pCombiner) {
        // Without elements there is nothing to derive a layout from, and the
        // difference between "no array" and "empty array" is meaningful enough to
        // report rather than to silently equate.
        if (pElements == null) {
            throw new IllegalArgumentException("The element array must not be null.");
        }

        // Without a combiner the tree could store the leaves but could never
        // aggregate them, which is the entire point of the structure.
        if (pCombiner == null) {
            throw new IllegalArgumentException("The combiner must not be null.");
        }

        // Validate the contents before allocating anything, so that a rejected
        // argument leaves no half-built tree behind.
        for (int index = 0; index < pElements.length; index++) {
            /*
             * A null element would be handed to the combiner during the build and
             * would additionally be indistinguishable from the marker that the
             * query descent uses for a range contributing nothing. Both make the
             * tree unsound rather than merely inconvenient.
             */
            if (pElements[index] == null) {
                throw new IllegalArgumentException("The element array must not contain null values.");
            }
        }

        this.combiner = pCombiner;
        this.size = pElements.length;

        // Reserve the safe upper bound of node slots for this position count; a
        // tree over no positions correctly reserves none.
        this.nodes = new Object[size * NODES_PER_POSITION];

        /*
         * Fill the tree from the root downwards, which lets the recursion split
         * ranges on the way in and merge the resulting aggregates on the way out.
         * The empty case is skipped because the root would have to cover the
         * range from zero to minus one, which no descent can interpret.
         */
        if (size > 0) {
            build(pElements, ROOT_INDEX, 0, size - 1);
        }
    }

    /**
     * Computes the node index of the left child of the specified node.
     *
     * Detailed explanation of:
     * - Purpose: Translates a parent position in the implicit tree into the
     *   position of the child covering the lower half of its range.
     * - Business context: The tree stores no child references at all. Deriving
     *   them arithmetically is what allows the entire structure to live inside
     *   one contiguous array, which keeps traversals cache-friendly and avoids
     *   the per-node object overhead a linked representation would add.
     * - Processing steps: Applies the standard heap-layout formula for a
     *   zero-based root.
     * - Assumptions: Assumes the caller descends only into a node whose range
     *   holds more than one position; leaves have no children, and the index this
     *   method would compute for one addresses an unused slot.
     * - Side effects: None; this method is a pure computation.
     *
     * @param pNodeIndex
     * Index of the parent node. Must be non-negative.
     *
     * @return
     * Index of the left child within the node array.
     */
    private int leftChildIndex(int pNodeIndex) {
        // In a zero-based heap layout the children of node i occupy 2i+1 and
        // 2i+2, because index zero consumes the slot that a one-based layout
        // would leave free.
        return 2 * pNodeIndex + 1;
    }

    /**
     * Computes the node index of the right child of the specified node.
     *
     * Detailed explanation of:
     * - Purpose: Translates a parent position in the implicit tree into the
     *   position of the child covering the upper half of its range.
     * - Business context: Counterpart of leftChildIndex; the two together form
     *   the whole navigation machinery of this structure.
     * - Processing steps: Applies the standard heap-layout formula for a
     *   zero-based root.
     * - Assumptions: Assumes the caller descends only into a node whose range
     *   holds more than one position.
     * - Side effects: None; this method is a pure computation.
     *
     * @param pNodeIndex
     * Index of the parent node. Must be non-negative.
     *
     * @return
     * Index of the right child within the node array.
     */
    private int rightChildIndex(int pNodeIndex) {
        // The right child follows immediately after the left one in the layout.
        return 2 * pNodeIndex + 2;
    }

    /**
     * Computes the position at which a range is split into its two halves.
     *
     * Detailed explanation of:
     * - Purpose: Determines the boundary that every descent uses to decide which
     *   child or children a request has to continue into.
     * - Business context: Concentrating the split rule here guarantees that the
     *   build, the query and the update decompose ranges identically. They must,
     *   because a query reads exactly the aggregates that the build wrote, and a
     *   divergent split would make the two address different ranges.
     * - Processing steps: Adds half the width of the range to its start, rather
     *   than halving the sum of both bounds, which keeps the computation correct
     *   even for position counts near the upper end of the integer range, where
     *   that sum would overflow into a negative value.
     * - Assumptions: Assumes the range is non-empty and ordered, that is, that
     *   its start does not exceed its end.
     * - Side effects: None; this method is a pure computation.
     *
     * @param pRangeStart
     * First position of the range. Must be non-negative and must not exceed
     * pRangeEnd.
     *
     * @param pRangeEnd
     * Last position of the range, inclusive. Must be less than the tree size.
     *
     * @return
     * The last position belonging to the lower half of the range; the upper half
     * begins immediately after it.
     */
    private int middleIndex(int pRangeStart, int pRangeEnd) {
        // Overflow-safe midpoint: the difference of two positions always fits,
        // whereas their sum need not.
        return pRangeStart + (pRangeEnd - pRangeStart) / 2;
    }

    /**
     * Reads the aggregate cached at the specified node.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single typed view onto the untyped node array.
     * - Business context: Java erases generic types and forbids the creation of a
     *   generic array, so the node store has to be an Object array. Routing every
     *   read through this method confines the resulting unchecked cast to one
     *   place instead of scattering suppressions across the class.
     * - Processing steps: Casts the stored reference back to the content type.
     * - Assumptions: Assumes the slot holds a value written by this class, which
     *   is guaranteed because the array is private, final and never handed out.
     *   The cast can therefore not fail at runtime.
     * - Side effects: None; this method only reads the array.
     *
     * @param pNodeIndex
     * Index of the node to read. Must address a slot that a descent actually
     * reaches; slots outside the tree hold null.
     *
     * @return
     * The aggregate cached at that node, or null when the slot was never
     * populated.
     */
    @SuppressWarnings("unchecked")
    private ContentType nodeAt(int pNodeIndex) {
        return (ContentType) nodes[pNodeIndex];
    }

    /**
     * Recursively fills the node array with the aggregate of every range the
     * layout defines.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the invariant the whole structure rests on, namely
     *   that each node holds the merge of its two children and therefore the
     *   aggregate of exactly the range it covers.
     * - Business context: The recursion visits each node once and performs one
     *   merge per internal node, which is why building costs time linear in the
     *   number of positions rather than the linearithmic cost of inserting the
     *   elements one by one.
     * - Processing steps:
     *   1. Store the element itself once the range has collapsed to a single
     *      position, since the aggregate of one element is that element.
     *   2. Otherwise split the range, build both halves first, and merge their
     *      results into this node afterwards.
     * - Assumptions: Assumes the node index reached lies within the reserved
     *   slots, which the NODES_PER_POSITION bound guarantees for every range this
     *   recursion can produce.
     * - Side effects: Writes to the node array throughout the subtree rooted at
     *   the given node.
     *
     * @param pElements
     * The initial values being read into the leaves. Must not be null and must
     * hold a non-null entry for every position of the range.
     *
     * @param pNodeIndex
     * Index of the node being filled, that is, the root of the subtree currently
     * under construction.
     *
     * @param pRangeStart
     * First position covered by this node. Must be non-negative.
     *
     * @param pRangeEnd
     * Last position covered by this node, inclusive. Must be less than the tree
     * size and must not precede pRangeStart.
     */
    private void build(ContentType[] pElements, int pNodeIndex, int pRangeStart, int pRangeEnd) {
        /*
         * A range spanning a single position cannot be divided further. Such a
         * node is a leaf and caches the element verbatim, which is the base case
         * every branch of the recursion eventually reaches.
         */
        if (pRangeStart == pRangeEnd) {
            nodes[pNodeIndex] = pElements[pRangeStart];
            return;
        }

        // Split the range so that both halves are as even as the width allows; an
        // odd width gives the additional position to the lower half.
        int middle = middleIndex(pRangeStart, pRangeEnd);

        // Build both halves before merging them: a node aggregate is defined in
        // terms of its children, so the children have to be correct already.
        build(pElements, leftChildIndex(pNodeIndex), pRangeStart, middle);
        build(pElements, rightChildIndex(pNodeIndex), middle + 1, pRangeEnd);

        /*
         * Merge the two halves into this node. The left half is passed first
         * because the combiner is promised its arguments in positional order,
         * which is what keeps order-sensitive aggregates well defined.
         */
        nodes[pNodeIndex] = combiner.combine(
                nodeAt(leftChildIndex(pNodeIndex)),
                nodeAt(rightChildIndex(pNodeIndex)));
    }

}
