package nonLinear.disjointSet;

/**
 * Purpose:
 * Implements a disjoint set, also called a union-find structure, which keeps a
 * fixed universe of elements divided into groups that never overlap and answers
 * the two questions such a division raises: whether two elements currently belong
 * to the same group, and what the division looks like once two groups are merged.
 * The structure exists because the obvious representations answer only one of
 * those questions cheaply, an explicit member list per group making membership
 * trivial but every merge linear, and a group label stored per element making the
 * membership test trivial but every merge a sweep over the whole universe. The
 * forest of parent references used here answers both in effectively constant
 * time: each group is represented by one of its own elements, found by walking
 * parent references upwards, so a merge is a single reference write regardless of
 * how large the two groups are. Two heuristics keep those upward walks short,
 * namely that the shallower tree is always hung beneath the deeper one and that
 * every walk flattens the path it has just travelled, so a deep lookup is paid
 * for at most once. It is the structure underneath cycle detection in undirected
 * graphs, connected component labelling and the minimum spanning tree algorithm
 * of Kruskal, each of which is little more than a long sequence of these two
 * operations.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Array-backed disjoint set over a fixed universe of elements identified by
 * zero-based indices, maintaining the partition of that universe under merging.
 *
 * Responsibility: Encapsulates the forest of parent references that encodes the
 * partition, the lookup of the representative element standing for a group, the
 * merging of two groups into one, and the bookkeeping that keeps both heuristics
 * effective, namely the rank recorded per representative and the element count
 * recorded per group. It maintains the invariant that every element reaches
 * exactly one representative by following parent references, that a
 * representative is the only element referencing itself, and that the rank and
 * the size recorded for a representative describe the whole group it stands for.
 *
 * Scope: Used wherever a partition has to be maintained under merging and never
 * under splitting. The structure is deliberately one-directional: groups grow by
 * merging and are never taken apart again, because undoing a merge would require
 * the history that the flattening of the paths destroys. Callers needing to
 * remove elements from groups are served by a different structure entirely.
 *
 * Dependencies: None beyond three plain integer arrays. In particular this class
 * does not implement the ComparableContent-based contract of the search trees of
 * this library and stores no content at all, because it never inspects, compares
 * or returns the elements themselves, only their membership. Elements are
 * therefore identified by index rather than by value, and callers holding objects
 * map them to indices once, typically by the position an element already occupies
 * in the array or list it was enumerated from.
 *
 * Thread-safety: This class is not thread-safe, and it is worth noting that not
 * even concurrent lookups are safe here, unlike in the read-only structures of
 * this library. A lookup rewrites the parent references along the path it walks,
 * so two threads reading at once are two threads writing at once. External
 * synchronization is required whenever an instance is shared across threads.
 *
 * Lifecycle: A disjoint set is created over a fixed number of elements and keeps
 * that number for its entire lifetime, since an element is addressed by its index
 * into arrays sized once at construction. It begins fully divided, with every
 * element alone in a group of its own, and every merge reduces the number of
 * groups by exactly one, so no more than one fewer merge than there are elements
 * can ever have an effect. Callers needing a larger universe build a new
 * instance.
 *
 * Architectural role: Serves as the membership counterpart to the graph
 * structures of this library, which describe how vertices are connected but not
 * which of them form a connected whole. It is also the clearest example in this
 * repository of a structure whose shape carries no meaning: the trees of the
 * forest are neither ordered nor balanced in the sense the search trees use, they
 * are never traversed downwards, and they are deliberately flattened towards a
 * single level, because the only thing ever asked of them is which representative
 * sits at the top.
 */
public class DisjointSet {

    /**
     * Result reported when a lookup is given an element the universe does not
     * contain.
     *
     * Every valid element is a zero-based index, so no genuine representative can
     * ever be negative and this value is unambiguous as a marker. It follows the
     * convention of the read operations elsewhere in this library, which report an
     * unanswerable request by returning a value that cannot otherwise occur rather
     * than by raising an exception, and it keeps a caller iterating over possibly
     * out-of-range identifiers free of exception handling.
     */
    public static final int NO_REPRESENTATIVE = -1;

    /**
     * Parent reference of each element, indexed by the element itself.
     *
     * An element representing its own group references itself, which is what
     * terminates every upward walk without a separate marker being needed. Every
     * other element references an element nearer to the representative, though not
     * necessarily the one it was originally attached to: lookups rewrite these
     * references to point straight at the representative, which is the entire
     * purpose of the flattening. The array reference is final, but its contents
     * change continuously, including during operations that merely read the
     * partition.
     */
    private final int[] parents;

    /**
     * Upper bound on the height of the tree rooted at each element.
     *
     * Meaningful only for an element currently representing a group; the entry of
     * any other element is stale and is never read, because the merge consults
     * this array exclusively for representatives. The value is an upper bound
     * rather than the exact height because the flattening performed by a lookup
     * shortens trees without the ranks being corrected: correcting them would
     * require knowing the heights of the remaining branches, which is precisely
     * the information the flattening does not gather. An upper bound is sufficient
     * here, since the value is used only to decide which of two trees is hung
     * beneath the other, and an overestimate cannot make that decision harmful.
     */
    private final int[] ranks;

    /**
     * Number of elements in the group rooted at each element.
     *
     * Meaningful only for an element currently representing a group, for the same
     * reason as the ranks: when a group is absorbed into another, its former
     * representative keeps a count describing a group that no longer exists, and
     * that count is never read again. Maintained separately from the rank because
     * the two answer different questions, the rank being about the shape of a tree
     * and existing purely to keep the structure fast, while the size is about the
     * partition itself and is what callers actually ask for, for instance when
     * looking for the largest connected component of a graph.
     */
    private final int[] setSizes;

    /**
     * Number of groups the universe is currently divided into.
     *
     * Starts equal to the number of elements, since every element begins alone,
     * and drops by exactly one with every merge joining two genuinely different
     * groups. Held as a field rather than derived on demand, because deriving it
     * would mean counting the self-referencing elements, which is linear work for
     * a value that changes by a known amount at a known moment.
     */
    private int disjointSetCount;

    /**
     * Constructs a disjoint set over the given number of elements, each alone in a
     * group of its own.
     *
     * Detailed explanation of:
     * - Purpose: Produces the fully divided starting partition from which every
     *   later state is reached by merging.
     * - Business context: This is the only starting state the structure offers,
     *   and the one every application of it begins from. An algorithm labelling
     *   the connected components of a graph starts with every vertex isolated and
     *   merges along the edges it walks; the minimum spanning tree algorithm of
     *   Kruskal does the same and uses the merge itself to decide whether an edge
     *   would close a cycle. Offering no other starting state is deliberate,
     *   because every partition is reachable from this one by merges alone.
     * - Processing steps:
     *   1. Reject a negative element count.
     *   2. Allocate the three parallel arrays describing the forest.
     *   3. Make every element reference itself, which marks it as the
     *      representative of the group containing only itself, and record that
     *      group as having height zero and exactly one member.
     * - Assumptions: Assumes the caller has already mapped whatever objects it
     *   works with onto the index range zero to pSize minus one, since the
     *   structure identifies elements by nothing else.
     * - Side effects: Allocates and populates three integer arrays.
     *
     * Time complexity: O(n) in the number of elements; each of the three arrays is
     * written once per element.
     * Space complexity: O(n); three integer entries per element and no per-group
     * allocation at all, since a group is described entirely by the entries of the
     * element representing it.
     *
     * @param pSize
     * Number of elements the structure covers, which are addressed afterwards by
     * the indices zero to pSize minus one. Must not be negative. Zero is accepted
     * and yields a universe containing nothing, so that callers deriving the count
     * from an empty data set need no special case.
     *
     * @throws IllegalArgumentException
     * Thrown when pSize is negative, which describes no universe at all and would
     * otherwise fail during array allocation with an exception naming the symptom
     * rather than the cause. Reporting it at construction is far cheaper than
     * discovering it during a later lookup, since a structure is built once and
     * queried many times.
     */
    public DisjointSet(int pSize) {
        // A negative universe is not merely empty but contradictory, and accepting
        // it would leave the caller holding an instance that silently rejects
        // every element it is later given.
        if (pSize < 0) {
            throw new IllegalArgumentException("The number of elements must not be negative.");
        }

        this.parents = new int[pSize];
        this.ranks = new int[pSize];
        this.setSizes = new int[pSize];

        /*
         * Every element begins as the representative of the group containing only
         * itself. The self-reference is what terminates the upward walk of a
         * lookup, so this loop establishes the invariant the whole structure rests
         * on rather than merely initialising memory.
         */
        for (int element = 0; element < pSize; element++) {
            parents[element] = element;

            // A single element forms a tree of height zero holding exactly one
            // member. The ranks array already holds zero after allocation, but
            // stating it here keeps the three arrays visibly in agreement about
            // what a singleton group looks like.
            ranks[element] = 0;
            setSizes[element] = 1;
        }

        // Nothing has been merged yet, so there are exactly as many groups as
        // there are elements.
        this.disjointSetCount = pSize;
    }

    /**
     * Reports whether the given identifier addresses an element of this universe.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single place in which the validity of an element
     *   identifier is decided.
     * - Business context: Element identifiers reach this structure from outside,
     *   commonly as vertex numbers or as positions in a caller's own array, and an
     *   identifier that has drifted out of range must never be allowed to index
     *   the internal arrays. Concentrating the decision here keeps the public
     *   operations free of repeated bounds arithmetic and guarantees that all of
     *   them agree on what an element is, which matters because they report an
     *   invalid element in different ways.
     * - Processing steps: Compares the identifier against the two ends of the
     *   valid range.
     * - Assumptions: Assumes the parents array is the authority on the size of the
     *   universe, which holds because all three arrays are allocated together and
     *   never resized.
     * - Side effects: None; this method only reads the array length.
     *
     * Time complexity: O(1); two comparisons.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pElement
     * Identifier to check. Any integer is accepted, including negative values and
     * values beyond the universe, since deciding exactly those cases is the
     * purpose of this method.
     *
     * @return
     * True when the identifier addresses an element of this universe and may
     * therefore be used to index the internal arrays; false otherwise, which for a
     * universe of no elements is the answer for every identifier.
     */
    private boolean isElement(int pElement) {
        return pElement >= 0 && pElement < parents.length;
    }

    /**
     * Determines which element currently represents the group containing the
     * given element, flattening the path travelled on the way.
     *
     * Detailed explanation of:
     * - Purpose: Turns an element into the single identifier shared by every
     *   member of its group, which is what makes any two members recognisable as
     *   belonging together.
     * - Business context: This is the read half of the structure and the operation
     *   every other one is built from, since a membership test compares two
     *   representatives and a merge joins them. The representative itself carries
     *   no meaning beyond identity: it is simply whichever member happens to sit at
     *   the top of the tree, it may change when the group is merged into another,
     *   and callers must therefore treat it as a label valid only until the next
     *   merge rather than as a property of the group.
     * - Processing steps:
     *   1. Reject an identifier outside the universe.
     *   2. Walk parent references upwards until reaching the element referencing
     *      itself, which is the representative.
     *   3. Walk the same path a second time and point every element on it directly
     *      at that representative.
     * - Assumptions: Assumes the parent references form a forest rather than a
     *   cycle, which holds because the only operation writing a parent reference
     *   attaches one representative to another and a representative referenced
     *   itself until that moment. A cycle would make the first walk non-
     *   terminating, so this assumption is load-bearing rather than cosmetic.
     * - Side effects: Rewrites the parent reference of every element on the path
     *   except the representative and the elements already pointing at it. The
     *   partition itself is left untouched, which is why this remains a read
     *   operation despite the writing, but it also means that even a caller doing
     *   nothing but querying mutates the structure, with the consequences for
     *   concurrency stated on this class.
     *
     * The second walk is what keeps the structure fast over a long sequence of
     * operations. Without it, the trees built by repeated merging would be walked
     * from the bottom again and again, and each of those walks would cost what the
     * height of the tree costs. With it, the cost of a deep path is paid once and
     * every element on that path is one step from its representative afterwards,
     * so the work is not merely repeated less often but permanently removed. The
     * flattening is performed as a separate walk rather than during the first one
     * because the destination is not known until the first walk has finished.
     *
     * Time complexity: O(alpha(n)) amortised over a sequence of operations, where
     * alpha is the inverse Ackermann function, which is at most four for any
     * universe that fits in memory and is therefore constant for practical
     * purposes. This bound holds only in combination with the union by rank
     * performed by the merge: either heuristic alone leaves a logarithmic bound.
     * A single call in isolation is O(log n) in the worst case, since it may be the
     * one paying for a path nobody has flattened yet.
     * Space complexity: O(1); both walks are iterative, which also removes the
     * stack depth that the recursive formulation of this operation would need over
     * a tall tree.
     *
     * @param pElement
     * The element whose group is to be identified. Must be non-negative and less
     * than the number of covered elements.
     *
     * @return
     * The element currently representing the group containing pElement, which for
     * an element still alone in its group is that element itself, or
     * NO_REPRESENTATIVE when the identifier lies outside the universe, including
     * every identifier handed to a structure covering no elements.
     */
    public int find(int pElement) {
        // An identifier outside the universe belongs to no group, and answering it
        // with a marker rather than an exception matches the tolerant read
        // behaviour of the other structures in this library.
        if (!isElement(pElement)) {
            return NO_REPRESENTATIVE;
        }

        /*
         * First walk: climb to the representative. The self-reference established
         * at construction, and preserved by the merge for every element that keeps
         * representing its group, is what stops this loop.
         */
        int representative = pElement;
        while (parents[representative] != representative) {
            representative = parents[representative];
        }

        /*
         * Second walk: retrace the same path and attach every element on it
         * directly to the representative, so that none of them will ever be walked
         * through again. The loop stops as soon as it reaches an element already
         * pointing at the representative, which covers both the representative
         * itself and the elements attached during an earlier flattening.
         */
        int pathElement = pElement;
        while (parents[pathElement] != representative) {
            // The original parent must be read before it is overwritten, since it
            // is the only remaining way to continue up the path.
            int nextOnPath = parents[pathElement];
            parents[pathElement] = representative;
            pathElement = nextOnPath;
        }

        return representative;
    }

    /**
     * Merges the groups containing the two given elements into a single group.
     *
     * Detailed explanation of:
     * - Purpose: Replaces two groups of the partition by their union, leaving
     *   every other group untouched.
     * - Business context: This is the write half of the structure and the reason
     *   it is preferred over recolouring every member of one group, which is what
     *   a label-per-element representation would have to do. The return value is
     *   as important as the merge itself and is what makes the operation directly
     *   usable as a decision: an edge of an undirected graph closes a cycle
     *   exactly when its two endpoints already share a group, so an algorithm
     *   detecting cycles, or the minimum spanning tree algorithm of Kruskal
     *   accepting or rejecting an edge, needs nothing beyond the answer to this
     *   one call.
     * - Processing steps:
     *   1. Look up both representatives, which also rejects identifiers outside
     *      the universe and flattens both paths.
     *   2. Report that nothing happened when the two elements already share a
     *      group.
     *   3. Hang the representative of the shallower tree beneath the
     *      representative of the deeper one.
     *   4. Carry the member count over to the surviving representative, raise its
     *      rank when both trees were equally deep, and record that one group fewer
     *      remains.
     * - Assumptions: Assumes that the ranks of both representatives are upper
     *   bounds on the heights of their trees, which is what the maintenance in
     *   step four preserves.
     * - Side effects: Rewrites the parent reference of one representative, updates
     *   the size and possibly the rank of the other, decrements the group count,
     *   and, through the two lookups, flattens the paths of both arguments. The
     *   representative of the absorbed group stops representing anything, so any
     *   representative a caller has held on to since an earlier call may have
     *   become stale.
     *
     * The choice of which tree is hung beneath the other is the second of the two
     * heuristics this structure depends on. Attaching the shallower tree to the
     * deeper one leaves the height of the result unchanged, whereas the opposite
     * choice would raise it, and only the case of two equally deep trees can
     * force the height up at all. That is what bounds the height logarithmically
     * even before any flattening, because a tree of a given rank cannot be built
     * from fewer than an exponential number of elements. Ranks rather than member
     * counts are compared here because it is the height that a lookup pays for,
     * and the two orderings can genuinely disagree once flattening has made a
     * populous group shallow.
     *
     * Time complexity: O(alpha(n)) amortised, dominated entirely by the two
     * lookups; the merge itself is a fixed number of array writes.
     * Space complexity: O(1); nothing is allocated, and in particular the members
     * of the absorbed group are never enumerated, which is the whole point of the
     * representation.
     *
     * @param pFirst
     * An element of the first group to merge. Must be non-negative and less than
     * the number of covered elements.
     *
     * @param pSecond
     * An element of the second group to merge. Must satisfy the same constraints
     * as pFirst. May address the same group as pFirst, in which case the call is
     * without effect.
     *
     * @return
     * True when two genuinely different groups were merged and the number of
     * groups consequently dropped by one; false when both elements already shared
     * a group, and equally false when either identifier lies outside the universe,
     * since neither case changes the partition. A caller needing to tell those two
     * reasons apart validates the identifiers beforehand, for instance against
     * size.
     */
    public boolean union(int pFirst, int pSecond) {
        // Resolve both groups first. This also validates both identifiers, since a
        // lookup answers an invalid one with the marker.
        int firstRepresentative = find(pFirst);
        int secondRepresentative = find(pSecond);

        // An element outside the universe belongs to no group, and there is
        // consequently no group to merge.
        if (firstRepresentative == NO_REPRESENTATIVE || secondRepresentative == NO_REPRESENTATIVE) {
            return false;
        }

        /*
         * Both elements already share a group. Reporting this without touching
         * anything is what lets callers use the return value as a cycle test, and
         * it is also a correctness requirement: attaching a representative to
         * itself would create the self-reference of a group while leaving the
         * counts describing two.
         */
        if (firstRepresentative == secondRepresentative) {
            return false;
        }

        /*
         * Decide which tree survives as the root of the merged group. The deeper
         * tree is kept on top so that the elements of the shallower one gain a
         * single step of depth at most, and the height of the result stays that of
         * the deeper tree.
         */
        int survivingRoot = firstRepresentative;
        int absorbedRoot = secondRepresentative;
        if (ranks[firstRepresentative] < ranks[secondRepresentative]) {
            survivingRoot = secondRepresentative;
            absorbedRoot = firstRepresentative;
        }

        // The single write that performs the merge. Every member of the absorbed
        // group now reaches the surviving representative, without any of them
        // being visited.
        parents[absorbedRoot] = survivingRoot;

        // The merged group holds the members of both. The count left behind at the
        // absorbed representative is now stale and is deliberately not cleared,
        // since it is never read again and clearing it would suggest that it
        // carried meaning.
        setSizes[survivingRoot] = setSizes[survivingRoot] + setSizes[absorbedRoot];

        /*
         * Only two equally deep trees can produce a deeper one: the absorbed root
         * moves one level down and, having been as deep as the surviving tree,
         * now reaches one level further than it did. In every other case the
         * shallower tree still ends within the depth the surviving tree already
         * had, and its rank needs no correction.
         */
        if (ranks[survivingRoot] == ranks[absorbedRoot]) {
            ranks[survivingRoot] = ranks[survivingRoot] + 1;
        }

        // Two groups became one, which is the only way this count ever changes.
        disjointSetCount = disjointSetCount - 1;

        return true;
    }

    /**
     * Reports whether the two given elements currently belong to the same group.
     *
     * Detailed explanation of:
     * - Purpose: Answers the membership question the structure exists for, without
     *   exposing the representative that the answer is derived from.
     * - Business context: Callers are almost always interested in whether two
     *   things are connected rather than in which label they share, and comparing
     *   representatives themselves is easy to get wrong once a merge has made an
     *   earlier one stale. Offering the comparison here keeps that trap out of
     *   calling code. Typical use is asking whether two vertices lie in the same
     *   connected component after a set of edges has been merged in.
     * - Processing steps: Looks both representatives up and compares them, having
     *   first ruled out the case in which neither element exists.
     * - Assumptions: Assumes nothing beyond the invariants of the structure.
     * - Side effects: None on the partition, but both lookups flatten the paths
     *   they walk, so this operation writes to the parent references despite
     *   reading only.
     *
     * Time complexity: O(alpha(n)) amortised; two lookups and one comparison.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pFirst
     * The first element to compare. Must be non-negative and less than the number
     * of covered elements.
     *
     * @param pSecond
     * The second element to compare. Must satisfy the same constraints as pFirst.
     * May be the same element as pFirst, which trivially shares its own group.
     *
     * @return
     * True when both elements belong to the same group; false when they belong to
     * different groups, and equally false when either identifier lies outside the
     * universe, because an element that does not exist shares a group with
     * nothing, not even with another identifier that does not exist.
     */
    public boolean connected(int pFirst, int pSecond) {
        int firstRepresentative = find(pFirst);
        int secondRepresentative = find(pSecond);

        /*
         * Rule out the non-existent elements before comparing. Without this test
         * two identifiers outside the universe would both answer with the marker
         * and would consequently be reported as sharing a group that neither of
         * them is a member of.
         */
        if (firstRepresentative == NO_REPRESENTATIVE || secondRepresentative == NO_REPRESENTATIVE) {
            return false;
        }

        // Two elements share a group exactly when the same element represents
        // both of them, which is the defining property of the representative.
        return firstRepresentative == secondRepresentative;
    }

    /**
     * Reports how many elements share a group with the given element.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the size of one part of the partition, counted including
     *   the element the question was asked about.
     * - Business context: The size of a group is the property callers ask for once
     *   the merging is done, typically to find the largest connected component of
     *   a graph, to check whether a graph has become connected as a whole, or to
     *   weight a component in a later decision. It is answered from a counter
     *   maintained during merging rather than by counting members, because the
     *   representation never holds the members of a group in any enumerable form.
     * - Processing steps: Looks the representative up and reads the count recorded
     *   for it, having first ruled out an element outside the universe.
     * - Assumptions: Assumes the count recorded for a representative describes its
     *   whole group, which the merge maintains by carrying the count of an
     *   absorbed group over to the surviving representative.
     * - Side effects: None on the partition, though the lookup flattens the path
     *   it walks.
     *
     * Time complexity: O(alpha(n)) amortised, dominated by the lookup; the count
     * itself is a single array read.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pElement
     * The element whose group is to be measured. Must be non-negative and less
     * than the number of covered elements.
     *
     * @return
     * The number of elements in the group containing pElement, which is at least
     * one for any element of the universe, since an element is always a member of
     * its own group, or zero when the identifier lies outside the universe. Zero
     * is unambiguous as that marker precisely because no real group can be empty.
     */
    public int sizeOf(int pElement) {
        int representative = find(pElement);

        // An element outside the universe is in no group, and a group of no
        // elements is the one answer a genuine group can never give.
        if (representative == NO_REPRESENTATIVE) {
            return 0;
        }

        // Only the representative carries a count describing the whole group; the
        // counts of absorbed representatives are stale by design.
        return setSizes[representative];
    }

    /**
     * Reports how many groups the universe is currently divided into.
     *
     * Detailed explanation of:
     * - Purpose: Describes the partition as a whole rather than one part of it.
     * - Business context: This is the value that turns the structure into a
     *   connectivity test for an entire graph: after every edge has been merged
     *   in, the graph is connected exactly when a single group remains, and the
     *   count is otherwise the number of connected components. Watching it drop is
     *   also how the minimum spanning tree algorithm of Kruskal knows it may stop
     *   early, since a spanning tree is complete as soon as one group is left.
     * - Processing steps: Returns the count maintained by the merge, which is
     *   raised to the number of elements at construction and lowered by one for
     *   every merge joining two genuinely different groups.
     * - Assumptions: Assumes the merge is the only operation changing the
     *   partition, which holds because the structure offers no way to separate
     *   groups again.
     * - Side effects: None; this operation only reads a field.
     *
     * Time complexity: O(1); the count is held as a field rather than derived by
     * scanning for self-referencing elements.
     * Space complexity: O(1).
     *
     * @return
     * The number of groups, which equals the number of elements before anything
     * has been merged, is at least one for a non-empty universe, and is zero
     * exactly when the universe holds no elements at all.
     */
    public int setCount() {
        return disjointSetCount;
    }

    /**
     * Reports how many elements the universe covers.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the fixed size of the universe, which bounds every
     *   identifier the operations accept.
     * - Business context: Callers commonly iterate over all elements, for instance
     *   to group them by representative for reporting, and need this bound to do
     *   so. It also lets a caller distinguish the two reasons a merge can report
     *   that nothing happened, by validating the identifiers against it
     *   beforehand. Note that this counts elements and not groups, the two being
     *   equal only until the first merge takes effect.
     * - Processing steps: Reports the length of the parent array, which was fixed
     *   at construction and cannot change afterwards.
     * - Assumptions: None.
     * - Side effects: None; this operation only reads an array length.
     *
     * Time complexity: O(1).
     * Space complexity: O(1).
     *
     * @return
     * The number of covered elements, never negative, and zero exactly when the
     * structure was constructed over an empty universe.
     */
    public int size() {
        return parents.length;
    }

    /**
     * Reports whether the universe contains no elements at all.
     *
     * Detailed explanation of:
     * - Purpose: Lets callers distinguish a structure that can answer questions
     *   from one that cannot, without interpreting the markers the individual
     *   operations return.
     * - Business context: An empty universe arises only from construction over a
     *   size of zero and stays empty for the whole lifetime of the instance, since
     *   there is no operation adding elements. Checking it once is therefore more
     *   meaningful than checking every answer against a marker. Note that this
     *   reports the absence of elements and not the absence of merges: a structure
     *   over ten elements that have never been merged is not empty in this sense,
     *   because all ten elements exist and are each a group of their own.
     * - Processing steps: Compares the number of covered elements against zero.
     * - Assumptions: None.
     * - Side effects: None; this operation only reads an array length.
     *
     * Time complexity: O(1); a single comparison.
     * Space complexity: O(1).
     *
     * @return
     * True when no element is covered and every operation is consequently without
     * effect; false when at least one element exists.
     */
    public boolean isEmpty() {
        return parents.length == 0;
    }

}
