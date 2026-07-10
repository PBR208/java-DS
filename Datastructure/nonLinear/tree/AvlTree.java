package nonLinear.tree;

/**
 * Purpose:
 * Implements a self-balancing binary search tree (AVL tree) that maintains
 * logarithmic-height guarantees through automatic rebalancing after every
 * insertion and removal. Unlike the plain BinaryTree structure elsewhere in
 * this package, this implementation relies on a bounded content type
 * (ComparableContent) to establish ordering, enabling standard binary
 * search tree semantics (search, insert, remove) combined with height
 * tracking and rotation-based rebalancing to keep the tree height within a
 * strict bound relative to the number of stored elements. This guarantees
 * worst-case O(log n) performance for search, insertion, and removal
 * operations, making it suitable for scenarios requiring predictable
 * performance regardless of insertion order, in contrast to an
 * unbalanced binary search tree which may degrade to linear-time
 * operations under adversarial insertion sequences.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Self-balancing binary search tree (AVL tree) generic over a bounded
 * comparable content type, maintaining logarithmic height through
 * automatic rotation-based rebalancing.
 *
 * Responsibility: Encapsulates ordered storage, search, insertion, and
 * removal of content values, while automatically maintaining the AVL
 * balance invariant (the height difference between left and right
 * subtrees of any node never exceeds one) through height tracking and
 * rotations.
 *
 * Scope: Used within the nonLinear.tree package as a performance-oriented
 * alternative to unbalanced binary search trees, wherever ordered,
 * height-bounded storage of comparable content is required.
 *
 * Dependencies: Depends on the ComparableContent interface to establish
 * ordering semantics (isLess, isGreater, isEqual) for the stored content
 * type; relies only on its own private nested AVLNode class for internal
 * node representation.
 *
 * Thread-safety: This class is not thread-safe. Concurrent insertion,
 * removal, or traversal from multiple threads without external
 * synchronization may corrupt the tree structure or violate the AVL
 * balance invariant.
 *
 * Lifecycle: An AvlTree instance begins empty and grows and shrinks
 * dynamically over its lifetime through repeated insert and remove
 * operations, with each mutating operation re-establishing the balance
 * invariant before returning.
 *
 * Architectural role: Serves as a foundational, generic, self-balancing
 * hierarchical data structure that may be consumed by higher-level
 * algorithms requiring guaranteed logarithmic-time ordered access within
 * the system.
 *
 * @param <ContentType>
 * The type of content stored at each node of the tree. Must implement
 * ComparableContent to provide the ordering comparisons required for
 * binary search tree placement and balancing.
 */
public class AvlTree<ContentType extends ComparableContent<ContentType>> {

    /**
     * Purpose:
     * Represents a single node within the AVL tree, bundling the stored
     * content together with raw left and right child references (unlike
     * BinaryTree, children here are plain AVLNode references rather than
     * wrapped tree instances) and a cached height value used for O(1)
     * balance-factor computation during rebalancing.
     *
     * Owner:
     * P.B.R. - https://github.com/PBR208/
     *
     * Version:
     * 1.0
     */

    /**
     * Represents a single node of the AVL tree, holding content, child
     * references, and a cached subtree height.
     *
     * Responsibility: Stores the content associated with this node,
     * direct references to its left and right child nodes (which may be
     * null to represent the absence of a child), and the cached height of
     * the subtree rooted at this node.
     *
     * Scope: Private to the enclosing AvlTree class; not exposed outside
     * this file.
     *
     * Dependencies: None beyond the generic ContentType parameter of the
     * enclosing class.
     *
     * Thread-safety: Not thread-safe; mutation of content, left, right, or
     * height fields from multiple threads without external synchronization
     * may result in inconsistent state.
     *
     * Lifecycle: An AVLNode is created when a new value is inserted into
     * the tree and is discarded when the corresponding value is removed;
     * its height field is recalculated after every structural change
     * beneath it.
     *
     * Architectural role: Acts as the internal storage unit backing each
     * position within the AVL tree.
     */
    private class AVLNode {

        // Stores the content value held at this node. Used both as the
        // data payload and as the basis for ordering comparisons during
        // search, insertion, and removal.
        ContentType content;

        // References the left child node, containing values considered
        // "less than" this node's content per ComparableContent semantics.
        // Null indicates the absence of a left child.
        AVLNode left;

        // References the right child node, containing values considered
        // "greater than" this node's content per ComparableContent
        // semantics. Null indicates the absence of a right child.
        AVLNode right;

        // Caches the height of the subtree rooted at this node (the
        // number of edges on the longest downward path to a leaf, plus
        // one). Maintained incrementally via updateHeight to allow O(1)
        // balance-factor computation rather than recalculating height by
        // full subtree traversal on every check.
        int height;

        /**
         * Constructs a new AVLNode holding the specified content, with no
         * children and an initial height of one (representing a leaf
         * node).
         *
         * Detailed explanation of:
         * - Purpose: Initializes a newly created tree node as a leaf,
         *   since a freshly inserted node has no children at the moment
         *   of creation.
         * - Business context: Represents the creation of a new data point
         *   within the ordered hierarchical structure, prior to any
         *   subsequent rebalancing that may occur as insertion recursion
         *   unwinds.
         * - Processing steps: Assigns the supplied content to the internal
         *   content field and initializes height to 1, reflecting a
         *   single-node subtree. The left and right fields are left at
         *   their default null value, since a newly created node has no
         *   children.
         * - Assumptions: Assumes the caller has already validated that
         *   pContent is non-null, since this constructor performs no
         *   validation of its own.
         * - Side effects: None beyond initialization of instance state.
         *
         * @param pContent
         * The content value to store at this node. Expected to be
         * non-null, as validation is performed by the calling insert
         * method prior to invocation.
         */
        AVLNode(ContentType pContent) {
            // Assign the supplied content as this node's payload.
            this.content = pContent;

            // A newly created node is always a leaf, so its subtree
            // height is exactly one.
            this.height = 1;
        }
    }

    // References the root node of this AVL tree, or null when the tree is
    // currently empty. This single field is the entry point for all
    // recursive operations (search, insert, remove, traversal).
    private AVLNode node;

    /**
     * Determines whether this AVL tree currently contains no elements.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to check whether the tree has
     *   any content without requiring the caller to inspect the root node
     *   reference directly.
     * - Business context: Used by traversal and query methods to guard
     *   against operating on an uninitialized or fully cleared tree.
     * - Processing steps: Directly evaluates whether the internal node
     *   reference is null.
     * - Assumptions: Assumes the node field accurately reflects the
     *   current occupancy state of the tree.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if this tree currently holds no elements (node is null); false
     * if at least one node is present.
     */
    public boolean isEmpty() {
        return node == null;
    }

    /**
     * Inserts the specified content into the tree, maintaining binary
     * search tree ordering and AVL balance invariants.
     *
     * Detailed explanation of:
     * - Purpose: Adds a new value to the tree at its correct sorted
     *   position while ensuring the tree remains height-balanced after the
     *   insertion.
     * - Business context: Serves as the primary entry point for growing
     *   the tree with new data while preserving both search-ability
     *   (ordering) and guaranteed logarithmic height (balance).
     * - Processing steps: Returns immediately without effect if the
     *   supplied content is null. Otherwise, delegates to the recursive
     *   helper insertRec, passing the current root, and reassigns the
     *   result back to the root field, since rebalancing may change which
     *   node is the new subtree root.
     * - Assumptions: Assumes the content type's isLess/isGreater/isEqual
     *   comparisons form a consistent total ordering over all values ever
     *   inserted into this tree.
     * - Side effects: Mutates the tree's internal node structure by
     *   adding a new node and potentially performing rotations to
     *   restore balance; updates the root reference if rebalancing
     *   changes it.
     *
     * @param pContent
     * The content value to insert into the tree. If null, this method
     * performs no action. If a value considered equal to an existing
     * node's content (per isEqual) is supplied, the insertion recursion
     * takes neither the less-than nor greater-than branch, effectively
     * leaving the tree structurally unchanged aside from a rebalancing
     * pass.
     */
    public void insert(ContentType pContent) {
        // Ignore null content rather than raising an exception or
        // inserting an invalid entry.
        if (pContent == null) {
            return;
        }
        // Delegate to the recursive insertion helper and reassign the
        // root, since balancing operations may replace the subtree root
        // returned from the recursive call.
        node = insertRec(node, pContent);
    }

    /**
     * Recursively locates the correct sorted position for the specified
     * value, inserts it, and rebalances the traversed path on the way
     * back up the recursion.
     *
     * Detailed explanation of:
     * - Purpose: Implements the core recursive descent-and-rebuild logic
     *   underlying the public insert method, combining binary search tree
     *   placement with AVL rebalancing in a single pass.
     * - Business context: Ensures that every insertion both preserves
     *   sorted ordering and restores the height-balance invariant along
     *   the entire path from the insertion point back to the root.
     * - Processing steps:
     *   1. Base case: if the current subtree is null, a new leaf node is
     *      created here to hold the value.
     *   2. If the value is less than the current node's content, recurse
     *      into the left subtree and reattach the (possibly rebalanced)
     *      result.
     *   3. Else if the value is greater, recurse into the right subtree
     *      and reattach the (possibly rebalanced) result.
     *   4. If the value is neither less nor greater (i.e., considered
     *      equal), no structural change is made at this level, since
     *      duplicate values are not distinctly inserted.
     *   5. Regardless of which branch was taken, the current node's
     *      height is refreshed and it is rebalanced before being returned
     *      to the caller, ensuring the balance invariant is restored
     *      bottom-up as the recursion unwinds.
     * - Assumptions: Assumes value.isLess and value.isGreater are mutually
     *   exclusive and together with isEqual form a total ordering.
     * - Side effects: Allocates a new AVLNode when the insertion point is
     *   reached; mutates left/right child references and height fields of
     *   nodes along the traversed path; may perform rotations via
     *   balance().
     *
     * @param current
     * The root of the subtree currently being examined for insertion. If
     * null, a new node is created here.
     * @param value
     * The content value being inserted. Must not be null.
     *
     * @return
     * The (possibly new, possibly rebalanced) root of the subtree after
     * the insertion and rebalancing have been applied.
     */
    private AVLNode insertRec(AVLNode current, ContentType value) {

        // Base case: an empty subtree is where the new value belongs;
        // create and return a new leaf node here.
        if (current == null) {
            return new AVLNode(value);
        }

        // Binary search tree placement: descend left if the value is
        // smaller than the current node's content.
        if (value.isLess(current.content)) {
            current.left = insertRec(current.left, value);
        } else if (value.isGreater(current.content)) {
            // Descend right if the value is larger than the current
            // node's content.
            current.right = insertRec(current.right, value);
        }
        // If the value is neither less nor greater (considered equal to
        // the current node), no branch is taken and no duplicate node is
        // created; the tree structure below this point remains unchanged.

        // Refresh this node's height and restore the AVL balance
        // invariant before returning control to the caller, ensuring
        // rebalancing propagates correctly up the recursive call chain.
        return balance(current);
    }

    /**
     * Searches the tree for content considered equal to the specified
     * value and returns the matching stored content, if found.
     *
     * Detailed explanation of:
     * - Purpose: Provides ordered lookup of a value within the tree,
     *   exploiting the binary search tree structure to avoid a full
     *   linear scan.
     * - Business context: Serves as the primary read/query operation for
     *   determining whether a given value exists within the tree and
     *   retrieving the actual stored instance.
     * - Processing steps: Returns null immediately if the supplied content
     *   is null. Otherwise, delegates to the recursive helper searchRec
     *   starting at the root, then unwraps the resulting AVLNode (if any)
     *   to return its content field, or null if no match was found.
     * - Assumptions: Assumes the content type's ordering comparisons are
     *   consistent with the ordering used during insertion.
     * - Side effects: None; this method does not modify tree state.
     *
     * @param pContent
     * The content value to search for, compared using isEqual/isLess/
     * isGreater semantics rather than reference or Object.equals
     * comparison. If null, this method returns null immediately.
     *
     * @return
     * The stored content instance considered equal to pContent, or null
     * if no such value exists in the tree, or if pContent itself is null.
     */
    public ContentType search(ContentType pContent) {
        // Null input cannot match any stored content; return immediately.
        if (pContent == null) {
            return null;
        }

        // Delegate to the recursive search helper starting at the root.
        AVLNode result = searchRec(node, pContent);

        // Unwrap the matching node's content, or return null if no match
        // was found.
        return result == null ? null : result.content;
    }

    /**
     * Recursively searches the subtree rooted at the specified node for
     * content considered equal to the given value, using binary search
     * tree ordering to guide the descent.
     *
     * Detailed explanation of:
     * - Purpose: Implements the core recursive lookup logic underlying the
     *   public search method.
     * - Business context: Enables logarithmic-time lookup by exploiting
     *   the sorted structure of the tree rather than requiring a full
     *   traversal.
     * - Processing steps: Returns null immediately if the current subtree
     *   is null (value not found). If the value is equal to the current
     *   node's content, returns the current node. Otherwise, recurses into
     *   the left subtree if the value is less than the current content, or
     *   the right subtree otherwise (implying the value is greater).
     * - Assumptions: Assumes value.isLess, value.isGreater, and isEqual
     *   together form a consistent total ordering, such that any value not
     *   equal to and not less than the current content must be greater.
     * - Side effects: None; this method does not modify tree state.
     *
     * @param current
     * The root of the subtree currently being searched. If null, the
     * search along this path terminates unsuccessfully.
     * @param value
     * The content value being searched for. Must not be null.
     *
     * @return
     * The AVLNode whose content is considered equal to value, or null if
     * no such node exists within the subtree rooted at current.
     */
    private AVLNode searchRec(AVLNode current, ContentType value) {

        // Base case: reached an empty subtree without finding a match.
        if (current == null) {
            return null;
        }

        // Direct match found at this node.
        if (value.isEqual(current.content)) {
            return current;
        }

        // Exploit sorted ordering: only one subtree can possibly contain
        // the target value.
        if (value.isLess(current.content)) {
            return searchRec(current.left, value);
        } else {
            // Value is neither equal to nor less than current content,
            // so per total ordering it must be greater; search the right
            // subtree.
            return searchRec(current.right, value);
        }
    }

    /**
     * Removes content considered equal to the specified value from the
     * tree, maintaining binary search tree ordering and AVL balance
     * invariants.
     *
     * Detailed explanation of:
     * - Purpose: Deletes a matching value from the tree while preserving
     *   both correct sorted structure and guaranteed logarithmic height
     *   after the removal.
     * - Business context: Serves as the primary entry point for shrinking
     *   the tree by eliminating obsolete or invalid data while keeping the
     *   structure balanced for continued efficient access.
     * - Processing steps: Returns immediately without effect if the
     *   supplied content is null. Otherwise, delegates to the recursive
     *   helper removeRec, passing the current root, and reassigns the
     *   result back to the root field, since removal and rebalancing may
     *   change which node is the new subtree root.
     * - Assumptions: Assumes the content type's ordering comparisons are
     *   consistent with the ordering used during insertion. If no node
     *   matching pContent exists, the tree remains structurally unchanged.
     * - Side effects: Mutates the tree's internal node structure by
     *   removing a node (and potentially promoting a successor value) and
     *   performing rotations to restore balance; updates the root
     *   reference if rebalancing or removal changes it.
     *
     * @param pContent
     * The content value to remove from the tree, matched using isEqual/
     * isLess/isGreater semantics. If null, this method performs no
     * action.
     */
    public void remove(ContentType pContent) {
        // Ignore null content rather than raising an exception.
        if (pContent == null) {
            return;
        }
        // Delegate to the recursive removal helper and reassign the root,
        // since removal and balancing operations may replace the subtree
        // root returned from the recursive call.
        node = removeRec(node, pContent);
    }

    /**
     * Recursively locates and removes the node whose content is considered
     * equal to the specified value, handling all structural cases (leaf,
     * single child, two children) and rebalancing the traversed path on
     * the way back up the recursion.
     *
     * Detailed explanation of:
     * - Purpose: Implements the core recursive descent-and-rebuild logic
     *   underlying the public remove method, combining binary search tree
     *   deletion with AVL rebalancing in a single pass.
     * - Business context: Ensures that every removal both preserves sorted
     *   ordering and restores the height-balance invariant along the
     *   entire path from the deletion point back to the root.
     * - Processing steps:
     *   1. Base case: if the current subtree is null, the value was not
     *      found; nothing to remove along this path.
     *   2. If the value is less than the current node's content, recurse
     *      into the left subtree and reattach the (possibly rebalanced)
     *      result.
     *   3. Else if the value is greater, recurse into the right subtree
     *      and reattach the (possibly rebalanced) result.
     *   4. Otherwise, the current node is the one to remove, and one of
     *      four deletion cases applies:
     *      a. No children (leaf): the node is simply removed by returning
     *         null to the caller, which detaches it from its parent.
     *      b. Only a right child: the node is replaced by its right child.
     *      c. Only a left child: the node is replaced by its left child.
     *      d. Two children: the in-order successor (the minimum value in
     *         the right subtree) is located, its content is copied into
     *         the current node (preserving the node's position in the
     *         tree), and the successor's original node is then removed
     *         recursively from the right subtree to eliminate the
     *         duplicate.
     *   5. Regardless of which branch was taken, the current node's
     *      height is refreshed and it is rebalanced before being returned
     *      to the caller, ensuring the balance invariant is restored
     *      bottom-up as the recursion unwinds.
     * - Assumptions: Assumes value.isLess and value.isGreater are mutually
     *   exclusive and together with isEqual form a total ordering.
     *   Assumes findMin never receives a null argument, which holds
     *   because it is only invoked on current.right after confirming
     *   current.right is non-null (the two-children case).
     * - Side effects: Mutates left/right child references, content
     *   (in the two-children case), and height fields of nodes along the
     *   traversed path; may perform rotations via balance(); the removed
     *   node itself becomes eligible for garbage collection once
     *   unreferenced.
     *
     * @param current
     * The root of the subtree currently being examined for removal. If
     * null, the value was not found along this path.
     * @param value
     * The content value to remove. Must not be null.
     *
     * @return
     * The (possibly new, possibly rebalanced) root of the subtree after
     * the removal and rebalancing have been applied.
     */
    private AVLNode removeRec(AVLNode current, ContentType value) {

        // Base case: value not found along this path; nothing to remove.
        if (current == null) {
            return null;
        }

        // Binary search tree navigation: descend left if the target is
        // smaller than the current node's content.
        if (value.isLess(current.content)) {
            current.left = removeRec(current.left, value);
        } else if (value.isGreater(current.content)) {
            // Descend right if the target is larger than the current
            // node's content.
            current.right = removeRec(current.right, value);
        } else {
            // The current node is the target to be removed; handle each
            // of the standard binary search tree deletion cases.

            // Case: leaf node (no children) — simply detach it by
            // returning null to the caller.
            if (current.left == null && current.right == null) {
                return null;
            }

            // Case: only a right child exists — the right child takes
            // this node's place.
            if (current.left == null) {
                return current.right;
            }

            // Case: only a left child exists — the left child takes this
            // node's place.
            if (current.right == null) {
                return current.left;
            }

            // Case: both children exist — replace this node's content
            // with its in-order successor (the smallest value in the
            // right subtree), then remove the successor's original node
            // from the right subtree to eliminate the now-duplicated
            // value.
            AVLNode successor = findMin(current.right);
            current.content = successor.content;
            current.right = removeRec(current.right, successor.content);
        }

        // Refresh this node's height and restore the AVL balance
        // invariant before returning control to the caller, ensuring
        // rebalancing propagates correctly up the recursive call chain.
        return balance(current);
    }

    /**
     * Locates the node holding the minimum value within the subtree rooted
     * at the specified node, by repeatedly descending into left children.
     *
     * Detailed explanation of:
     * - Purpose: Identifies the in-order successor's source node during
     *   two-children removal, exploiting the binary search tree property
     *   that the minimum value of a subtree is always found by following
     *   left child references until none remain.
     * - Business context: Supports the removeRec algorithm's handling of
     *   nodes with two children by providing the replacement value that
     *   preserves sorted ordering.
     * - Processing steps: Iteratively reassigns current to current.left
     *   as long as a left child exists, then returns the final node
     *   reached, which has no left child and therefore holds the smallest
     *   value in the subtree.
     * - Assumptions: Assumes the supplied node is non-null; this method
     *   performs no null-check on its input, since callers are expected
     *   to guarantee a non-null starting point (as removeRec does by
     *   invoking it only on a confirmed non-null current.right).
     * - Side effects: None; this method does not modify tree state.
     *
     * @param current
     * The root of the subtree in which to locate the minimum value node.
     * Must not be null.
     *
     * @return
     * The node holding the smallest value within the subtree rooted at
     * current, identified as the leftmost node reachable from current.
     */
    private AVLNode findMin(AVLNode current) {
        // Repeatedly descend into left children; the leftmost reachable
        // node holds the minimum value in the subtree.
        while (current.left != null) {
            current = current.left;
        }
        return current;
    }

    /**
     * Retrieves the cached height of the specified node's subtree,
     * treating a null node as having a height of zero.
     *
     * Detailed explanation of:
     * - Purpose: Provides a null-safe accessor for subtree height,
     *   avoiding repeated null-checks at every call site throughout the
     *   balancing logic.
     * - Business context: Height values are the foundation of the AVL
     *   balance-factor calculation used to detect and correct imbalance
     *   after structural changes.
     * - Processing steps: Returns 0 immediately if the supplied node is
     *   null (representing an empty subtree); otherwise returns the
     *   node's cached height field directly.
     * - Assumptions: Assumes the height field of any non-null node is
     *   kept up to date via updateHeight, since this method does not
     *   recompute height from scratch.
     * - Side effects: None; this method does not modify tree state.
     *
     * @param node
     * The node whose subtree height is to be retrieved. May be null to
     * represent an empty subtree.
     *
     * @return
     * The cached height of the subtree rooted at the specified node, or
     * 0 if the node is null.
     */
    private int getHeight(AVLNode node) {
        return node == null ? 0 : node.height;
    }

    /**
     * Computes the AVL balance factor of the specified node, defined as
     * the height of its left subtree minus the height of its right
     * subtree.
     *
     * Detailed explanation of:
     * - Purpose: Quantifies how skewed a node's subtree is toward its left
     *   or right side, forming the basis for detecting when rotations are
     *   required to restore the AVL invariant.
     * - Business context: The AVL invariant requires that the balance
     *   factor of every node remain within the range [-1, 1]; values
     *   outside this range indicate the tree has become unbalanced and
     *   must be corrected via rotation.
     * - Processing steps: Returns 0 immediately if the supplied node is
     *   null (an empty subtree is trivially balanced). Otherwise,
     *   subtracts the height of the right subtree from the height of the
     *   left subtree using the null-safe getHeight helper.
     * - Assumptions: Assumes both child subtrees' height fields are
     *   current and accurate at the time of this call.
     * - Side effects: None; this method does not modify tree state.
     *
     * @param node
     * The node whose balance factor is to be computed. May be null.
     *
     * @return
     * The balance factor of the specified node: a positive value indicates
     * a left-heavy subtree, a negative value indicates a right-heavy
     * subtree, and zero indicates equal left and right heights. Returns 0
     * if the node is null.
     */
    private int getBalance(AVLNode node) {
        return node == null ? 0 : getHeight(node.left) - getHeight(node.right);
    }

    /**
     * Recalculates and updates the cached height of the specified node
     * based on the current heights of its two children.
     *
     * Detailed explanation of:
     * - Purpose: Keeps a node's cached height field accurate after any
     *   structural change to its children, which is required for correct
     *   balance-factor computation.
     * - Business context: Height caching is what allows balance-factor
     *   checks to run in constant time rather than requiring a full
     *   subtree traversal after every insertion or removal.
     * - Processing steps: Performs no action if the supplied node is null.
     *   Otherwise, sets the node's height to one plus the greater of its
     *   left and right child subtree heights, reflecting the longest path
     *   from this node down to a leaf.
     * - Assumptions: Assumes both child subtrees' height fields are
     *   already current at the time of this call; this method does not
     *   recursively update descendant heights.
     * - Side effects: Mutates the height field of the supplied node when
     *   it is non-null.
     *
     * @param node
     * The node whose height is to be recalculated and updated. If null,
     * this method performs no action.
     */
    private void updateHeight(AVLNode node) {
        if (node != null) {
            node.height = 1 + Math.max(getHeight(node.left), getHeight(node.right));
        }
    }

    /**
     * Restores the AVL balance invariant at the specified node by
     * refreshing its height and applying the appropriate rotation(s) if
     * the node has become unbalanced.
     *
     * Detailed explanation of:
     * - Purpose: Serves as the central rebalancing routine invoked after
     *   every insertion and removal step, detecting imbalance and
     *   applying the correct single or double rotation to restore the
     *   invariant.
     * - Business context: The AVL balance invariant (balance factor within
     *   [-1, 1] at every node) is what guarantees the tree's height stays
     *   logarithmic in the number of stored elements, which in turn
     *   guarantees logarithmic-time search, insertion, and removal.
     * - Processing steps:
     *   1. Refreshes the node's cached height to reflect any recent
     *      structural changes to its children.
     *   2. Computes the node's balance factor.
     *   3. If the balance factor exceeds 1 (left-heavy), the subtree is
     *      left-left or left-right heavy. If the left child itself leans
     *      right (negative balance), a left rotation is first applied to
     *      the left child to convert the case into a simple left-left
     *      case (this is the "double rotation" scenario); a right
     *      rotation is then applied to the current node to restore
     *      balance.
     *   4. If the balance factor is less than -1 (right-heavy), the
     *      subtree is right-right or right-left heavy. If the right child
     *      itself leans left (positive balance), a right rotation is
     *      first applied to the right child to convert the case into a
     *      simple right-right case; a left rotation is then applied to
     *      the current node to restore balance.
     *   5. If the balance factor is already within [-1, 1], no rotation
     *      is necessary and the node is returned unchanged.
     * - Assumptions: Assumes this method is called on every node along
     *   the path affected by an insertion or removal, from the deepest
     *   modified node up to the root, so that imbalance is detected and
     *   corrected at every level.
     * - Side effects: Mutates the height field of the supplied node (via
     *   updateHeight); may restructure the subtree via rotateLeft and/or
     *   rotateRight, which reassigns child references and heights of the
     *   nodes involved in the rotation.
     *
     * @param node
     * The node at which to check and, if necessary, restore the balance
     * invariant.
     *
     * @return
     * The root of the subtree after rebalancing: either the original node
     * (if no rotation was needed) or the new subtree root produced by the
     * applied rotation(s).
     */
    private AVLNode balance(AVLNode node) {

        // Refresh this node's height to account for any recent structural
        // changes beneath it before evaluating balance.
        updateHeight(node);

        // Compute how skewed this node's subtree currently is.
        int balanceFactor = getBalance(node);

        // Left-heavy case: the left subtree is more than one level taller
        // than the right subtree.
        if (balanceFactor > 1) {

            // Left-Right case: the left child leans right, so a single
            // right rotation at this node would not fully resolve the
            // imbalance. First rotate the left child left to convert this
            // into a straightforward Left-Left case.
            if (getBalance(node.left) < 0) {
                node.left = rotateLeft(node.left);
            }

            // Left-Left case (or converted from Left-Right above):
            // a single right rotation at this node restores balance.
            return rotateRight(node);
        }

        // Right-heavy case: the right subtree is more than one level
        // taller than the left subtree.
        if (balanceFactor < -1) {

            // Right-Left case: the right child leans left, so a single
            // left rotation at this node would not fully resolve the
            // imbalance. First rotate the right child right to convert
            // this into a straightforward Right-Right case.
            if (getBalance(node.right) > 0) {
                node.right = rotateRight(node.right);
            }

            // Right-Right case (or converted from Right-Left above):
            // a single left rotation at this node restores balance.
            return rotateLeft(node);
        }

        // Balance factor already within the acceptable [-1, 1] range; no
        // rotation is required.
        return node;
    }

    /**
     * Performs a right rotation at the specified node, promoting its left
     * child to become the new subtree root.
     *
     * Detailed explanation of:
     * - Purpose: Corrects a left-heavy imbalance by restructuring the
     *   subtree so that the left child takes the place of the current
     *   node, while preserving binary search tree ordering.
     * - Business context: One of the two fundamental rotation operations
     *   used by the balance() method to restore the AVL invariant after
     *   insertions or removals cause a left-heavy imbalance.
     * - Processing steps: Captures the current node's left child as the
     *   pivot. Reassigns the current node's left reference to the pivot's
     *   former right subtree (since that subtree's values are all greater
     *   than the pivot but less than the current node, they remain
     *   correctly ordered as the current node's new left subtree).
     *   Reassigns the pivot's right reference to the current node,
     *   completing the rotation. Refreshes the heights of both the
     *   original node and the pivot, in that order, since the original
     *   node's height must be recalculated before the pivot's (the
     *   pivot's height now depends on the original node's updated
     *   height).
     * - Assumptions: Assumes node.left is non-null, since this method is
     *   only invoked from balance() when a left-heavy condition has
     *   already been established, which implies a left child exists.
     * - Side effects: Mutates the left/right child references of both the
     *   original node and its former left child; mutates the height
     *   fields of both nodes.
     *
     * @param node
     * The node at which to perform the right rotation. Must have a
     * non-null left child.
     *
     * @return
     * The former left child of the input node, now serving as the new
     * root of this subtree after rotation.
     */
    private AVLNode rotateRight(AVLNode node) {

        // The left child becomes the new subtree root (the rotation
        // pivot).
        AVLNode leftChild = node.left;

        // The pivot's former right subtree becomes the original node's
        // new left subtree, preserving correct ordering.
        node.left = leftChild.right;

        // The original node becomes the pivot's right child, completing
        // the rotation.
        leftChild.right = node;

        // Recalculate heights bottom-up: the original node's height must
        // be updated first, since the pivot's height calculation depends
        // on it.
        updateHeight(node);
        updateHeight(leftChild);

        // Return the new subtree root.
        return leftChild;
    }

    /**
     * Performs a left rotation at the specified node, promoting its right
     * child to become the new subtree root.
     *
     * Detailed explanation of:
     * - Purpose: Corrects a right-heavy imbalance by restructuring the
     *   subtree so that the right child takes the place of the current
     *   node, while preserving binary search tree ordering.
     * - Business context: One of the two fundamental rotation operations
     *   used by the balance() method to restore the AVL invariant after
     *   insertions or removals cause a right-heavy imbalance.
     * - Processing steps: Captures the current node's right child as the
     *   pivot. Reassigns the current node's right reference to the
     *   pivot's former left subtree (since that subtree's values are all
     *   less than the pivot but greater than the current node, they
     *   remain correctly ordered as the current node's new right
     *   subtree). Reassigns the pivot's left reference to the current
     *   node, completing the rotation. Refreshes the heights of both the
     *   original node and the pivot, in that order, since the original
     *   node's height must be recalculated before the pivot's (the
     *   pivot's height now depends on the original node's updated
     *   height).
     * - Assumptions: Assumes node.right is non-null, since this method is
     *   only invoked from balance() when a right-heavy condition has
     *   already been established, which implies a right child exists.
     * - Side effects: Mutates the left/right child references of both the
     *   original node and its former right child; mutates the height
     *   fields of both nodes.
     *
     * @param node
     * The node at which to perform the left rotation. Must have a
     * non-null right child.
     *
     * @return
     * The former right child of the input node, now serving as the new
     * root of this subtree after rotation.
     */
    private AVLNode rotateLeft(AVLNode node) {

        // The right child becomes the new subtree root (the rotation
        // pivot).
        AVLNode rightChild = node.right;

        // The pivot's former left subtree becomes the original node's new
        // right subtree, preserving correct ordering.
        node.right = rightChild.left;

        // The original node becomes the pivot's left child, completing
        // the rotation.
        rightChild.left = node;

        // Recalculate heights bottom-up: the original node's height must
        // be updated first, since the pivot's height calculation depends
        // on it.
        updateHeight(node);
        updateHeight(rightChild);

        // Return the new subtree root.
        return rightChild;
    }

    /**
     * Initiates an in-order (left, root, right) traversal of this tree,
     * printing each visited node's content in ascending sorted order.
     *
     * Detailed explanation of:
     * - Purpose: Provides a public entry point for in-order traversal
     *   without requiring the caller to interact with internal AVLNode
     *   references.
     * - Business context: Because this tree maintains binary search tree
     *   ordering, an in-order traversal yields all stored values in
     *   ascending sorted sequence, useful for reporting or exporting
     *   sorted data.
     * - Processing steps: Delegates immediately to the private recursive
     *   helper inOrderRec, passing this tree's internal root node
     *   reference as the starting point.
     * - Assumptions: Assumes the internal node field accurately reflects
     *   the current tree structure.
     * - Side effects: Produces console output via the visit method for
     *   every node in the tree, in ascending sorted order.
     */
    public void inOrder() {
        inOrderRec(node);
    }

    /**
     * Recursively performs an in-order (left, root, right) traversal
     * starting from the specified node.
     *
     * Detailed explanation of:
     * - Purpose: Implements the recursive descent logic underlying the
     *   public inOrder method, visiting each node in left-root-right
     *   sequence, which corresponds to ascending sorted order for a
     *   binary search tree.
     * - Business context: Supports diagnostic output and any consumer
     *   requiring values in sorted sequence.
     * - Processing steps: Returns immediately if the current node is null
     *   (base case: empty subtree). Otherwise, recurses into the left
     *   child first, then visits the current node's content, then
     *   recurses into the right child.
     * - Assumptions: None beyond a well-formed tree structure.
     * - Side effects: Produces console output via the visit method for
     *   every visited node; consumes call stack space proportional to
     *   tree height due to recursion (bounded logarithmically thanks to
     *   the AVL balance invariant).
     *
     * @param current
     * The node from which to begin the in-order traversal. If null, the
     * recursion terminates immediately (base case).
     */
    private void inOrderRec(AVLNode current) {

        // Base case: an empty subtree contributes nothing to the
        // traversal.
        if (current == null) {
            return;
        }

        // Recurse into the left subtree before visiting the current node,
        // satisfying the left-first in-order sequence.
        inOrderRec(current.left);

        // Visit (print) the current node's content after the entire left
        // subtree has been processed.
        visit(current.content);

        // Recurse into the right subtree after the current node has been
        // visited.
        inOrderRec(current.right);
    }

    /**
     * Initiates a pre-order (root, left, right) traversal of this tree,
     * printing each visited node's content.
     *
     * Detailed explanation of:
     * - Purpose: Provides a public entry point for pre-order traversal
     *   without requiring the caller to interact with internal AVLNode
     *   references.
     * - Business context: Pre-order traversal is commonly used when the
     *   processing order needs to visit a parent before its children,
     *   such as when reconstructing or serializing the tree structure.
     * - Processing steps: Delegates immediately to the private recursive
     *   helper preOrderRec, passing this tree's internal root node
     *   reference as the starting point.
     * - Assumptions: Assumes the internal node field accurately reflects
     *   the current tree structure.
     * - Side effects: Produces console output via the visit method for
     *   every node in the tree, in pre-order sequence.
     */
    public void preOrder() {
        preOrderRec(node);
    }

    /**
     * Recursively performs a pre-order (root, left, right) traversal
     * starting from the specified node.
     *
     * Detailed explanation of:
     * - Purpose: Implements the recursive descent logic underlying the
     *   public preOrder method, visiting each node in root-left-right
     *   sequence.
     * - Business context: Supports algorithms and diagnostic output that
     *   require nodes to be processed before their descendants.
     * - Processing steps: Returns immediately if the current node is null
     *   (base case: empty subtree). Otherwise, visits the current node's
     *   content first, then recurses into the left child, then recurses
     *   into the right child.
     * - Assumptions: None beyond a well-formed tree structure.
     * - Side effects: Produces console output via the visit method for
     *   every visited node; consumes call stack space proportional to
     *   tree height due to recursion (bounded logarithmically thanks to
     *   the AVL balance invariant).
     *
     * @param current
     * The node from which to begin the pre-order traversal. If null, the
     * recursion terminates immediately (base case).
     */
    private void preOrderRec(AVLNode current) {

        // Base case: an empty subtree contributes nothing to the
        // traversal.
        if (current == null) {
            return;
        }

        // Visit (print) the current node's content before descending into
        // its children, satisfying the root-first pre-order sequence.
        visit(current.content);

        // Recurse into the left subtree.
        preOrderRec(current.left);

        // Recurse into the right subtree.
        preOrderRec(current.right);
    }

    /**
     * Initiates a post-order (left, right, root) traversal of this tree,
     * printing each visited node's content.
     *
     * Detailed explanation of:
     * - Purpose: Provides a public entry point for post-order traversal
     *   without requiring the caller to interact with internal AVLNode
     *   references.
     * - Business context: Post-order traversal is commonly used when
     *   children must be fully processed before their parent, such as
     *   when deleting the tree structure or evaluating bottom-up
     *   dependencies.
     * - Processing steps: Delegates immediately to the private recursive
     *   helper postOrderRec, passing this tree's internal root node
     *   reference as the starting point.
     * - Assumptions: Assumes the internal node field accurately reflects
     *   the current tree structure.
     * - Side effects: Produces console output via the visit method for
     *   every node in the tree, in post-order sequence.
     */
    public void postOrder() {
        postOrderRec(node);
    }

    /**
     * Recursively performs a post-order (left, right, root) traversal
     * starting from the specified node.
     *
     * Detailed explanation of:
     * - Purpose: Implements the recursive descent logic underlying the
     *   public postOrder method, visiting each node in left-right-root
     *   sequence.
     * - Business context: Supports algorithms and diagnostic output that
     *   require both children to be fully processed before their parent.
     * - Processing steps: Returns immediately if the current node is null
     *   (base case: empty subtree). Otherwise, recurses into the left
     *   child, then recurses into the right child, and only then visits
     *   the current node's content.
     * - Assumptions: None beyond a well-formed tree structure.
     * - Side effects: Produces console output via the visit method for
     *   every visited node; consumes call stack space proportional to
     *   tree height due to recursion (bounded logarithmically thanks to
     *   the AVL balance invariant).
     *
     * @param current
     * The node from which to begin the post-order traversal. If null, the
     * recursion terminates immediately (base case).
     */
    private void postOrderRec(AVLNode current) {

        // Base case: an empty subtree contributes nothing to the
        // traversal.
        if (current == null) {
            return;
        }

        // Recurse into the left subtree first, fully processing it before
        // moving on.
        postOrderRec(current.left);

        // Recurse into the right subtree next, fully processing it before
        // visiting the current node.
        postOrderRec(current.right);

        // Visit (print) the current node's content only after both child
        // subtrees have been completely processed, satisfying the
        // root-last post-order sequence.
        visit(current.content);
    }

    /**
     * Outputs the specified content to the standard console stream.
     *
     * Detailed explanation of:
     * - Purpose: Provides the concrete "visiting" action invoked by each
     *   traversal method (inOrder, preOrder, postOrder) when a node is
     *   reached in the traversal sequence.
     * - Business context: Serves as a simple diagnostic or demonstrative
     *   mechanism for observing traversal order and tree contents;
     *   represents the single point of customization if visiting behavior
     *   were to be extended in the future (e.g., writing to a log instead
     *   of the console).
     * - Processing steps: Directly prints the supplied content to
     *   standard output using System.out.println.
     * - Assumptions: Assumes the ContentType's toString() representation
     *   (invoked implicitly by println) produces meaningful output for
     *   diagnostic purposes.
     * - Side effects: Writes a line of output to the standard console
     *   stream.
     *
     * @param content
     * The content value to output. May be any value of type ContentType,
     * including null, since println safely handles null arguments.
     */
    private void visit(ContentType content) {
        System.out.println(content);
    }

    /**
     * Retrieves a new AvlTree instance representing the left subtree of
     * the root node.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the left subtree as an independently usable
     *   AvlTree instance, wrapping the raw AVLNode child reference rather
     *   than returning it directly, since AVLNode is a private inner
     *   type.
     * - Business context: Allows external callers to inspect or operate
     *   on the left branch of the tree using the same public AvlTree API,
     *   such as for recursive external processing.
     * - Processing steps: Returns null immediately if this tree is empty.
     *   Otherwise, constructs a new, otherwise-empty AvlTree wrapper and
     *   directly assigns its internal node field to reference this tree's
     *   root's left child (without copying the underlying subtree
     *   structure).
     * - Assumptions: Assumes isEmpty() accurately reflects node occupancy.
     *   Note that the returned AvlTree shares its underlying node
     *   structure with this tree; mutating operations performed through
     *   the returned instance (insert, remove) will affect the same
     *   underlying nodes referenced by this tree's left subtree, and vice
     *   versa, since no defensive copy is made.
     * - Side effects: Allocates a new AvlTree wrapper instance; does not
     *   copy or modify the underlying node structure.
     *
     * @return
     * A new AvlTree instance wrapping this tree's left child node, or
     * null if this tree itself is empty. If the root has no left child,
     * the returned AvlTree will itself report isEmpty() as true.
     */
    public AvlTree<ContentType> getLeftTree() {

        // An empty tree has no root, and therefore no left subtree to
        // expose.
        if (isEmpty()) {
            return null;
        }

        // Wrap the raw left child reference in a new AvlTree instance,
        // since AVLNode cannot be exposed directly outside this class.
        AvlTree<ContentType> t = new AvlTree<>();
        t.node = node.left;

        return t;
    }

    /**
     * Retrieves a new AvlTree instance representing the right subtree of
     * the root node.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the right subtree as an independently usable
     *   AvlTree instance, wrapping the raw AVLNode child reference rather
     *   than returning it directly, since AVLNode is a private inner
     *   type.
     * - Business context: Allows external callers to inspect or operate
     *   on the right branch of the tree using the same public AvlTree
     *   API, such as for recursive external processing.
     * - Processing steps: Returns null immediately if this tree is empty.
     *   Otherwise, constructs a new, otherwise-empty AvlTree wrapper and
     *   directly assigns its internal node field to reference this tree's
     *   root's right child (without copying the underlying subtree
     *   structure).
     * - Assumptions: Assumes isEmpty() accurately reflects node occupancy.
     *   Note that the returned AvlTree shares its underlying node
     *   structure with this tree; mutating operations performed through
     *   the returned instance (insert, remove) will affect the same
     *   underlying nodes referenced by this tree's right subtree, and
     *   vice versa, since no defensive copy is made.
     * - Side effects: Allocates a new AvlTree wrapper instance; does not
     *   copy or modify the underlying node structure.
     *
     * @return
     * A new AvlTree instance wrapping this tree's right child node, or
     * null if this tree itself is empty. If the root has no right child,
     * the returned AvlTree will itself report isEmpty() as true.
     */
    public AvlTree<ContentType> getRightTree() {

        // An empty tree has no root, and therefore no right subtree to
        // expose.
        if (isEmpty()) {
            return null;
        }

        // Wrap the raw right child reference in a new AvlTree instance,
        // since AVLNode cannot be exposed directly outside this class.
        AvlTree<ContentType> t = new AvlTree<>();
        t.node = node.right;

        return t;
    }

    /**
     * Retrieves the content stored at the root of this tree.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the data payload held at this tree's root node,
     *   if any, for inspection by callers.
     * - Business context: Used by external client code to read the value
     *   associated with a given tree position, such as when navigating
     *   the tree manually via getLeftTree/getRightTree.
     * - Processing steps: Returns null immediately if this tree is empty;
     *   otherwise returns the content field of the underlying root node.
     * - Assumptions: Assumes isEmpty() accurately reflects node occupancy.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * The content stored at this tree's root node, or null if this tree
     * is empty.
     */
    public ContentType getContent() {
        return isEmpty() ? null : node.content;
    }

    /**
     * Retrieves the cached height of this tree.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the overall height of the tree for inspection by
     *   callers, such as for diagnostic purposes or verifying balance
     *   characteristics externally.
     * - Business context: Height reflects the longest path from the root
     *   to a leaf and is the quantity the AVL balancing logic works to
     *   keep logarithmic relative to the number of stored elements.
     * - Processing steps: Delegates to the private, null-safe getHeight
     *   helper, passing the internal root node reference.
     * - Assumptions: Assumes the root node's height field, and by
     *   extension all descendant height fields, are kept current via
     *   updateHeight during insertions and removals.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * The height of this tree, or 0 if the tree is empty.
     */
    public int getHeight() {
        return getHeight(node);
    }

    /**
     * Retrieves the AVL balance factor of this tree's root node.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the current balance factor of the root for
     *   inspection by callers, such as for diagnostic purposes or
     *   verifying that the AVL invariant is being correctly maintained.
     * - Business context: The balance factor (left subtree height minus
     *   right subtree height) is the core metric used internally to
     *   detect when rebalancing rotations are required.
     * - Processing steps: Delegates to the private, null-safe getBalance
     *   helper, passing the internal root node reference.
     * - Assumptions: Assumes the root node's height field, and by
     *   extension all descendant height fields, are kept current via
     *   updateHeight during insertions and removals.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * The balance factor of this tree's root node, or 0 if the tree is
     * empty. A well-formed AVL tree should always report a value within
     * the range [-1, 1].
     */
    public int getBalanceFactor() {
        return getBalance(node);
    }
}