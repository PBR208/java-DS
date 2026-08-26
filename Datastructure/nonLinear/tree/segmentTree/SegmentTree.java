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

    /**
     * Reports the aggregate over the specified range of positions.
     *
     * Detailed explanation of:
     * - Purpose: Answers the question the structure exists for, namely what the
     *   maintained aggregate is over an arbitrary contiguous span of positions,
     *   without inspecting the elements that span contains.
     * - Business context: Serves as the primary read entry point. The answer is
     *   assembled from at most two cached ranges per level of the tree, which is
     *   what keeps the cost logarithmic no matter how wide the requested range
     *   is: a query covering the entire array is answered by a single node.
     * - Processing steps:
     *   1. Reject a range that no position could satisfy, reporting the absence
     *      of an answer through null.
     *   2. Descend from the root, which covers every position, and let the
     *      recursion assemble the answer.
     * - Assumptions: Assumes the caller uses inclusive bounds, matching the way
     *   ranges are described throughout this class.
     * - Side effects: None; this operation only reads cached aggregates.
     *
     * @param pFromIndex
     * First position of the range, inclusive. Must be non-negative and must not
     * exceed pToIndex.
     *
     * @param pToIndex
     * Last position of the range, inclusive. Must be less than the number of
     * positions the tree covers.
     *
     * @return
     * The aggregate over the requested range, or null when the range is empty,
     * reversed, or reaches outside the covered positions, and likewise for every
     * query against a tree covering no positions at all. Null is unambiguous as
     * that marker because the tree neither stores nor produces null aggregates:
     * construction rejects null elements and the combiner is contractually
     * forbidden from returning null. Reporting an invalid range this way rather
     * than by exception follows the convention of the read operations elsewhere
     * in this library, where an unanswerable question yields null instead of
     * interrupting the caller.
     */
    public ContentType query(int pFromIndex, int pToIndex) {
        /*
         * Reject anything that cannot describe a real span of stored positions:
         * a negative start, an end beyond the covered positions, or bounds in the
         * wrong order. The size check also covers the empty tree, where no index
         * can be valid at all.
         */
        if (pFromIndex < 0 || pToIndex >= size || pFromIndex > pToIndex) {
            return null;
        }

        // Start at the root, whose range spans every position, so that the
        // descent can narrow down to the requested span from a node guaranteed to
        // contain it.
        return queryRange(ROOT_INDEX, 0, size - 1, pFromIndex, pToIndex);
    }

    /**
     * Recursively assembles the aggregate over a requested range from the cached
     * ranges that cover it.
     *
     * Detailed explanation of:
     * - Purpose: Decomposes the requested range into the smallest number of
     *   cached ranges whose union is exactly that range, and merges their
     *   aggregates in positional order.
     * - Business context: This method is the reason a segment tree beats a scan.
     *   Each level of the recursion contributes at most two nodes that are only
     *   partially covered, because a partially covered node can only occur at the
     *   two ends of the requested range; everything between those ends is served
     *   by whole cached nodes. The number of visited nodes is therefore bounded
     *   by a constant per level, and the levels are logarithmic in the position
     *   count.
     * - Processing steps:
     *   1. Report no contribution when the node range and the requested range do
     *      not overlap at all.
     *   2. Report the cached aggregate directly when the node range lies entirely
     *      inside the requested range, which is the step that avoids descending
     *      into the elements.
     *   3. Otherwise split, recurse into both halves, and merge whatever the two
     *      sides contributed.
     * - Assumptions: Assumes the requested range was already validated against
     *   the covered positions by the public entry point, so the recursion is
     *   concerned only with overlap and never with legality.
     * - Side effects: None; this method only reads cached aggregates.
     *
     * @param pNodeIndex
     * Index of the node currently visited.
     *
     * @param pRangeStart
     * First position covered by that node.
     *
     * @param pRangeEnd
     * Last position covered by that node, inclusive.
     *
     * @param pFromIndex
     * First position of the requested range, inclusive.
     *
     * @param pToIndex
     * Last position of the requested range, inclusive.
     *
     * @return
     * The aggregate of the overlap between the node range and the requested
     * range, or null when the two do not overlap. Returning null for an absent
     * contribution is what frees this implementation from requiring a neutral
     * element from the combiner: a caller merging sums could supply zero, but a
     * caller merging minima would have to invent an artificial infinity for their
     * content type, and demanding that would restrict the aggregates this class
     * can serve for no benefit. The two null checks below cost one comparison per
     * merge and remove that restriction entirely.
     */
    private ContentType queryRange(int pNodeIndex, int pRangeStart, int pRangeEnd, int pFromIndex, int pToIndex) {
        /*
         * The node covers positions entirely outside the requested range, so
         * nothing below it can contribute and the subtree is abandoned. Pruning
         * here rather than at the parent keeps the overlap test in one place.
         */
        if (pToIndex < pRangeStart || pFromIndex > pRangeEnd) {
            return null;
        }

        /*
         * The node range lies wholly inside the requested range, so its cached
         * aggregate is exactly the contribution of this subtree and the elements
         * below need not be visited. This early return is the entire performance
         * argument of the structure.
         */
        if (pFromIndex <= pRangeStart && pRangeEnd <= pToIndex) {
            return nodeAt(pNodeIndex);
        }

        // The overlap is partial, so the answer has to be assembled from both
        // halves of this node using the same split the build used.
        int middle = middleIndex(pRangeStart, pRangeEnd);

        // Collect the contribution of the lower half, which may be absent when
        // the requested range begins beyond the midpoint.
        ContentType leftResult = queryRange(leftChildIndex(pNodeIndex), pRangeStart, middle, pFromIndex, pToIndex);

        // Collect the contribution of the upper half, which may be absent when
        // the requested range ends at or before the midpoint.
        ContentType rightResult = queryRange(rightChildIndex(pNodeIndex), middle + 1, pRangeEnd, pFromIndex, pToIndex);

        // Only the upper half contributed, so its aggregate stands unchanged;
        // merging it with an absent contribution would require a neutral element
        // that this implementation deliberately does not demand.
        if (leftResult == null) {
            return rightResult;
        }

        // Only the lower half contributed, for the mirrored reason.
        if (rightResult == null) {
            return leftResult;
        }

        // Both halves contributed, and the lower one is passed first so that the
        // combiner receives the two aggregates in the order their positions
        // appear in the array.
        return combiner.combine(leftResult, rightResult);
    }

    /**
     * Replaces the value stored at the specified position and refreshes every
     * aggregate that depended on it.
     *
     * Detailed explanation of:
     * - Purpose: Keeps the cached aggregates consistent with the underlying
     *   values after a single position changes.
     * - Business context: Serves as the primary write entry point and is the
     *   operation that distinguishes this structure from a precomputed table of
     *   prefix aggregates. A position participates in exactly one cached range
     *   per level of the tree, so only that one path has to be repaired, and the
     *   remaining aggregates stay valid untouched. A prefix table, by contrast,
     *   would have to recompute everything from the changed position onwards.
     * - Processing steps:
     *   1. Ignore a position outside the covered range and a null value, neither
     *      of which describes a change the tree could apply.
     *   2. Descend to the affected leaf, overwrite it, and recompute the
     *      aggregate of every node on the way back up.
     * - Assumptions: Assumes the caller intends a replacement rather than an
     *   insertion. The tree covers a fixed number of positions, so a value can be
     *   exchanged but no position can be added or removed.
     * - Side effects: Overwrites one leaf slot and rewrites the aggregate of each
     *   of its ancestors, leaving the rest of the node array untouched.
     *
     * @param pIndex
     * Position whose value is replaced. Must be non-negative and less than the
     * number of positions the tree covers; an out-of-range position is silently
     * ignored, consistent with the tolerant write behaviour of the other
     * structures in this library.
     *
     * @param pValue
     * The new value for that position. Must not be null; a null value is ignored,
     * because storing it would hand null to the combiner during the repair and
     * would additionally collide with the marker that the query descent uses for
     * a range contributing nothing.
     */
    public void update(int pIndex, ContentType pValue) {
        /*
         * Reject a position the tree does not cover. The size comparison also
         * rules out every position in a tree covering no positions, where no
         * update can ever be meaningful.
         */
        if (pIndex < 0 || pIndex >= size) {
            return;
        }

        // Reject null early: it would propagate up the repair path and turn
        // genuine aggregates into absent ones.
        if (pValue == null) {
            return;
        }

        // Begin at the root, the only node guaranteed to cover the position,
        // and let the recursion narrow down to the single leaf holding it.
        updateAt(ROOT_INDEX, 0, size - 1, pIndex, pValue);
    }

    /**
     * Recursively descends to the leaf of the specified position, replaces its
     * value, and recomputes the aggregates of the nodes above it.
     *
     * Detailed explanation of:
     * - Purpose: Restores the invariant that every node holds the merge of its
     *   two children after a single leaf has changed.
     * - Business context: The repair travels in one direction only. A leaf is
     *   contained in exactly one node per level, so precisely the ancestors of
     *   that leaf can have become stale, and no sibling subtree needs to be
     *   inspected. This is the structural reason an update costs a logarithmic
     *   number of merges rather than a rebuild.
     * - Processing steps:
     *   1. Overwrite the value once the range has narrowed to the single position
     *      being changed.
     *   2. Otherwise continue into the half that contains the position, and merge
     *      both children into this node once that half has been repaired.
     * - Assumptions: Assumes the position lies inside the node range, which holds
     *   for the root by validation in the public entry point and is preserved by
     *   the choice of branch at every step.
     * - Side effects: Writes to one leaf slot and to every node on the path from
     *   the root to it.
     *
     * @param pNodeIndex
     * Index of the node currently visited.
     *
     * @param pRangeStart
     * First position covered by that node.
     *
     * @param pRangeEnd
     * Last position covered by that node, inclusive.
     *
     * @param pIndex
     * Position being changed. Must lie within the node range.
     *
     * @param pValue
     * The new value for that position. Must not be null.
     */
    private void updateAt(int pNodeIndex, int pRangeStart, int pRangeEnd, int pIndex, ContentType pValue) {
        /*
         * The range has narrowed to the single position being changed, so this
         * node is the leaf holding it and the new value replaces the old one
         * directly. No merge is involved, because a leaf aggregates one element.
         */
        if (pRangeStart == pRangeEnd) {
            nodes[pNodeIndex] = pValue;
            return;
        }

        // Split exactly as the build did, so that the descent follows the same
        // node boundaries the cached aggregates were computed for.
        int middle = middleIndex(pRangeStart, pRangeEnd);

        // Continue into the half that contains the position. The other half
        // cannot have become stale, since none of its cached ranges includes the
        // changed position.
        if (pIndex <= middle) {
            updateAt(leftChildIndex(pNodeIndex), pRangeStart, middle, pIndex, pValue);
        } else {
            updateAt(rightChildIndex(pNodeIndex), middle + 1, pRangeEnd, pIndex, pValue);
        }

        /*
         * Recompute this node from its children now that the affected one is up
         * to date. Doing so after the recursion is what makes the repair travel
         * from the leaf back to the root, which is the only order in which each
         * merge sees correct inputs.
         */
        nodes[pNodeIndex] = combiner.combine(
                nodeAt(leftChildIndex(pNodeIndex)),
                nodeAt(rightChildIndex(pNodeIndex)));
    }

    /**
     * Reports the value currently stored at the specified position.
     *
     * Detailed explanation of:
     * - Purpose: Gives callers read access to the individual elements, which the
     *   tree otherwise only ever exposes in aggregated form.
     * - Business context: The tree keeps no copy of the array it was built from,
     *   so the leaves are the only remaining record of the individual values, and
     *   update changes them without the caller seeing the result. Exposing them
     *   makes a tree self-contained, which matters for callers that read a value
     *   in order to derive the next one they write.
     * - Processing steps: Delegates to the range query over the single requested
     *   position, which lands on exactly the leaf holding it.
     * - Assumptions: Assumes the caller accepts the descent cost rather than
     *   keeping a parallel copy of the array; a caller reading positions in bulk
     *   is better served by retaining the array it built the tree from.
     * - Side effects: None; this operation only reads cached aggregates.
     *
     * @param pIndex
     * Position to read. Must be non-negative and less than the number of covered
     * positions.
     *
     * @return
     * The value stored at that position, or null when the position lies outside
     * the covered range, including every position of a tree covering none. Null
     * is unambiguous here for the same reason as in query: no null value is ever
     * stored.
     */
    public ContentType get(int pIndex) {
        /*
         * A single position is just a range of width one, and the descent for it
         * ends at the leaf holding that position. Reusing the query keeps the
         * bounds validation and the traversal rules in one place rather than
         * duplicating a second descent that could drift from the first.
         */
        return query(pIndex, pIndex);
    }

    /**
     * Reports how many positions the tree covers.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the fixed width of the tree, which bounds every valid
     *   index accepted by query, update and get.
     * - Business context: Callers commonly iterate over positions or query the
     *   full range, and both require this bound. It counts positions, not the
     *   nodes used to cache aggregates over them: a tree over ten positions
     *   reports ten while holding close to twenty aggregates.
     * - Processing steps: Returns the count recorded at construction, which
     *   cannot change afterwards because the node layout was derived from it.
     * - Assumptions: None.
     * - Side effects: None; this operation only reads a field.
     *
     * @return
     * The number of covered positions, never negative. Zero exactly when the tree
     * was built from an empty array.
     */
    public int size() {
        return size;
    }

    /**
     * Reports whether the tree covers no positions at all.
     *
     * Detailed explanation of:
     * - Purpose: Lets callers distinguish a tree that can answer questions from
     *   one that cannot, without interpreting a null result.
     * - Business context: An empty tree arises only from construction over an
     *   empty array and stays empty for its entire lifetime, since the structure
     *   has no operation that adds positions. Checking this once is therefore
     *   more meaningful than checking for a null answer on every query.
     * - Processing steps: Compares the recorded position count against zero.
     * - Assumptions: None.
     * - Side effects: None; this operation only reads a field.
     *
     * @return
     * True when the tree covers no positions and every query, update and get is
     * consequently without effect; false when at least one position is covered.
     */
    public boolean isEmpty() {
        return size == 0;
    }

}
