package nonLinear.tree.segmentTree;

/**
 * Purpose:
 * Defines the merge contract that a segment tree relies on to fold the answers
 * of two adjacent ranges into the single answer covering both of them together.
 * A segment tree is deliberately not tied to one particular aggregate: the very
 * same structure answers range sums, range minima, range maxima, greatest common
 * divisors or bitwise unions, and the only thing separating those cases is how
 * two partial results are merged. Externalising that merge into this interface
 * keeps the tree itself free of any assumption about the quantity it maintains,
 * in the same spirit in which the ComparableContent contract keeps the search
 * trees of this package free of assumptions about ordering. An implementation
 * therefore carries the entire domain meaning of a tree instance, while the tree
 * contributes only the decomposition of a range into precomputed pieces and the
 * order in which those pieces are merged.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Strategy contract describing how two range results are merged into one.
 *
 * Responsibility: Supplies the single binary operation from which a SegmentTree
 * derives every stored aggregate, both when a tree is first built from an array
 * and whenever a point update forces the aggregates on one root-to-leaf path to
 * be recomputed.
 *
 * Scope: Implemented by callers of SegmentTree, one implementation per aggregate
 * they wish to query. Implementations are typically stateless and are safe to
 * share between several trees.
 *
 * Dependencies: None. The contract is expressed purely in terms of the content
 * type it merges, which keeps it usable for aggregates whose result type carries
 * no ordering and no arithmetic.
 *
 * Thread-safety: Implementations must be free of mutable state, or must
 * synchronize it themselves. A SegmentTree calls this method during ordinary
 * read operations, so an implementation that mutates shared state would make
 * even concurrent queries unsafe.
 *
 * Lifecycle: Supplied once at tree construction and retained for the lifetime of
 * that tree, because every stored aggregate was produced by it and replacing it
 * afterwards would invalidate all of them at once.
 *
 * Architectural role: Acts as the extension point of the segment tree, playing
 * the same part for range aggregation that ComparableContent plays for ordered
 * trees: the structure supplies the algorithm, the caller supplies the semantics.
 *
 * @param <ContentType>
 * The type of the values stored in the leaves and of the aggregates derived from
 * them. Both share one type, which means an aggregate must be expressible in the
 * same terms as an element; callers whose aggregate differs in shape from their
 * elements are expected to store a small carrier object in the leaves instead.
 */
public interface SegmentCombiner<ContentType> {

    /**
     * Merges the results of two adjacent ranges into the result of the range
     * that both of them together span.
     *
     * Detailed explanation of:
     * - Purpose: Produces the aggregate of a combined range from the aggregates
     *   of its two halves, without inspecting the individual elements those
     *   halves contain.
     * - Business context: This is the operation that makes a segment tree
     *   cheaper than a scan. Because the answer for a range can be assembled
     *   from precomputed answers for its parts, a query never has to visit the
     *   elements themselves, and an update never has to recompute more than the
     *   aggregates on one path.
     * - Processing steps: Implementations perform exactly one domain-specific
     *   operation, such as adding two sums, taking the smaller of two minima, or
     *   concatenating two strings.
     * - Assumptions: The operation must be associative, meaning that merging
     *   three or more results must give the same answer regardless of how they
     *   are grouped. The tree decides the grouping itself from the shape of the
     *   range it decomposes, and that shape shifts as the range moves, so a
     *   non-associative operation would return different answers for ranges the
     *   caller considers equivalent. Commutativity is explicitly NOT required:
     *   the tree always passes the result of the range lying further left as the
     *   first argument, which keeps order-sensitive merges such as string
     *   concatenation or matrix multiplication valid. The operation must further
     *   be deterministic and free of side effects, since the tree may invoke it
     *   during a read.
     * - Side effects: None permitted. The tree caches the returned values and
     *   would silently serve stale answers if an implementation reported results
     *   that depend on anything other than its two arguments.
     *
     * Time complexity: Determined entirely by the implementation. Every
     * complexity figure documented on SegmentTree assumes a merge in O(1), which
     * holds for the usual arithmetic and comparison aggregates; an implementation
     * whose merge is more expensive multiplies those figures by its own cost, so
     * an aggregate such as string concatenation, whose cost grows with the length
     * of its operands, changes the bounds of the tree it is used with.
     * Space complexity: Likewise determined by the implementation, and expected
     * to be O(1) beyond the returned value.
     *
     * @param pLeft
     * Aggregate of the range positioned further to the left. Never null, because
     * a segment tree neither stores nor propagates null aggregates, and it skips
     * the merge entirely when only one side contributes to a range.
     *
     * @param pRight
     * Aggregate of the range positioned immediately to the right of pLeft, with
     * no gap between the two. Never null, for the same reason as pLeft.
     *
     * @return
     * The aggregate of the two ranges taken together. Must never be null: a null
     * result is used inside the tree to mean that a range contributes nothing at
     * all, so returning it here would erase a genuine aggregate and corrupt every
     * query that later reads the affected node.
     */
    ContentType combine(ContentType pLeft, ContentType pRight);

}
