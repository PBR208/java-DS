package nonLinear.tree.fenwickTree;

/**
 * Purpose:
 * Defines the algebraic contract that a Fenwick tree requires of the quantity it
 * aggregates. Where a segment tree merges two ranges and never needs to take one
 * apart again, a Fenwick tree stores only prefix fragments and derives the answer
 * for an arbitrary range by removing the shorter prefix from the longer one. That
 * subtraction is not an implementation detail but the price of the structure's
 * compactness, and it raises the demand on the aggregate from a mere merge to a
 * commutative group: a neutral element to start an empty accumulation from, an
 * associative and commutative merge, and the ability to undo that merge. This
 * interface states those three obligations explicitly so that the tree can be
 * generic over sums, exclusive-or, products of invertible factors and counts,
 * while making unmistakable why minimum and maximum cannot be served by it.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Strategy contract describing the commutative group a FenwickTree aggregates
 * over.
 *
 * Responsibility: Supplies the neutral element, the merge and the inverse merge
 * from which a FenwickTree derives every prefix fragment it stores and every
 * range answer it reports.
 *
 * Scope: Implemented by callers of FenwickTree, one implementation per aggregate
 * they wish to maintain. Implementations are typically stateless and are safe to
 * share between several trees.
 *
 * Dependencies: None. The contract is expressed purely in terms of the content
 * type it operates on, which keeps it independent of any ordering or arithmetic
 * that type might otherwise offer.
 *
 * Thread-safety: Implementations must be free of mutable state, or must
 * synchronize it themselves. A FenwickTree invokes these methods during ordinary
 * read operations, so an implementation mutating shared state would make even
 * concurrent queries unsafe.
 *
 * Lifecycle: Supplied once at tree construction and retained for the lifetime of
 * that tree, because every fragment the tree holds was produced by it and
 * replacing it afterwards would leave those fragments meaningless.
 *
 * Architectural role: Acts as the extension point of the Fenwick tree and as the
 * deliberate counterpart to SegmentCombiner in the neighbouring segmentTree
 * package. The two interfaces mark the exact boundary between the structures:
 * SegmentCombiner demands only an associative merge and therefore serves minima
 * and maxima, while this contract additionally demands commutativity and an
 * inverse and is rewarded with a tree of half the size and a tighter constant
 * factor. Choosing between the two structures is, in practice, choosing which of
 * these two contracts the aggregate can satisfy.
 *
 * @param <ContentType>
 * The type of the stored values and of the aggregates derived from them. Both
 * share one type, since a group is closed under its own operation.
 */
public interface FenwickGroup<ContentType> {

    /**
     * Reports the neutral element of the group.
     *
     * Detailed explanation of:
     * - Purpose: Provides the value that an empty aggregation starts from and
     *   that an untouched position holds.
     * - Business context: A Fenwick tree begins every prefix walk with an empty
     *   accumulation and may be constructed over positions no value was supplied
     *   for. Both cases need a value that behaves as though nothing were there,
     *   which for a sum is zero, for exclusive-or is zero as well, and for a
     *   product is one. Requiring it from the caller rather than guessing it is
     *   what keeps the tree free of assumptions about the content type.
     * - Processing steps: Implementations return a constant.
     * - Assumptions: The returned value must be neutral on both sides, so that
     *   combining it with any value yields that value unchanged regardless of the
     *   order of the two arguments. A value that is neutral on one side only
     *   would make the answers depend on the shape of the walk that produced
     *   them.
     * - Side effects: None permitted. The value may be requested many times per
     *   operation and must be the same on every call.
     *
     * Time complexity: Expected to be O(1). The tree requests the neutral element
     * once per prefix walk and once per uninitialised position during
     * construction.
     * Space complexity: Expected to be O(1); returning a shared immutable
     * constant is the intended implementation.
     *
     * @return
     * The neutral element of the group. Must never be null, because the tree
     * stores it in positions no value was supplied for and would otherwise hold
     * null fragments that later merges could not consume.
     */
    ContentType identity();

    /**
     * Merges two aggregates into the aggregate of both together.
     *
     * Detailed explanation of:
     * - Purpose: Produces the combined aggregate of two disjoint groups of
     *   positions from their individual aggregates.
     * - Business context: This is the operation the tree accumulates with, both
     *   when it folds the fragments along a prefix walk and when it absorbs a
     *   point change into every fragment covering the changed position.
     * - Processing steps: Implementations perform exactly one domain-specific
     *   operation, such as adding two sums or combining two bit masks.
     * - Assumptions: The operation must be associative, so that the grouping of
     *   three or more merges does not affect the result, and it must additionally
     *   be commutative, so that their order does not either. Commutativity is
     *   genuinely required here and is not a convenience: the fragments a Fenwick
     *   tree stores are absorbed in the order dictated by the binary structure of
     *   the indices, which does not follow the order of the positions they cover.
     *   An order-sensitive aggregate such as string concatenation is therefore
     *   served correctly by a segment tree but not by this structure. The
     *   operation must further be deterministic and free of side effects, since
     *   the tree invokes it during reads.
     * - Side effects: None permitted. The tree caches the returned values and
     *   would serve stale answers for an implementation whose result depends on
     *   anything beyond its two arguments.
     *
     * Time complexity: Determined entirely by the implementation. Every figure
     * documented on FenwickTree assumes a merge in O(1), which holds for the
     * arithmetic and bitwise aggregates this structure is normally used with; a
     * more expensive merge multiplies those figures by its own cost.
     * Space complexity: Likewise determined by the implementation, and expected
     * to be O(1) beyond the returned value.
     *
     * @param pLeft
     * The first aggregate. Never null; the tree neither stores nor propagates
     * null values, and it substitutes the neutral element wherever nothing has
     * been accumulated yet.
     *
     * @param pRight
     * The second aggregate. Never null, for the same reason as pLeft.
     *
     * @return
     * The aggregate of both arguments taken together. Must never be null, since
     * the tree stores the result directly into a fragment and a null there would
     * corrupt every later query touching it.
     */
    ContentType combine(ContentType pLeft, ContentType pRight);

    /**
     * Removes one aggregate from another, undoing a previous merge.
     *
     * Detailed explanation of:
     * - Purpose: Recovers the aggregate of the positions that remain when the
     *   positions covered by the subtrahend are excluded from those covered by
     *   the minuend.
     * - Business context: This is the operation that lets a structure storing
     *   only prefixes answer questions about arbitrary ranges: the aggregate over
     *   a range is the aggregate of the prefix ending at its last position with
     *   the aggregate of the prefix ending just before its first position removed
     *   again. The same operation lets a value be replaced at a position, since
     *   the tree must first work out what change would turn the old value into
     *   the new one.
     * - Processing steps: Implementations perform the inverse of their own merge,
     *   such as subtracting two sums or, for exclusive-or, applying the identical
     *   operation once more.
     * - Assumptions: Must satisfy that removing a value from a merge containing
     *   it restores the other operand exactly, for every pair of values. Removing
     *   the neutral element must leave a value unchanged. An implementation whose
     *   inverse is only approximate, such as one over floating-point values,
     *   accumulates error with every operation and will eventually report range
     *   answers that drift from the true aggregate; callers needing exactness
     *   there are better served by a segment tree, which never subtracts.
     * - Side effects: None permitted, for the same reason as on combine.
     *
     * Time complexity: Determined entirely by the implementation, and assumed to
     * be O(1) by the figures documented on FenwickTree.
     * Space complexity: Likewise determined by the implementation, and expected
     * to be O(1) beyond the returned value.
     *
     * @param pMinuend
     * The aggregate to remove from, covering a superset of the positions covered
     * by pSubtrahend. Never null.
     *
     * @param pSubtrahend
     * The aggregate to remove. Never null.
     *
     * @return
     * The aggregate of the positions remaining after the removal. Must never be
     * null, since the tree hands the result straight to the caller as a range
     * answer and reserves null for a range it refuses to answer at all.
     */
    ContentType difference(ContentType pMinuend, ContentType pSubtrahend);

}
