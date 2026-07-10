package nonLinear.tree;

/**
 * Purpose:
 * Implements a generic, recursively-structured binary tree data structure
 * in which every subtree is itself represented as a BinaryTree instance,
 * rather than relying on plain node-to-node references. This design allows
 * empty subtrees to be represented uniformly as BinaryTree instances with
 * no internal node, eliminating the need for explicit null-checks against
 * raw node references throughout the traversal and mutation logic. The
 * class supports construction of single-node trees, trees with pre-built
 * left and right subtrees, and provides the three classical depth-first
 * traversal strategies (pre-order, in-order, post-order) for visiting and
 * printing tree contents. It serves as a foundational, reusable data
 * structure for hierarchical data representation within the non-linear
 * data structures package, generic over the content type stored at each
 * node.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Generic binary tree implementation where every subtree is itself a
 * BinaryTree instance, enabling recursive, node-free representation of
 * empty subtrees.
 *
 * Responsibility: Encapsulates the structural representation of a binary
 * tree, including content storage, left/right subtree management, and the
 * three classical depth-first traversal orders (pre-order, in-order,
 * post-order).
 *
 * Scope: Used within the nonLinear.tree package as a general-purpose,
 * reusable hierarchical data structure, generic over the content type
 * ContentType.
 *
 * Dependencies: Has no external dependencies; relies only on its own
 * private nested BTNode class to hold per-node content and subtree
 * references.
 *
 * Thread-safety: This class is not thread-safe. Concurrent structural
 * modification (setContent, setLeftTree, setRightTree) or traversal from
 * multiple threads without external synchronization may result in
 * inconsistent state or traversal errors.
 *
 * Lifecycle: A BinaryTree instance may begin empty (no content) or
 * pre-populated via a parameterized constructor; it may be mutated over
 * its lifetime by attaching content and subtrees, and subtrees may be
 * shared or reattached between different BinaryTree instances since
 * getLeftTree/getRightTree and setLeftTree/setRightTree operate on direct
 * BinaryTree references rather than defensive copies.
 *
 * Architectural role: Serves as a foundational, generic hierarchical data
 * structure that may be consumed by higher-level algorithms requiring
 * ordered or hierarchical data traversal within the system.
 *
 * @param <ContentType>
 * The type of content stored at each node of the tree.
 */
public class BinaryTree<ContentType> {

	/**
	 * Purpose:
	 * Represents a single occupied node within the enclosing BinaryTree,
	 * bundling the stored content together with its left and right
	 * subtrees. Because the left and right subtree fields are themselves
	 * BinaryTree instances rather than raw BTNode references, every node
	 * uniformly delegates the representation of "no child" to an empty
	 * BinaryTree, simplifying traversal logic by avoiding raw null checks
	 * against child nodes.
	 *
	 * Owner:
	 * P.B.R. - https://github.com/PBR208/
	 *
	 * Version:
	 * 1.0
	 */

	/**
	 * Represents a single occupied node of the binary tree, holding
	 * content and references to its left and right subtrees.
	 *
	 * Responsibility: Stores the content associated with this node and the
	 * two BinaryTree instances representing its left and right children.
	 *
	 * Scope: Private to the enclosing BinaryTree class; not exposed
	 * outside this file.
	 *
	 * Dependencies: Depends on the enclosing BinaryTree class to represent
	 * its left and right subtree references.
	 *
	 * Thread-safety: Not thread-safe; mutation of content, left, or right
	 * fields from multiple threads without external synchronization may
	 * result in inconsistent state.
	 *
	 * Lifecycle: A BTNode is created only when content is assigned to a
	 * previously empty BinaryTree, and exists for as long as that
	 * BinaryTree instance retains content.
	 *
	 * Architectural role: Acts as the internal storage unit backing each
	 * occupied position within a BinaryTree.
	 */
	private class BTNode {

		// Stores the content value held at this node. Represents the
		// actual data payload associated with this position in the tree.
		ContentType content;

		// References the left subtree of this node. Always a valid
		// BinaryTree instance (never a raw null), which may itself be
		// empty to represent the absence of a left child.
		BinaryTree<ContentType> left;

		// References the right subtree of this node. Always a valid
		// BinaryTree instance (never a raw null), which may itself be
		// empty to represent the absence of a right child.
		BinaryTree<ContentType> right;

		/**
		 * Constructs a new BTNode holding the specified content, with both
		 * child subtrees initialized to empty BinaryTree instances.
		 *
		 * Detailed explanation of:
		 * - Purpose: Initializes a newly occupied tree position with its
		 *   content and ensures both children default to a well-defined,
		 *   empty state rather than being left null.
		 * - Business context: Represents the creation of a new data point
		 *   within the hierarchical tree structure.
		 * - Processing steps: Assigns the supplied content to the internal
		 *   content field, then initializes both the left and right fields
		 *   to newly allocated, empty BinaryTree instances.
		 * - Assumptions: Assumes the caller has already validated that
		 *   pContent is non-null, since this constructor performs no
		 *   validation of its own.
		 * - Side effects: Allocates two new empty BinaryTree instances to
		 *   serve as the initial left and right subtrees.
		 *
		 * @param pContent
		 * The content value to store at this node. Expected to be
		 * non-null, as validation is performed by the calling BinaryTree
		 * constructor or mutator prior to invocation.
		 */
		BTNode(ContentType pContent) {
			// Assign the supplied content as this node's payload.
			this.content = pContent;

			// Initialize the left subtree to a new, empty BinaryTree,
			// representing the absence of a left child by default.
			this.left = new BinaryTree<>();

			// Initialize the right subtree to a new, empty BinaryTree,
			// representing the absence of a right child by default.
			this.right = new BinaryTree<>();
		}
	}

	// References the occupied node backing this BinaryTree instance, or
	// null when this tree represents an empty tree or subtree. This single
	// field is the basis for the isEmpty() check used throughout the
	// class.
	private BTNode node;

	/**
	 * Constructs a new, empty BinaryTree with no content.
	 *
	 * Detailed explanation of:
	 * - Purpose: Initializes a BinaryTree instance representing an empty
	 *   tree, used both as a standalone empty tree and as the default
	 *   value for unpopulated child subtrees.
	 * - Business context: Represents the base case for hierarchical data
	 *   construction, allowing trees to be built up incrementally starting
	 *   from an empty state.
	 * - Processing steps: Sets the internal node reference to null.
	 * - Assumptions: None; this constructor takes no parameters.
	 * - Side effects: None beyond initialization of instance state.
	 */
	public BinaryTree() {
		node = null;
	}

	/**
	 * Constructs a new BinaryTree containing a single node with the
	 * specified content and no children.
	 *
	 * Detailed explanation of:
	 * - Purpose: Initializes a BinaryTree representing a single occupied
	 *   node, with both left and right subtrees defaulting to empty.
	 * - Business context: Represents the creation of a standalone data
	 *   point, typically used as a leaf node or as the starting point for
	 *   further tree construction.
	 * - Processing steps: If the supplied content is non-null, creates a
	 *   new BTNode wrapping that content (which internally initializes
	 *   both child subtrees to empty). If the content is null, the tree
	 *   remains empty (node stays null, per default field initialization).
	 * - Assumptions: Assumes a null content value indicates the caller
	 *   intends to create an empty tree rather than an error condition;
	 *   no exception is raised for null input.
	 * - Side effects: Allocates a new BTNode, which in turn allocates two
	 *   new empty BinaryTree instances for its children, when pContent is
	 *   non-null.
	 *
	 * @param pContent
	 * The content value to store at the root of this tree. If null, the
	 * resulting tree is empty.
	 */
	public BinaryTree(ContentType pContent) {

		// Only create an occupied node if valid content was supplied;
		// otherwise the tree remains in its default empty state.
		if (pContent != null) {
			node = new BTNode(pContent);
		}
	}

	/**
	 * Constructs a new BinaryTree with the specified content and
	 * pre-built left and right subtrees.
	 *
	 * Detailed explanation of:
	 * - Purpose: Initializes a BinaryTree representing a fully specified
	 *   node, allowing existing subtrees to be attached directly at
	 *   construction time rather than built incrementally afterward.
	 * - Business context: Supports construction of complex hierarchical
	 *   structures in a single step, such as when assembling a tree from
	 *   previously constructed subtrees.
	 * - Processing steps: If the supplied content is non-null, creates a
	 *   new BTNode wrapping that content, then overwrites the node's
	 *   default empty left and right subtrees with the supplied
	 *   pLeftTree and pRightTree respectively, falling back to a newly
	 *   allocated empty BinaryTree for either subtree that is null.
	 * - Assumptions: Assumes a null content value indicates the caller
	 *   intends to create an empty tree rather than an error condition,
	 *   in which case the supplied subtree arguments are silently
	 *   discarded and the tree remains empty.
	 * - Side effects: Allocates a new BTNode (which itself allocates two
	 *   default empty BinaryTree instances that are then immediately
	 *   replaced or reused) and potentially allocates additional empty
	 *   BinaryTree instances for any null subtree arguments, when
	 *   pContent is non-null.
	 *
	 * @param pContent
	 * The content value to store at the root of this tree. If null, the
	 * resulting tree is empty and the subtree parameters are ignored.
	 * @param pLeftTree
	 * The subtree to attach as the left child. If null, an empty
	 * BinaryTree is used instead.
	 * @param pRightTree
	 * The subtree to attach as the right child. If null, an empty
	 * BinaryTree is used instead.
	 */
	public BinaryTree(
			ContentType pContent,
			BinaryTree<ContentType> pLeftTree,
			BinaryTree<ContentType> pRightTree) {

		// Only construct an occupied node if valid content was supplied;
		// otherwise the tree remains empty and the subtree arguments are
		// not attached.
		if (pContent != null) {

			// Create the node wrapping the supplied content; this also
			// initializes default empty left/right subtrees internally.
			node = new BTNode(pContent);

			// Attach the supplied left subtree if provided, otherwise fall
			// back to a new empty BinaryTree to preserve the invariant that
			// left is never a raw null.
			node.left =
					(pLeftTree != null)
							? pLeftTree
							: new BinaryTree<>();

			// Attach the supplied right subtree if provided, otherwise fall
			// back to a new empty BinaryTree to preserve the invariant that
			// right is never a raw null.
			node.right =
					(pRightTree != null)
							? pRightTree
							: new BinaryTree<>();
		}
	}

	/**
	 * Determines whether this BinaryTree instance represents an empty
	 * tree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Provides the central check used throughout this class,
	 *   and by external callers, to determine whether a node is present
	 *   at this position in the tree.
	 * - Business context: Enables both internal methods and external
	 *   client code to safely branch behavior depending on whether a tree
	 *   or subtree currently holds content.
	 * - Processing steps: Directly evaluates whether the internal node
	 *   reference is null.
	 * - Assumptions: Assumes the node field accurately reflects the
	 *   current occupancy state of this tree instance.
	 * - Side effects: None; this method does not modify internal state.
	 *
	 * @return
	 * True if this tree currently holds no content (node is null); false
	 * if a node is present.
	 */
	public boolean isEmpty() {
		return node == null;
	}

	/**
	 * Retrieves the content stored at the root of this tree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Exposes the data payload held at this tree's node, if
	 *   any, for inspection by callers.
	 * - Business context: Used by traversal logic and external client
	 *   code to read the value associated with a given tree position.
	 * - Processing steps: Returns null immediately if this tree is empty;
	 *   otherwise returns the content field of the underlying node.
	 * - Assumptions: Assumes isEmpty() accurately reflects node occupancy.
	 * - Side effects: None; this method does not modify internal state.
	 *
	 * @return
	 * The content stored at this tree's node, or null if this tree is
	 * empty.
	 */
	public ContentType getContent() {
		return isEmpty() ? null : node.content;
	}

	/**
	 * Sets or replaces the content stored at the root of this tree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Allows the data payload at this tree position to be
	 *   established (if currently empty) or updated (if already
	 *   occupied).
	 * - Business context: Supports incremental construction and later
	 *   modification of tree contents without requiring the caller to
	 *   rebuild the surrounding tree structure.
	 * - Processing steps: Returns immediately without effect if the
	 *   supplied content is null. If this tree is currently empty, creates
	 *   a new BTNode wrapping the supplied content (which also
	 *   initializes default empty child subtrees). If this tree already
	 *   holds a node, simply overwrites the existing content field,
	 *   leaving the existing left and right subtrees untouched.
	 * - Assumptions: Assumes a null content value indicates the caller
	 *   does not intend to modify the tree; silently ignores such calls
	 *   rather than raising an exception or clearing existing content.
	 * - Side effects: Mutates this tree's node reference (allocating a new
	 *   BTNode) if previously empty, or mutates the existing node's
	 *   content field if already occupied.
	 *
	 * @param pContent
	 * The new content value to store at this tree's node. If null, this
	 * method performs no action and existing content, if any, is left
	 * unchanged.
	 */
	public void setContent(ContentType pContent) {

		// Ignore null content rather than clearing existing state or
		// raising an exception.
		if (pContent == null) {
			return;
		}

		// If this tree currently has no node, create one to hold the new
		// content, which also establishes default empty child subtrees.
		if (isEmpty()) {
			node = new BTNode(pContent);
		} else {
			// A node already exists; simply update its content in place,
			// preserving the existing left and right subtrees.
			node.content = pContent;
		}
	}

	/**
	 * Retrieves the left subtree of this tree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Exposes the left child subtree for inspection or further
	 *   traversal by callers.
	 * - Business context: Used by traversal algorithms and external
	 *   client code to navigate into the left branch of the hierarchical
	 *   structure.
	 * - Processing steps: Returns null immediately if this tree is empty;
	 *   otherwise returns the left field of the underlying node directly
	 *   (not a defensive copy).
	 * - Assumptions: Assumes isEmpty() accurately reflects node occupancy.
	 *   Note that because the returned reference is not a defensive copy,
	 *   callers who mutate the returned subtree will affect this tree's
	 *   actual left subtree.
	 * - Side effects: None; this method does not modify internal state,
	 *   though the returned reference grants the caller direct mutation
	 *   access to the underlying subtree.
	 *
	 * @return
	 * The BinaryTree instance representing this tree's left subtree, or
	 * null if this tree itself is empty. Note that when this tree is
	 * occupied, the returned left subtree may itself be empty but will
	 * never be a raw null in that case.
	 */
	public BinaryTree<ContentType> getLeftTree() {
		return isEmpty() ? null : node.left;
	}

	/**
	 * Sets the left subtree of this tree to the specified BinaryTree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Allows the left child branch of this tree to be attached
	 *   or replaced with a different subtree.
	 * - Business context: Supports incremental or corrective construction
	 *   of hierarchical structures, such as attaching a previously built
	 *   subtree to an existing node.
	 * - Processing steps: Performs the assignment only if this tree is
	 *   currently non-empty (has a node to attach the subtree to) and the
	 *   supplied subtree is non-null; otherwise the call has no effect.
	 * - Assumptions: Assumes that attempting to set a child subtree on an
	 *   empty tree, or supplying a null subtree, indicates an invalid or
	 *   no-op request rather than an error condition; no exception is
	 *   raised in either case.
	 * - Side effects: Mutates the left field of this tree's underlying
	 *   node when both preconditions are satisfied; discards the
	 *   previously attached left subtree reference.
	 *
	 * @param pTree
	 * The BinaryTree instance to attach as the left subtree. If null, or
	 * if this tree is currently empty, this method performs no action.
	 */
	public void setLeftTree(BinaryTree<ContentType> pTree) {

		// Only perform the assignment when this tree has a node to attach
		// to and a valid subtree was supplied.
		if (!isEmpty() && pTree != null) {
			node.left = pTree;
		}
	}

	/**
	 * Retrieves the right subtree of this tree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Exposes the right child subtree for inspection or
	 *   further traversal by callers.
	 * - Business context: Used by traversal algorithms and external
	 *   client code to navigate into the right branch of the hierarchical
	 *   structure.
	 * - Processing steps: Returns null immediately if this tree is empty;
	 *   otherwise returns the right field of the underlying node directly
	 *   (not a defensive copy).
	 * - Assumptions: Assumes isEmpty() accurately reflects node occupancy.
	 *   Note that because the returned reference is not a defensive copy,
	 *   callers who mutate the returned subtree will affect this tree's
	 *   actual right subtree.
	 * - Side effects: None; this method does not modify internal state,
	 *   though the returned reference grants the caller direct mutation
	 *   access to the underlying subtree.
	 *
	 * @return
	 * The BinaryTree instance representing this tree's right subtree, or
	 * null if this tree itself is empty. Note that when this tree is
	 * occupied, the returned right subtree may itself be empty but will
	 * never be a raw null in that case.
	 */
	public BinaryTree<ContentType> getRightTree() {
		return isEmpty() ? null : node.right;
	}

	/**
	 * Sets the right subtree of this tree to the specified BinaryTree.
	 *
	 * Detailed explanation of:
	 * - Purpose: Allows the right child branch of this tree to be
	 *   attached or replaced with a different subtree.
	 * - Business context: Supports incremental or corrective construction
	 *   of hierarchical structures, such as attaching a previously built
	 *   subtree to an existing node.
	 * - Processing steps: Performs the assignment only if this tree is
	 *   currently non-empty (has a node to attach the subtree to) and the
	 *   supplied subtree is non-null; otherwise the call has no effect.
	 * - Assumptions: Assumes that attempting to set a child subtree on an
	 *   empty tree, or supplying a null subtree, indicates an invalid or
	 *   no-op request rather than an error condition; no exception is
	 *   raised in either case.
	 * - Side effects: Mutates the right field of this tree's underlying
	 *   node when both preconditions are satisfied; discards the
	 *   previously attached right subtree reference.
	 *
	 * @param pTree
	 * The BinaryTree instance to attach as the right subtree. If null, or
	 * if this tree is currently empty, this method performs no action.
	 */
	public void setRightTree(BinaryTree<ContentType> pTree) {

		// Only perform the assignment when this tree has a node to attach
		// to and a valid subtree was supplied.
		if (!isEmpty() && pTree != null) {
			node.right = pTree;
		}
	}

	/**
	 * Initiates a pre-order (root, left, right) traversal of this tree,
	 * printing each visited node's content.
	 *
	 * Detailed explanation of:
	 * - Purpose: Provides a public entry point for pre-order traversal
	 *   without requiring the caller to interact with internal BTNode
	 *   references.
	 * - Business context: Pre-order traversal is commonly used when the
	 *   processing order needs to visit a parent before its children, such
	 *   as when copying or serializing a tree structure.
	 * - Processing steps: Delegates immediately to the private recursive
	 *   helper preOrderRec, passing this tree's internal node reference as
	 *   the starting point.
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
	 *   content first, then recurses into the left subtree's internal
	 *   node, then recurses into the right subtree's internal node.
	 * - Assumptions: Assumes that left and right subtree fields are
	 *   always valid BinaryTree instances (never raw null), such that
	 *   accessing current.left.node and current.right.node is always safe
	 *   even when those subtrees are themselves empty.
	 * - Side effects: Produces console output via the visit method for
	 *   every visited node; consumes call stack space proportional to
	 *   tree depth due to recursion.
	 *
	 * @param current
	 * The BTNode from which to begin the pre-order traversal. If null,
	 * the recursion terminates immediately (base case).
	 */
	private void preOrderRec(BTNode current) {

		// Base case: an empty subtree contributes nothing to the
		// traversal.
		if (current == null) {
			return;
		}

		// Visit (print) the current node's content before descending into
		// its children, satisfying the root-first pre-order sequence.
		visit(current.content);

		// Recurse into the left subtree's internal node.
		preOrderRec(current.left.node);

		// Recurse into the right subtree's internal node.
		preOrderRec(current.right.node);
	}

	/**
	 * Initiates an in-order (left, root, right) traversal of this tree,
	 * printing each visited node's content.
	 *
	 * Detailed explanation of:
	 * - Purpose: Provides a public entry point for in-order traversal
	 *   without requiring the caller to interact with internal BTNode
	 *   references.
	 * - Business context: In-order traversal is commonly used when nodes
	 *   must be processed in a sorted sequence, such as when the tree
	 *   represents a binary search tree.
	 * - Processing steps: Delegates immediately to the private recursive
	 *   helper inOrderRec, passing this tree's internal node reference as
	 *   the starting point.
	 * - Assumptions: Assumes the internal node field accurately reflects
	 *   the current tree structure.
	 * - Side effects: Produces console output via the visit method for
	 *   every node in the tree, in in-order sequence.
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
	 *   sequence.
	 * - Business context: Supports algorithms and diagnostic output that
	 *   require nodes to be processed in ascending sorted order, typically
	 *   when the tree maintains binary search tree ordering invariants.
	 * - Processing steps: Returns immediately if the current node is null
	 *   (base case: empty subtree). Otherwise, recurses into the left
	 *   subtree's internal node first, then visits the current node's
	 *   content, then recurses into the right subtree's internal node.
	 * - Assumptions: Assumes that left and right subtree fields are
	 *   always valid BinaryTree instances (never raw null), such that
	 *   accessing current.left.node and current.right.node is always safe
	 *   even when those subtrees are themselves empty.
	 * - Side effects: Produces console output via the visit method for
	 *   every visited node; consumes call stack space proportional to
	 *   tree depth due to recursion.
	 *
	 * @param current
	 * The BTNode from which to begin the in-order traversal. If null, the
	 * recursion terminates immediately (base case).
	 */
	private void inOrderRec(BTNode current) {

		// Base case: an empty subtree contributes nothing to the
		// traversal.
		if (current == null) {
			return;
		}

		// Recurse into the left subtree's internal node before visiting
		// the current node, satisfying the left-first in-order sequence.
		inOrderRec(current.left.node);

		// Visit (print) the current node's content after the entire left
		// subtree has been processed.
		visit(current.content);

		// Recurse into the right subtree's internal node after the current
		// node has been visited.
		inOrderRec(current.right.node);
	}

	/**
	 * Initiates a post-order (left, right, root) traversal of this tree,
	 * printing each visited node's content.
	 *
	 * Detailed explanation of:
	 * - Purpose: Provides a public entry point for post-order traversal
	 *   without requiring the caller to interact with internal BTNode
	 *   references.
	 * - Business context: Post-order traversal is commonly used when
	 *   children must be fully processed before their parent, such as
	 *   when deleting a tree or evaluating an expression tree.
	 * - Processing steps: Delegates immediately to the private recursive
	 *   helper postOrderRec, passing this tree's internal node reference
	 *   as the starting point.
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
	 *   require both children to be fully processed before their parent,
	 *   such as safe deletion or bottom-up evaluation of hierarchical
	 *   structures.
	 * - Processing steps: Returns immediately if the current node is null
	 *   (base case: empty subtree). Otherwise, recurses into the left
	 *   subtree's internal node, then recurses into the right subtree's
	 *   internal node, and only then visits the current node's content.
	 * - Assumptions: Assumes that left and right subtree fields are
	 *   always valid BinaryTree instances (never raw null), such that
	 *   accessing current.left.node and current.right.node is always safe
	 *   even when those subtrees are themselves empty.
	 * - Side effects: Produces console output via the visit method for
	 *   every visited node; consumes call stack space proportional to
	 *   tree depth due to recursion.
	 *
	 * @param current
	 * The BTNode from which to begin the post-order traversal. If null,
	 * the recursion terminates immediately (base case).
	 */
	private void postOrderRec(BTNode current) {

		// Base case: an empty subtree contributes nothing to the
		// traversal.
		if (current == null) {
			return;
		}

		// Recurse into the left subtree's internal node first, fully
		// processing it before moving on.
		postOrderRec(current.left.node);

		// Recurse into the right subtree's internal node next, fully
		// processing it before visiting the current node.
		postOrderRec(current.right.node);

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
	 *   traversal method (preOrder, inOrder, postOrder) when a node is
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
}