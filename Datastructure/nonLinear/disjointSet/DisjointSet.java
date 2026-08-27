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

}
