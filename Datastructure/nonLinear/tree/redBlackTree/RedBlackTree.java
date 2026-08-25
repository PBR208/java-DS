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

    /**
     * Determines whether this tree currently contains no elements.
     *
     * Detailed explanation of:
     * - Purpose: Provides a way to check whether the tree holds any content
     *   without requiring the caller to know that emptiness is represented by a
     *   root pointing at the sentinel rather than by a null root.
     * - Business context: Used by traversal and query methods, and by callers
     *   draining the tree, to guard against operating on an empty structure.
     * - Processing steps: Compares the root reference against the sentinel.
     * - Assumptions: Assumes the root field is never null, which the constructor
     *   establishes and which every mutating operation preserves.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if this tree currently holds no elements; false if at least one
     * content value is present.
     */
    public boolean isEmpty() {
        // Emptiness is represented by the root referring to the sentinel, which
        // is why this is a sentinel comparison rather than a null check.
        return root == nil;
    }

    /**
     * Returns the number of content values currently stored in this tree.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the element count without requiring a traversal, and
     *   provides the quantity against which this structure's logarithmic height
     *   guarantee is expressed.
     * - Business context: Callers use the count to report occupancy, to size
     *   output buffers before an in-order traversal, or to verify that the
     *   height bound of 2*log2(n+1) actually holds for the stored population.
     * - Processing steps: Returns the incrementally maintained counter.
     * - Assumptions: Assumes insert and remove keep the counter in step with the
     *   actual node population, which they do by adjusting it only on the paths
     *   that genuinely add or detach a node.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * Element count, zero for an empty tree and never negative. The sentinel is
     * not counted, since it represents the absence of a node rather than a
     * stored value.
     */
    public int size() {
        // The counter is the single source of truth for the element count.
        return size;
    }

    /**
     * Searches the tree for a value considered equal to the supplied content and
     * returns the stored instance.
     *
     * Detailed explanation of:
     * - Purpose: Retrieves the instance actually held by the tree, which may be
     *   a different object than the one supplied as the search key while still
     *   comparing equal under the ContentType ordering. This matters whenever
     *   the content carries a payload beyond the fields used for ordering, which
     *   is the normal case for a keyed record.
     * - Business context: Serves as the primary read entry point of the
     *   structure; the whole balancing machinery exists to keep this operation
     *   logarithmic regardless of insertion order.
     * - Processing steps: Delegates to the private descent helper and translates
     *   its sentinel result into null, so that the public contract can be
     *   expressed without reference to the internal representation.
     * - Assumptions: Assumes the ordering comparisons of ContentType form a
     *   consistent total order; if they do not, the descent may take a wrong
     *   branch and fail to find a value that is in fact stored.
     * - Side effects: None; searching does not modify the tree.
     *
     * Time complexity: O(log n), because the colour invariants bound the height
     * at 2*log2(n+1) and the descent visits at most one node per level.
     * Space complexity: O(1); the descent is iterative and allocates nothing.
     *
     * @param pContent
     * The search key. Must not be null; a null key is answered with null rather
     * than being compared, since the ordering methods cannot be invoked on it.
     * Only the fields participating in the ordering comparisons are relevant.
     *
     * @return
     * The content instance stored in this tree that compares equal to pContent,
     * or null when no such value is present or when pContent itself is null.
     */
    public ContentType search(ContentType pContent) {
        // A null key cannot be compared against stored content, so report "not
        // found" rather than raising an exception, consistent with the way the
        // mutating operations tolerate null.
        if (pContent == null) {
            return null;
        }

        // Locate the node holding an equal value, if any.
        RBNode foundNode = searchNode(pContent);

        // Translate the internal sentinel result into the null that the public
        // contract specifies for an unsuccessful search.
        if (foundNode == nil) {
            return null;
        }

        // Return the stored instance rather than the supplied key, since the two
        // may compare equal while differing in their non-ordering fields.
        return foundNode.content;
    }

    /**
     * Locates the node holding a value equal to the supplied content.
     *
     * Detailed explanation of:
     * - Purpose: Implements the shared binary search descent used by both the
     *   public search method and the removal procedure, which needs the node
     *   itself rather than the value it carries.
     * - Business context: Concentrates the ordering-driven navigation in one
     *   place, so that a change to the comparison semantics cannot leave search
     *   and removal disagreeing about where a value lives.
     * - Processing steps: Starts at the root and repeatedly compares the key
     *   against the current node, descending left when the key is smaller and
     *   right when it is larger, until either an equal value is found or the
     *   descent reaches the sentinel.
     * - Assumptions: Assumes isLess and isGreater are mutually exclusive and,
     *   together with the equality case, form a total ordering consistent with
     *   the one that governed insertion.
     * - Side effects: None; this method only reads the structure.
     *
     * @param pContent
     * The search key. Must not be null; the public entry points filter null out
     * before delegating here.
     *
     * @return
     * The node whose content compares equal to pContent, or the sentinel when no
     * such node exists. The sentinel is returned rather than null so that callers
     * can compare against it without a null check.
     */
    private RBNode searchNode(ContentType pContent) {
        // Begin the descent at the root; for an empty tree this is already the
        // sentinel and the loop below terminates immediately.
        RBNode currentNode = root;

        // Descend until the value is found or the search falls off a leaf.
        while (currentNode != nil) {

            if (pContent.isLess(currentNode.content)) {
                // The key precedes the current node, so it can only be stored in
                // the left subtree.
                currentNode = currentNode.left;
            } else if (pContent.isGreater(currentNode.content)) {
                // The key follows the current node, so it can only be stored in
                // the right subtree.
                currentNode = currentNode.right;
            } else {
                // Neither less nor greater means the ordering considers the two
                // values equal, which is the definition of a match here.
                return currentNode;
            }
        }

        // The descent reached a leaf position without finding an equal value.
        return nil;
    }

    /**
     * Rotates the subtree rooted at the specified node to the left, promoting
     * its right child into its position.
     *
     * Detailed explanation of:
     * - Purpose: Provides one of the two structural primitives from which all
     *   red-black repairs are composed. A rotation changes the shape of the tree
     *   while leaving the in-order sequence of the stored values untouched,
     *   which is what makes it safe to use for rebalancing a search tree.
     * - Business context: Rotations are the only operations in this class that
     *   move nodes relative to one another. Recolouring alone can fix some
     *   violations, but any violation that requires shortening a path can only
     *   be repaired by rotating.
     * - Processing steps:
     *   1. Take the right child of the pivot; it will become the new subtree
     *      root.
     *   2. Move that child's left subtree across to become the pivot's new right
     *      subtree, which is the step that preserves the ordering: those values
     *      lie between the pivot and its right child, so they belong to the
     *      right of the pivot and to the left of the promoted node.
     *   3. Reattach the promoted node to the pivot's former parent, handling the
     *      case where the pivot was the root.
     *   4. Finally place the pivot as the left child of the promoted node.
     *   Every one of these link updates is performed in both directions, because
     *   the repair procedures navigate upwards as well as downwards.
     * - Assumptions: Assumes the right child of the pivot is not the sentinel,
     *   which every call site guarantees; rotating a sentinel into the subtree
     *   root position would discard the pivot's subtree.
     * - Side effects: Mutates the parent and child references of up to three
     *   nodes and possibly the root reference of the tree. Colours are
     *   deliberately left untouched: the calling repair procedure decides them,
     *   because the correct colouring depends on which case it is handling.
     *
     * Time complexity: O(1); a fixed number of reference assignments regardless
     * of subtree size.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pPivot
     * The node to rotate downwards into the left position. Must be a node of
     * this tree and must have a right child other than the sentinel.
     */
    private void rotateLeft(RBNode pPivot) {
        // The right child is promoted into the position currently held by the
        // pivot.
        RBNode promoted = pPivot.right;

        // Step 1: the promoted node's left subtree holds exactly those values
        // that are greater than the pivot but less than the promoted node, so it
        // becomes the pivot's new right subtree. This is what keeps the in-order
        // sequence unchanged.
        pPivot.right = promoted.left;

        // Maintain the upward link of the transferred subtree. The sentinel is
        // excluded because its parent field is reserved for use by the delete
        // repair and must not be overwritten here.
        if (promoted.left != nil) {
            promoted.left.parent = pPivot;
        }

        // Step 2: the promoted node inherits the pivot's former parent.
        promoted.parent = pPivot.parent;

        // Step 3: attach the promoted node underneath that parent, or make it
        // the new root when the pivot was the root of the whole tree.
        if (pPivot.parent == nil) {
            root = promoted;
        } else if (pPivot == pPivot.parent.left) {
            pPivot.parent.left = promoted;
        } else {
            pPivot.parent.right = promoted;
        }

        // Step 4: the pivot descends to become the left child of the node that
        // took its place, completing the rotation in both link directions.
        promoted.left = pPivot;
        pPivot.parent = promoted;
    }

    /**
     * Rotates the subtree rooted at the specified node to the right, promoting
     * its left child into its position.
     *
     * Detailed explanation of:
     * - Purpose: Provides the mirror image of rotateLeft. The red-black case
     *   analysis is symmetric in the two directions, and both repair procedures
     *   handle a left-hand and a right-hand case that differ only in which of
     *   these two rotations they invoke.
     * - Business context: See rotateLeft; the same reasoning applies with the
     *   directions exchanged.
     * - Processing steps: Mirrors rotateLeft exactly, promoting the left child
     *   and transferring that child's right subtree across to become the pivot's
     *   new left subtree. The two methods are written out separately rather than
     *   being folded into one direction-parameterised helper, because the
     *   symmetry is easier to verify when both forms are visible and because
     *   this is where mistaken link directions are most likely to hide.
     * - Assumptions: Assumes the left child of the pivot is not the sentinel,
     *   which every call site guarantees.
     * - Side effects: Mutates the parent and child references of up to three
     *   nodes and possibly the root reference. Colours are left untouched.
     *
     * Time complexity: O(1); a fixed number of reference assignments.
     * Space complexity: O(1); no allocation occurs.
     *
     * @param pPivot
     * The node to rotate downwards into the right position. Must be a node of
     * this tree and must have a left child other than the sentinel.
     */
    private void rotateRight(RBNode pPivot) {
        // The left child is promoted into the position currently held by the
        // pivot.
        RBNode promoted = pPivot.left;

        // The promoted node's right subtree holds exactly those values that are
        // less than the pivot but greater than the promoted node, so it becomes
        // the pivot's new left subtree.
        pPivot.left = promoted.right;

        // Maintain the upward link of the transferred subtree, excluding the
        // sentinel for the same reason as in rotateLeft.
        if (promoted.right != nil) {
            promoted.right.parent = pPivot;
        }

        // The promoted node inherits the pivot's former parent.
        promoted.parent = pPivot.parent;

        // Attach the promoted node underneath that parent, or make it the new
        // root when the pivot was the root of the whole tree.
        if (pPivot.parent == nil) {
            root = promoted;
        } else if (pPivot == pPivot.parent.right) {
            pPivot.parent.right = promoted;
        } else {
            pPivot.parent.left = promoted;
        }

        // The pivot descends to become the right child of the node that took its
        // place, completing the rotation in both link directions.
        promoted.right = pPivot;
        pPivot.parent = promoted;
    }

    /**
     * Inserts the specified content into the tree, maintaining both the binary
     * search ordering and all five red-black properties.
     *
     * Detailed explanation of:
     * - Purpose: Adds a new value at its correct sorted position and then
     *   restores the colour invariants, so that the height bound continues to
     *   hold no matter what order values arrive in.
     * - Business context: Serves as the primary entry point for growing the
     *   tree. The guarantee it provides is precisely what an unbalanced binary
     *   search tree cannot offer: inserting an already-sorted sequence produces
     *   a tree of logarithmic rather than linear height.
     * - Processing steps:
     *   1. Reject null, consistent with the other structures in this library.
     *   2. Descend from the root comparing the new value against each node,
     *      remembering the last node visited, until a leaf position is reached.
     *      Abandon the insertion if an equal value is encountered, since
     *      duplicates are not stored distinctly.
     *   3. Link the new node underneath the remembered parent on the side the
     *      comparison dictates, or install it as the root if the tree was empty.
     *   4. Colour the new node red and repair.
     * - Assumptions: Assumes the ordering comparisons form a consistent total
     *   order over every value ever inserted; an inconsistent ordering places
     *   values where later searches will not look for them.
     * - Side effects: Allocates one node, increments the element count, and may
     *   recolour and rotate nodes along the path back to the root.
     *
     * Time complexity: O(log n). The descent is bounded by the height, and the
     * repair performs at most two rotations plus a recolouring walk that is also
     * bounded by the height.
     * Space complexity: O(1); both the descent and the repair are iterative.
     *
     * @param pContent
     * The content value to insert. If null, this method performs no action
     * rather than raising an exception. If a value comparing equal to one
     * already stored is supplied, the tree is left completely unchanged,
     * including its element count.
     */
    public void insert(ContentType pContent) {
        // Ignore null rather than storing an entry that could not participate in
        // any subsequent ordering comparison.
        if (pContent == null) {
            return;
        }

        // Cursor descending towards the leaf position where the value belongs.
        RBNode currentNode = root;

        // Trails one level behind the cursor, so that when the cursor falls off a
        // leaf this still refers to the node the new one must hang from.
        RBNode parentNode = nil;

        // Locate the insertion point.
        while (currentNode != nil) {
            // Remember the current node before descending past it.
            parentNode = currentNode;

            if (pContent.isLess(currentNode.content)) {
                currentNode = currentNode.left;
            } else if (pContent.isGreater(currentNode.content)) {
                currentNode = currentNode.right;
            } else {
                // An equal value is already stored. Duplicates are not inserted
                // distinctly, so abandon the operation entirely; returning here
                // rather than falling through is what keeps the element count
                // truthful.
                return;
            }
        }

        // Create the node and give it the sentinel as both children, which is
        // this implementation's representation of a leaf.
        RBNode insertedNode = new RBNode(pContent);
        insertedNode.left = nil;
        insertedNode.right = nil;
        insertedNode.parent = parentNode;

        // Link the new node into the tree on the side the ordering dictates.
        if (parentNode == nil) {
            // The tree was empty, so the new node becomes the root. The repair
            // below is responsible for colouring it black.
            root = insertedNode;
        } else if (pContent.isLess(parentNode.content)) {
            parentNode.left = insertedNode;
        } else {
            parentNode.right = insertedNode;
        }

        /*
         * Colour the new node red rather than black. This choice is what makes
         * the repair tractable:
         * - A black node would add one to the black height of every path running
         *   through it, breaking the equal-black-count property immediately and
         *   in a way that cannot be observed locally.
         * - A red node leaves all black counts unchanged, so the only property
         *   that can now be violated is the one forbidding a red node from
         *   having a red parent, which is a purely local condition.
         */
        insertedNode.color = Color.RED;

        // Account for the stored value before repairing, since the repair does
        // not add or remove any content.
        size++;

        // Restore the colour invariants that the red insertion may have broken.
        insertFixup(insertedNode);
    }

    /**
     * Restores the red-black properties after a red node has been linked into
     * the tree.
     *
     * Detailed explanation of:
     * - Purpose: Repairs the single class of violation that insertion can
     *   introduce, namely a red node whose parent is also red, and does so
     *   without disturbing the equal-black-count property.
     * - Business context: This procedure is the reason the structure keeps its
     *   logarithmic height. Every rotation it performs shortens the longest path
     *   relative to the shortest, and the recolouring cases push the problem
     *   upwards until it either disappears or reaches the root, where it is
     *   resolved by colouring the root black.
     * - Processing steps: The loop runs only while the current node's parent is
     *   red, since a black parent means no violation exists. Within the loop the
     *   node's uncle decides between two treatments, and the whole analysis
     *   comes in a left-hand and a mirrored right-hand form depending on which
     *   side of the grandparent the parent sits:
     *   1. Red uncle: the parent and uncle are recoloured black and the
     *      grandparent red. Every path through the grandparent keeps its black
     *      count, so this is a pure recolouring. The grandparent may now clash
     *      with its own parent, so it becomes the new current node and the loop
     *      repeats one level higher. This is the only case that iterates, and it
     *      is what makes the repair O(log n) rather than O(1).
     *   2. Black uncle, current node on the inner side: a single rotation at the
     *      parent converts this into case 3, which is the shape the final
     *      rotation can actually fix.
     *   3. Black uncle, current node on the outer side: recolour the parent
     *      black and the grandparent red, then rotate at the grandparent. This
     *      terminates the loop, because the subtree root is now black and can no
     *      longer clash with anything above it.
     * - Assumptions: Assumes the supplied node is red and is linked into the
     *   tree, and that the only property currently violated is the red-red one.
     * - Side effects: Recolours nodes along the path towards the root and
     *   performs at most two rotations; may change the root reference.
     *
     * Time complexity: O(log n) worst case, dominated by the red-uncle case
     * walking upwards one level at a time. At most two rotations are performed
     * in total, which is the sense in which insertion is cheaper here than in an
     * AVL tree.
     * Space complexity: O(1); the repair is iterative.
     *
     * @param pInserted
     * The newly inserted red node at which the repair begins. Must be a node of
     * this tree.
     */
    private void insertFixup(RBNode pInserted) {
        // Tracks the position of the potential red-red violation as it moves
        // upwards through the tree.
        RBNode currentNode = pInserted;

        // A black parent means the red-red property holds and no repair is
        // required. The root's parent is the black sentinel, so this condition
        // also terminates the loop at the top of the tree.
        while (currentNode.parent.color == Color.RED) {

            // The grandparent exists whenever the parent is red, because a red
            // node can never be the root and therefore always has a parent.
            RBNode grandparent = currentNode.parent.parent;

            if (currentNode.parent == grandparent.left) {
                // Left-hand form: the parent is the grandparent's left child, so
                // the uncle is the right child.
                RBNode uncle = grandparent.right;

                if (uncle.color == Color.RED) {
                    // Case 1: pushing blackness down from the grandparent to its
                    // two children preserves every black count while removing
                    // the local red-red clash.
                    currentNode.parent.color = Color.BLACK;
                    uncle.color = Color.BLACK;
                    grandparent.color = Color.RED;

                    // The grandparent is now red and may clash with its own
                    // parent, so the same analysis repeats one level higher.
                    currentNode = grandparent;
                } else {
                    if (currentNode == currentNode.parent.right) {
                        // Case 2: the node is on the inner side of the
                        // grandparent, a shape the final rotation cannot fix
                        // directly. Rotating at the parent moves it to the outer
                        // side and turns this into case 3. The cursor moves to
                        // the former parent, which is the node that descends.
                        currentNode = currentNode.parent;
                        rotateLeft(currentNode);
                    }

                    // Case 3: recolour so that the subtree root ends up black,
                    // then rotate the grandparent down. The rotation restores the
                    // black count that the recolouring would otherwise have
                    // altered on the paths through the uncle.
                    currentNode.parent.color = Color.BLACK;
                    currentNode.parent.parent.color = Color.RED;
                    rotateRight(currentNode.parent.parent);
                }
            } else {
                // Right-hand form: exact mirror image of the above, with left
                // and right exchanged throughout.
                RBNode uncle = grandparent.left;

                if (uncle.color == Color.RED) {
                    // Case 1, mirrored.
                    currentNode.parent.color = Color.BLACK;
                    uncle.color = Color.BLACK;
                    grandparent.color = Color.RED;
                    currentNode = grandparent;
                } else {
                    if (currentNode == currentNode.parent.left) {
                        // Case 2, mirrored.
                        currentNode = currentNode.parent;
                        rotateRight(currentNode);
                    }

                    // Case 3, mirrored.
                    currentNode.parent.color = Color.BLACK;
                    currentNode.parent.parent.color = Color.RED;
                    rotateLeft(currentNode.parent.parent);
                }
            }
        }

        /*
         * The root must always be black. Case 1 can colour it red on its way up,
         * and forcing it black here is safe precisely because it adds one to the
         * black count of every path in the tree simultaneously, which leaves all
         * of them equal.
         */
        root.color = Color.BLACK;
    }
}
