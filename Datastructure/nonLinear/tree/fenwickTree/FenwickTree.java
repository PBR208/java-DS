package nonLinear.tree.fenwickTree;

/**
 * Purpose:
 * Implements a Fenwick tree, also called a binary indexed tree, which maintains
 * the aggregate of every prefix of a changing sequence in a single array of the
 * same length as that sequence. Its organising idea is that the binary
 * representation of an index already describes how to decompose the prefix
 * ending there: a prefix of length thirteen splits into blocks of eight, four and
 * one, exactly the set bits of thirteen, so if every array slot stores the
 * aggregate of one such block, any prefix is assembled by clearing set bits one
 * at a time. The structure exists because that idea makes both directions cheap
 * at once, a prefix being read by walking bits downwards and a point change being
 * absorbed by walking them upwards, each in as many steps as the index has bits.
 * It is the compact specialist next to the segment tree in this package: it
 * answers a narrower class of questions, since it can only aggregate quantities
 * that can also be subtracted again, but it does so in one array slot per
 * position, with no recursion and with a notably smaller constant factor.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Array-backed Fenwick tree over a fixed number of positions, generic in the
 * stored content type and in the commutative group aggregated over it.
 *
 * Responsibility: Encapsulates the storage of the prefix fragments, the assembly
 * of any prefix aggregate from them, the derivation of arbitrary range aggregates
 * by removing one prefix from another, and the absorption of point changes into
 * every fragment that covers the changed position. It maintains the invariant
 * that the slot at one-based index i holds the aggregate of exactly the positions
 * from i minus its lowest set bit, exclusive, up to i, inclusive.
 *
 * Scope: Used within the nonLinear.tree package wherever prefix or range
 * aggregates over a changing sequence are required and the aggregate is
 * invertible. The aggregate is not fixed here: it is supplied by the FenwickGroup
 * handed in at construction, so sums, exclusive-or folds, counts and products of
 * invertible factors are all served by this one class.
 *
 * Dependencies: Depends on the FenwickGroup interface of this package for the
 * neutral element, the merge and the inverse merge, and otherwise only on a plain
 * Object array used as the fragment store. Like the segment tree, it deliberately
 * does not implement the ComparableContent-based contract of the search trees
 * here, because it never compares two stored values and derives its shape from
 * array positions alone.
 *
 * Thread-safety: This class is not thread-safe. A point change rewrites several
 * fragments in sequence, so a concurrent query may read some of them in their
 * updated and others in their previous state and return an aggregate that never
 * existed. External synchronization is required whenever instances are shared
 * across threads; concurrent queries alone are safe as long as nothing is
 * written.
 *
 * Lifecycle: A FenwickTree is created for a fixed number of positions and keeps
 * that number for its entire lifetime. Values change freely through add and set,
 * but the tree neither grows nor shrinks, because the block each slot stands for
 * is derived from its index and a change in length would silently redefine those
 * blocks. Callers needing a different length build a new tree.
 *
 * Architectural role: Serves as the compact counterpart to the SegmentTree of the
 * neighbouring package and as the reference implementation showing that a tree
 * need not be a tree in memory at all: the parent and child relationships of this
 * one exist solely in the arithmetic of the indices, and no reference, recursion
 * or node object is stored anywhere.
 *
 * @param <ContentType>
 * The type of the stored values and of the aggregates derived from them. No
 * ordering or comparison capability is required of this type; every operation on
 * it is delegated to the supplied FenwickGroup.
 */
public class FenwickTree<ContentType> {

    /**
     * Difference between a caller's position and the internal slot holding it.
     *
     * The public interface counts positions from zero, matching the arrays and
     * the other structures of this library, while the fragment array is indexed
     * from one. The offset is not cosmetic: the whole traversal rests on the
     * lowest set bit of an index, and zero has no set bit at all, so a zero-based
     * slot could neither be reached from its parent nor advanced from, and an
     * update starting there would loop forever without moving. Reserving slot
     * zero and shifting every position by one removes that degenerate case
     * entirely instead of guarding against it on every step.
     */
    private static final int INDEX_BASE_OFFSET = 1;

    /**
     * The commutative group defining which aggregate this tree maintains.
     *
     * Held final because every fragment stored in the tree was produced by this
     * group: exchanging it later would leave the stored aggregates unrelated to
     * the question the tree then claims to answer.
     */
    private final FenwickGroup<ContentType> group;

    /**
     * Prefix fragments of the sequence, indexed from one.
     *
     * The slot at index i holds the aggregate of the block of positions ending at
     * i whose length is the lowest set bit of i, so slot eight covers the first
     * eight positions, slot twelve covers the four positions ending at twelve, and
     * every odd slot covers a single position. The block boundaries are never
     * stored, since they follow from the index itself. Slot zero is reserved and
     * never read, which is what allows the index arithmetic to terminate. The
     * array is declared as Object[] rather than ContentType[] because Java forbids
     * the creation of a generic array; every read passes through fragmentAt, which
     * confines the resulting unchecked cast to a single place.
     */
    private final Object[] fragments;

    /**
     * Number of positions the tree covers.
     *
     * Fixed at construction, since the block each slot stands for is derived from
     * it together with the index. It bounds every valid position and is one less
     * than the length of the fragment array.
     */
    private final int size;

    /**
     * Constructs a Fenwick tree over the given number of positions, each holding
     * the neutral element of the supplied group.
     *
     * Detailed explanation of:
     * - Purpose: Produces an empty tree that is immediately usable, in the sense
     *   that every position exists and aggregates to nothing.
     * - Business context: This is the constructor for the accumulating use of the
     *   structure, where positions are counters or running totals that start at
     *   nothing and are raised by repeated calls to add. Frequency tables and the
     *   inversion counting that Fenwick trees are classically used for are built
     *   this way, and they would otherwise have to allocate an array of neutral
     *   elements only to hand it straight back.
     * - Processing steps:
     *   1. Reject a negative length and a null group.
     *   2. Allocate the fragment array with the reserved slot at index zero.
     *   3. Fill every slot with the neutral element, which is a valid state
     *      because the aggregate of any block of neutral elements is neutral
     *      again.
     * - Assumptions: Assumes the supplied group satisfies the obligations its own
     *   contract states, in particular that its neutral element is neutral on both
     *   sides.
     * - Side effects: Allocates the fragment array and populates it.
     *
     * Time complexity: O(n) in the number of positions; every slot is written
     * once. No merge is performed, because neutral blocks need no aggregation.
     * Space complexity: O(n) for the fragment array, which holds exactly one slot
     * per position plus the reserved slot at index zero.
     *
     * @param pSize
     * Number of positions the tree covers. Must not be negative. Zero is
     * accepted and yields a tree that answers every query with null, so that
     * callers deriving the length from a data set need no special case.
     *
     * @param pGroup
     * The group defining the maintained aggregate. Must not be null and must
     * satisfy the requirements stated by FenwickGroup, in particular
     * commutativity and the presence of a genuine inverse.
     *
     * @throws IllegalArgumentException
     * Thrown when pSize is negative or pGroup is null. Both leave the tree
     * incapable of serving its purpose, and since a tree is constructed exactly
     * once, reporting the defect at that single point is far cheaper than
     * discovering it during a later query.
     */
    public FenwickTree(int pSize, FenwickGroup<ContentType> pGroup) {
        // A negative length describes no sequence at all and would additionally
        // make the array allocation below fail with an exception that names the
        // symptom rather than the cause.
        if (pSize < 0) {
            throw new IllegalArgumentException("The number of positions must not be negative.");
        }

        // Without a group the tree could hold values but could neither aggregate
        // nor separate them, which is the entire purpose of the structure.
        if (pGroup == null) {
            throw new IllegalArgumentException("The group must not be null.");
        }

        this.group = pGroup;
        this.size = pSize;

        // One slot per position, plus the reserved slot zero that the index
        // arithmetic needs as its terminator.
        this.fragments = new Object[pSize + INDEX_BASE_OFFSET];

        /*
         * Seed every slot, including the reserved one, with the neutral element.
         * A tree in which nothing has been accumulated must report the neutral
         * element for every prefix, and since each fragment aggregates a block of
         * neutral elements, the neutral element is exactly what each of them
         * holds.
         */
        for (int treeIndex = 0; treeIndex < fragments.length; treeIndex++) {
            fragments[treeIndex] = pGroup.identity();
        }
    }

    /**
     * Constructs a Fenwick tree over the supplied values, deriving every prefix
     * fragment from them in a single pass.
     *
     * Detailed explanation of:
     * - Purpose: Produces a tree that already reflects an existing sequence,
     *   without paying the cost of inserting its values one at a time.
     * - Business context: This is the constructor for the querying use of the
     *   structure, where a known sequence is loaded and then interrogated and
     *   modified. Building in one pass rather than through repeated additions is
     *   what keeps that load linear; the obvious alternative of calling add once
     *   per position would cost O(n log n) for the same result.
     * - Processing steps:
     *   1. Reject a null value array, a null group or a null value.
     *   2. Allocate the fragment array and copy each value into the slot of its
     *      own position, which is the block of length one that every odd index
     *      and, initially, every other index stands for.
     *   3. Fold each slot into the slot that covers it, in increasing index
     *      order, so that every fragment ends up holding its whole block.
     * - Assumptions: Assumes the group is commutative, as its contract demands.
     *   The fold below merges blocks in index order rather than in position
     *   order, so an order-sensitive merge would produce fragments that are
     *   internally consistent but do not describe the supplied sequence.
     * - Side effects: Allocates the fragment array and populates it. The value
     *   array is read but never retained, so later changes the caller makes to it
     *   do not reach the tree.
     *
     * Time complexity: O(n) in the number of positions. Each slot is written once
     * and folded into its covering slot at most once, giving fewer than n merges
     * in total.
     * Space complexity: O(n) for the fragment array; the fold itself is in place
     * and allocates nothing.
     *
     * @param pValues
     * The initial values, one per position, in the order the tree indexes them.
     * Must not be null and must not contain null. May be empty, which yields a
     * tree covering no positions.
     *
     * @param pGroup
     * The group defining the maintained aggregate. Must not be null and must
     * satisfy the requirements stated by FenwickGroup.
     *
     * @throws IllegalArgumentException
     * Thrown when pValues is null, when pGroup is null, or when any value is
     * null. A null value would be handed to the merge during the fold and would
     * additionally collide with the marker this class uses for a question it
     * declines to answer.
     */
    public FenwickTree(ContentType[] pValues, FenwickGroup<ContentType> pGroup) {
        // Without values there is nothing to derive a length from, and the
        // difference between "no array" and "empty array" is meaningful enough to
        // report rather than to silently equate.
        if (pValues == null) {
            throw new IllegalArgumentException("The value array must not be null.");
        }

        // Without a group the fragments could never be formed in the first place.
        if (pGroup == null) {
            throw new IllegalArgumentException("The group must not be null.");
        }

        // Validate the contents before allocating anything, so that a rejected
        // argument leaves no half-built tree behind.
        for (int index = 0; index < pValues.length; index++) {
            if (pValues[index] == null) {
                throw new IllegalArgumentException("The value array must not contain null values.");
            }
        }

        this.group = pGroup;
        this.size = pValues.length;
        this.fragments = new Object[size + INDEX_BASE_OFFSET];

        /*
         * The reserved slot is never read by any traversal, but it is given the
         * neutral element rather than being left null so that no slot of this
         * array ever holds a value the group could not consume.
         */
        this.fragments[0] = pGroup.identity();

        buildFrom(pValues);
    }

    /**
     * Isolates the lowest set bit of an index.
     *
     * Detailed explanation of:
     * - Purpose: Reports the length of the block that the slot at the given index
     *   is responsible for, which is simultaneously the step width by which
     *   traversals move between slots.
     * - Business context: This single expression is the whole structure of a
     *   Fenwick tree. Subtracting the result from an index yields the slot that
     *   covers the remaining prefix, which is how a prefix is read; adding it
     *   yields the next larger slot whose block contains the index, which is how
     *   a change is propagated. Both walks clear or carry one bit per step, which
     *   is why both take as many steps as the index has bits.
     * - Processing steps: Exploits the two's-complement representation of
     *   negative integers. Negating a value inverts all its bits and adds one,
     *   which leaves every bit below the lowest set bit at zero, flips that bit
     *   back to one, and inverts everything above it. Combining the value with its
     *   own negation therefore retains exactly the lowest set bit.
     * - Assumptions: Assumes a positive index. The result for zero is zero, which
     *   would leave every walk standing still; the one-based indexing of the
     *   fragment array exists precisely so that no traversal ever reaches it.
     * - Side effects: None; this method is a pure computation.
     *
     * Time complexity: O(1); one negation and one bitwise conjunction.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pTreeIndex
     * One-based index of a fragment slot. Must be positive for the result to be
     * meaningful.
     *
     * @return
     * The value of the lowest set bit of the index, that is, the size of the
     * block covered by that slot.
     */
    private static int lowestSetBit(int pTreeIndex) {
        return pTreeIndex & -pTreeIndex;
    }

    /**
     * Reads the fragment stored at the specified slot.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single typed view onto the untyped fragment array.
     * - Business context: Java erases generic types and forbids the creation of a
     *   generic array, so the fragment store has to be an Object array. Routing
     *   every read through this method confines the resulting unchecked cast to
     *   one place instead of scattering suppressions across the class.
     * - Processing steps: Casts the stored reference back to the content type.
     * - Assumptions: Assumes the slot holds a value written by this class, which
     *   is guaranteed because the array is private, final and never handed out.
     *   The cast can therefore not fail at runtime.
     * - Side effects: None; this method only reads the array.
     *
     * Time complexity: O(1); one array read. The cast is erased at compile time
     * and costs nothing at runtime.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pTreeIndex
     * One-based index of the slot to read. Must lie within the fragment array.
     *
     * @return
     * The aggregate of the block covered by that slot. Never null for a tree that
     * completed construction.
     */
    @SuppressWarnings("unchecked")
    private ContentType fragmentAt(int pTreeIndex) {
        return (ContentType) fragments[pTreeIndex];
    }

    /**
     * Fills the fragment array from the supplied values in a single pass.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the invariant the structure rests on, namely that
     *   each slot holds the aggregate of the block of positions ending at its own
     *   index whose length is that index's lowest set bit.
     * - Business context: The pass exploits the fact that the blocks are nested:
     *   the block of a slot is exactly its own value together with the blocks of
     *   the slots that were folded into it. Handing each slot upwards to the one
     *   covering it therefore completes every fragment with a single merge per
     *   slot, in place, and in increasing index order, because a slot is always
     *   complete by the time it is read.
     * - Processing steps:
     *   1. Copy each value into the slot of its own position.
     *   2. Walk the slots in increasing order and merge each into the slot found
     *      by adding its lowest set bit, which is the next larger block containing
     *      it, skipping the step when that slot lies beyond the tree.
     * - Assumptions: Assumes the fragment array has already been allocated to the
     *   full length and that the values contain no null.
     * - Side effects: Writes every slot of the fragment array.
     *
     * Time complexity: O(n); one write and at most one merge per position.
     * Space complexity: O(1) beyond the array being filled; the fold is in place.
     *
     * @param pValues
     * The initial values, one per position. Must not be null and must not contain
     * null.
     */
    private void buildFrom(ContentType[] pValues) {
        // Seed each slot with the value of its own position. At this point every
        // slot describes a block of length one, which is correct only for the odd
        // indices and is repaired for all others by the fold below.
        for (int position = 0; position < size; position++) {
            fragments[position + INDEX_BASE_OFFSET] = pValues[position];
        }

        // Fold each slot into the slot whose block contains it. Increasing order
        // is essential: a slot must already hold its complete block before it is
        // handed upwards, and every slot that feeds it carries a smaller index.
        for (int treeIndex = INDEX_BASE_OFFSET; treeIndex <= size; treeIndex++) {
            int coveringIndex = treeIndex + lowestSetBit(treeIndex);

            /*
             * A covering slot beyond the last position means this block is not
             * contained in any larger one within this tree, so the fragment is
             * already final and nothing is handed upwards.
             */
            if (coveringIndex <= size) {
                fragments[coveringIndex] = group.combine(fragmentAt(coveringIndex), fragmentAt(treeIndex));
            }
        }
    }

    /**
     * Accumulates the fragments describing the prefix that ends at the specified
     * slot.
     *
     * Detailed explanation of:
     * - Purpose: Assembles the aggregate of the first positions of the sequence
     *   out of the stored blocks, which is the only aggregate this structure holds
     *   directly and the one every public query is derived from.
     * - Business context: The walk is the reading half of the idea the structure
     *   is named for. Each slot covers the block of positions ending at its own
     *   index whose length is that index's lowest set bit, so removing that bit
     *   moves to the slot covering everything before the block just consumed. The
     *   bits of the index are therefore cleared one by one, and the blocks
     *   collected are exactly the powers of two the index decomposes into.
     * - Processing steps: Starts from the neutral element and repeatedly merges
     *   the current slot before clearing its lowest set bit, until the index has
     *   reached the reserved slot at zero and no bit is left.
     * - Assumptions: Assumes the slot index lies within the fragment array, which
     *   the public entry points establish. An index of zero is explicitly valid
     *   and describes the empty prefix, for which the loop performs no iteration
     *   and the neutral element is returned unchanged.
     * - Side effects: None; this method only reads fragments.
     *
     * Time complexity: O(log n); one iteration per set bit of the index, and an
     * index bounded by n has at most as many bits as n does. There is no
     * recursion and no branching, which is what gives this walk a smaller
     * constant factor than the equivalent descent through a segment tree.
     * Space complexity: O(1); the walk is iterative and holds a single running
     * aggregate.
     *
     * @param pTreeIndex
     * One-based index of the last position of the prefix, or zero for the empty
     * prefix. Must not exceed the number of covered positions.
     *
     * @return
     * The aggregate of the positions up to and including that slot, or the
     * neutral element of the group for the empty prefix. Never null.
     */
    private ContentType prefixUpTo(int pTreeIndex) {
        // Start from the neutral element so that the empty prefix needs no
        // special case and the first merge below has a valid left operand.
        ContentType aggregate = group.identity();

        /*
         * Consume one block per iteration, moving from the end of the prefix
         * towards its start. Clearing the lowest set bit both leaves the block
         * just consumed behind and lands exactly on the slot that covers the
         * positions preceding it, which is why no boundary ever has to be
         * computed explicitly.
         */
        for (int currentIndex = pTreeIndex; currentIndex > 0; currentIndex -= lowestSetBit(currentIndex)) {
            aggregate = group.combine(aggregate, fragmentAt(currentIndex));
        }

        return aggregate;
    }

    /**
     * Reports the aggregate over the positions from the beginning of the sequence
     * up to the specified position.
     *
     * Detailed explanation of:
     * - Purpose: Answers the question the structure stores its data for, namely
     *   what the maintained aggregate is over an initial run of positions.
     * - Business context: Prefix aggregates are the natural currency of this
     *   structure and the reason it is the standard choice for running totals,
     *   cumulative frequency tables and order-statistics counting. Every other
     *   query offered here is expressed in terms of this one.
     * - Processing steps: Rejects a position the tree does not cover, then
     *   translates it into the one-based slot index and walks the fragments.
     * - Assumptions: Assumes the caller uses an inclusive bound, matching the way
     *   ranges are described throughout this class.
     * - Side effects: None; this operation only reads fragments.
     *
     * Time complexity: O(log n); one merge per set bit of the translated index.
     * Space complexity: O(1); the walk is iterative.
     *
     * @param pToIndex
     * Last position of the prefix, inclusive. Must be non-negative and less than
     * the number of covered positions.
     *
     * @return
     * The aggregate over the positions from zero up to and including pToIndex, or
     * null when that position lies outside the covered range, which includes
     * every query against a tree covering no positions. Null is unambiguous as
     * that marker because the tree never stores or produces null: construction
     * rejects null values and the group is contractually forbidden from returning
     * null. Reporting an unanswerable request this way rather than by exception
     * follows the convention of the read operations elsewhere in this library.
     */
    public ContentType prefix(int pToIndex) {
        // Reject a position outside the sequence. The size comparison also covers
        // the tree over no positions, where no position can be valid at all.
        if (pToIndex < 0 || pToIndex >= size) {
            return null;
        }

        // Translate the caller's zero-based position into the one-based slot that
        // ends the requested prefix.
        return prefixUpTo(pToIndex + INDEX_BASE_OFFSET);
    }

    /**
     * Reports the aggregate over the specified range of positions.
     *
     * Detailed explanation of:
     * - Purpose: Answers the aggregate over an arbitrary contiguous span rather
     *   than only over an initial one.
     * - Business context: This is where the demand for an invertible aggregate
     *   becomes visible. The structure holds no fragment describing a range that
     *   starts anywhere but at the beginning, so the answer is obtained by taking
     *   the prefix ending at the last requested position and removing from it the
     *   prefix ending just before the first. A segment tree needs no such removal
     *   and consequently serves minima and maxima, which have no inverse; this
     *   structure trades that generality for half the memory and a shorter walk.
     * - Processing steps:
     *   1. Reject a range that no position could satisfy.
     *   2. Assemble the longer prefix, assemble the shorter one, and remove the
     *      second from the first.
     * - Assumptions: Assumes the group's inverse is exact, as its contract
     *   demands. An approximate inverse, such as one over floating-point values,
     *   returns answers that drift as the two prefixes grow.
     * - Side effects: None; this operation only reads fragments.
     *
     * Time complexity: O(log n); two prefix walks, each of one merge per set bit,
     * followed by a single removal. Notably independent of the width of the
     * requested range, so a range covering the whole sequence costs no more than
     * one covering two positions.
     * Space complexity: O(1); both walks are iterative.
     *
     * @param pFromIndex
     * First position of the range, inclusive. Must be non-negative and must not
     * exceed pToIndex.
     *
     * @param pToIndex
     * Last position of the range, inclusive. Must be less than the number of
     * covered positions.
     *
     * @return
     * The aggregate over the requested range, or null when the range is empty,
     * reversed, or reaches outside the covered positions.
     */
    public ContentType range(int pFromIndex, int pToIndex) {
        /*
         * Reject anything that cannot describe a real span of covered positions:
         * a negative start, an end beyond the sequence, or bounds in the wrong
         * order.
         */
        if (pFromIndex < 0 || pToIndex >= size || pFromIndex > pToIndex) {
            return null;
        }

        // The prefix ending at the last requested position, which covers the
        // range plus everything preceding it.
        ContentType includingPrefix = prefixUpTo(pToIndex + INDEX_BASE_OFFSET);

        /*
         * The prefix ending immediately before the range. The one-based index of
         * the position preceding pFromIndex is pFromIndex itself, so no separate
         * subtraction is needed here, and a range starting at position zero
         * naturally yields the empty prefix.
         */
        ContentType precedingPrefix = prefixUpTo(pFromIndex);

        // Remove the part that precedes the range, leaving exactly the requested
        // span.
        return group.difference(includingPrefix, precedingPrefix);
    }

}
