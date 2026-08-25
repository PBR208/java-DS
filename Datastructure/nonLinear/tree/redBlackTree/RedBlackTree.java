package nonLinear.tree.redBlackTree;

import nonLinear.tree.base.ComparableContent;

/**
 * Purpose:
 * Implements a self-balancing binary search tree that keeps its height within a
 * logarithmic bound by colouring every node red or black and enforcing four
 * structural invariants after each mutation, rather than by tracking subtree
 * heights as the AvlTree in this package does. The colour invariants guarantee
 * that the longest root-to-leaf path is at most twice the length of the
 * shortest, which bounds the height at 2*log2(n+1) and therefore keeps search,
 * insertion and removal at O(log n) even for adversarial input orders such as
 * fully sorted insertion sequences. This file exists to make that alternative
 * balancing discipline available alongside the AVL variant, because the two
 * differ in a way that matters in practice: red-black trees perform fewer
 * rotations per update and are consequently preferred for write-heavy
 * workloads, while AVL trees stay more rigidly balanced and answer lookups
 * marginally faster.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Self-balancing binary search tree using red-black colour invariants, generic
 * over a bounded comparable content type.
 *
 * Responsibility: Encapsulates ordered storage, search, insertion and removal of
 * content values while continuously maintaining the five red-black properties:
 * every node is either red or black; the root is black; every leaf sentinel is
 * black; a red node never has a red child; and every path from a given node down
 * to any leaf sentinel below it contains the same number of black nodes. The
 * last of these is the property that actually bounds the height, and every
 * rotation and recolouring performed by this class exists to restore it.
 *
 * Scope: Used within the nonLinear.tree package as an alternative self-balancing
 * ordered container to AvlTree, wherever predictable logarithmic access is
 * required and updates are frequent relative to lookups.
 *
 * Dependencies: Depends on the ComparableContent interface to establish ordering
 * semantics (isLess, isGreater, isEqual) for the stored content type; relies
 * otherwise only on its own private nested Color enum and RBNode class.
 *
 * Thread-safety: This class is not thread-safe. A rotation temporarily leaves
 * parent and child references inconsistent with each other, so concurrent
 * mutation or even concurrent traversal during a mutation from multiple threads
 * without external synchronization may corrupt the tree structure or violate the
 * colour invariants.
 *
 * Lifecycle: A RedBlackTree instance begins empty and grows and shrinks
 * dynamically through repeated insert and remove operations, with each mutating
 * operation re-establishing all five colour invariants before returning.
 *
 * Architectural role: Serves as a foundational, generic, self-balancing
 * hierarchical data structure that may be consumed by higher-level algorithms
 * requiring guaranteed logarithmic-time ordered access, and as the reference
 * implementation against which the AVL variant can be compared.
 *
 * @param <ContentType>
 * The type of content stored at each node of the tree. Must implement
 * ComparableContent to provide the ordering comparisons required for binary
 * search tree placement.
 */
public class RedBlackTree<ContentType extends ComparableContent<ContentType>> {

    /**
     * Purpose:
     * Enumerates the two node colours that carry the balancing information of a
     * red-black tree. A dedicated type is used rather than a boolean flag so
     * that the balancing code reads as the textbook case analysis it implements
     * and so that neither colour can be confused with an unrelated boolean such
     * as a null check or a comparison result.
     *
     * Owner:
     * PBR208 - https://github.com/PBR208/
     *
     * Version:
     * 1.0
     */
    private enum Color {

        /**
         * Marks a node that does not contribute to the black height of any path
         * through it. Newly inserted nodes are red precisely because that
         * choice cannot violate the equal-black-count property, leaving at most
         * a red-red violation to repair.
         */
        RED,

        /**
         * Marks a node that contributes one to the black height of every path
         * through it. The root and every leaf sentinel are permanently black.
         */
        BLACK
    }

    /**
     * Purpose:
     * Represents a single position within the red-black tree, bundling the
     * stored content with child references, a parent reference and the node
     * colour. Unlike the AVLNode of the AvlTree in this package, this node
     * carries an explicit parent link, because the red-black repair procedures
     * examine a node's parent, grandparent and uncle and therefore have to move
     * upwards through the tree rather than only unwinding a recursion.
     *
     * Owner:
     * PBR208 - https://github.com/PBR208/
     *
     * Version:
     * 1.0
     */

    /**
     * Represents a single node of the red-black tree, holding content, child and
     * parent references, and a colour.
     *
     * Responsibility: Stores the content associated with this node, references
     * to its left child, right child and parent, and the colour that encodes its
     * contribution to the black height of the paths running through it.
     *
     * Scope: Private to the enclosing RedBlackTree class; not exposed outside
     * this file.
     *
     * Dependencies: None beyond the generic ContentType parameter of the
     * enclosing class and the enclosing Color enum.
     *
     * Thread-safety: Not thread-safe; mutation of any field from multiple
     * threads without external synchronization may result in inconsistent state.
     *
     * Lifecycle: An RBNode is created when a new value is inserted and is
     * discarded when the corresponding value is removed. The single shared
     * sentinel node is the exception: it is created once with the tree and lives
     * for as long as the tree does.
     *
     * Architectural role: Acts as the internal storage unit backing each
     * position within the red-black tree, including the leaf positions, which
     * are represented by the shared sentinel rather than by null.
     */
    private class RBNode {

        // Stores the content value held at this node. Used both as the data
        // payload and as the basis for ordering comparisons during search,
        // insertion and removal. Null for the shared sentinel node, which
        // represents a leaf position and therefore carries no payload.
        ContentType content;

        // References the left child, containing values considered "less than"
        // this node's content per ComparableContent semantics. Never null for a
        // node that is part of the tree: an absent child is represented by the
        // shared sentinel, which removes the null checks that would otherwise be
        // required at every step of the balancing case analysis.
        RBNode left;

        // References the right child, containing values considered "greater
        // than" this node's content. Never null, for the same reason as left.
        RBNode right;

        // References the parent node, or the shared sentinel when this node is
        // the root. This link is what allows the repair procedures to walk
        // upwards from the point of a violation towards the root; without it,
        // the red-black case analysis could not inspect the grandparent and
        // uncle of a node.
        RBNode parent;

        // Encodes this node's contribution to the black height of every path
        // running through it. The colour is the entire balancing state of the
        // structure: unlike the AVL variant, no height or balance factor is
        // stored, and the height bound follows purely from the colour
        // invariants.
        Color color;

        /**
         * Constructs a new node holding the specified content.
         *
         * Detailed explanation of:
         * - Purpose: Initializes a newly created tree position with its payload,
         *   leaving the structural links to be established by the caller.
         * - Business context: Represents the creation of a new data point within
         *   the ordered hierarchical structure, prior to the colour repair that
         *   the insertion procedure performs afterwards.
         * - Processing steps: Assigns the supplied content to the internal
         *   content field. The child, parent and colour fields are deliberately
         *   left at their default values and are set by the insertion procedure,
         *   which is the only code that knows where in the tree the node will be
         *   linked and therefore which sentinel and colour apply.
         * - Assumptions: Assumes the caller has already validated that pContent
         *   is non-null, except when the enclosing tree constructs its sentinel,
         *   which intentionally carries no content.
         * - Side effects: None beyond initialization of instance state.
         *
         * @param pContent
         * The content value to store at this node. Expected to be non-null for
         * every node that holds data, since validation is performed by the
         * calling insert method; null exclusively for the shared sentinel node.
         */
        RBNode(ContentType pContent) {
            // Assign the supplied content as this node's payload. All remaining
            // fields are established by the caller, because their correct values
            // depend on the node's eventual position in the tree.
            this.content = pContent;
        }
    }

    /**
     * The single shared sentinel node representing every leaf position, as well
     * as the parent of the root.
     *
     * Using one permanently black sentinel instead of null is the decisive
     * simplification of this implementation. The repair procedures routinely
     * read the colour of a node's uncle or of a sibling's children, and any of
     * those may be absent; with null those reads would each need a guard, and
     * the delete repair in particular would become substantially harder to
     * verify. With a sentinel, an absent node answers "black" like any other
     * black node, which is exactly the semantics the case analysis requires.
     *
     * The reference itself never changes for the lifetime of the tree, but the
     * sentinel's own parent field is deliberately written during removal: the
     * delete repair may start at the sentinel, and it needs a valid upward link
     * to walk from there.
     */
    private final RBNode nil;

    /**
     * References the root node of this tree, or the sentinel when the tree is
     * currently empty.
     *
     * This is the entry point for all descents. It is never null, which is what
     * allows every operation to treat "empty tree" as an ordinary sentinel
     * comparison rather than as a separate special case.
     */
    private RBNode root;

    /**
     * Number of content values currently stored in the tree, excluding the
     * sentinel.
     *
     * The counter is maintained incrementally by insert and remove so that the
     * element count is available without traversing the tree, and it is the
     * quantity against which the logarithmic height bound is stated.
     */
    private int size;

    /**
     * Constructs an empty red-black tree.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the sentinel-based representation that every other
     *   operation of this class relies upon, so that no operation ever has to
     *   distinguish an absent node from a present one by a null check.
     * - Business context: Produces a tree that is immediately usable and that
     *   already satisfies all five red-black properties vacuously, since a tree
     *   consisting only of the black sentinel trivially has equal black counts
     *   on all of its (zero) paths.
     * - Processing steps: Creates the sentinel with no content and colours it
     *   black, as required by the property that every leaf is black. Points the
     *   sentinel's own links at itself, so that reading the parent or a child of
     *   the sentinel yields a valid node rather than null even in the edge cases
     *   of the delete repair. Finally points the root at the sentinel, which is
     *   this implementation's representation of an empty tree.
     * - Assumptions: Assumes no caller ever mutates the content of the sentinel;
     *   it is private and never exposed, so this holds by construction.
     * - Side effects: Allocates the single sentinel node that will be shared by
     *   every leaf position of this tree instance for its entire lifetime.
     */
    public RedBlackTree() {
        // Create the shared leaf sentinel. It carries no payload, because it
        // represents the absence of a node rather than a stored value.
        nil = new RBNode(null);

        // The sentinel must be black: the red-black properties require every
        // leaf to be black, and the delete repair relies on being able to read
        // this colour without a special case.
        nil.color = Color.BLACK;

        // Point the sentinel's links at itself so that any accidental or
        // intentional dereference during the repair procedures yields a valid
        // black node instead of a null reference.
        nil.left = nil;
        nil.right = nil;
        nil.parent = nil;

        // An empty tree is represented by a root that refers to the sentinel,
        // which keeps the root reference non-null at all times.
        root = nil;

        // No content values are stored yet.
        size = 0;
    }
}
