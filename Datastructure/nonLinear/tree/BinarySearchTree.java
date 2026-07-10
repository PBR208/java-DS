package nonLinear.tree;

/**
 * Purpose:
 * Provides a generic Binary Search Tree (BST) implementation for storing,
 * organizing, and retrieving ordered data efficiently.
 *
 * This implementation maintains the binary search tree invariant whereby all
 * elements located in a node's left subtree are considered smaller than the
 * node itself, while all elements located in the right subtree are considered
 * greater according to the ComparableContent comparison contract.
 *
 * The tree supports insertion, search, removal, and traversal operations.
 * Duplicate elements are intentionally prohibited to guarantee deterministic
 * lookup behavior and to preserve a unique representation of stored data.
 *
 * Thread Safety:
 * This implementation is not thread-safe. Concurrent access and modification
 * require external synchronization.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0.0
 */
public class BinarySearchTree<ContentType extends ComparableContent<ContentType>> {

	/**
	 * Represents a single node within the binary search tree.
	 *
	 * Each node stores one content element together with references to its
	 * left and right child nodes. The node structure is used internally to
	 * maintain the ordering guarantees required by the binary search tree.
	 *
	 * Lifecycle:
	 * Nodes are created during insertion operations and removed or relocated
	 * during deletion operations when tree restructuring becomes necessary.
	 */
	private class BSTNode {

		/**
		 * Value stored at the current tree position.
		 *
		 * The stored object must implement the ComparableContent contract to
		 * ensure that ordering decisions can be performed consistently.
		 */
		ContentType content;

		/**
		 * Reference to the subtree containing elements ordered before the
		 * current node.
		 */
		BSTNode left;

		/**
		 * Reference to the subtree containing elements ordered after the
		 * current node.
		 */
		BSTNode right;

		/**
		 * Creates a new tree node containing the supplied content.
		 *
		 * @param pContent
		 * Value to be stored inside the newly created node.
		 * Must not be null.
		 */
		BSTNode(ContentType pContent) {

			// Store the provided value as the content represented by this node.
			this.content = pContent;
		}
	}

	/**
	 * Root node of the binary search tree.
	 *
	 * A null value indicates that the tree currently contains no elements.
	 * All traversal, search, insertion, and deletion operations originate
	 * from this node.
	 */
	private BSTNode node;

	/**
	 * Determines whether the tree currently contains any elements.
	 *
	 * This method provides a lightweight mechanism for validating tree state
	 * before performing operations that require the existence of a root node.
	 *
	 * @return
	 * True when the tree contains no nodes.
	 * False when at least one element exists within the tree.
	 */
	public boolean isEmpty() {

		// A null root reference indicates that no elements exist in the tree.
		return node == null;
	}

	/**
	 * Inserts a new element into the binary search tree.
	 *
	 * The insertion process navigates the existing tree structure using the
	 * ordering rules defined by ComparableContent. A new node is created only
	 * when an appropriate insertion position is located.
	 *
	 * Duplicate elements are intentionally ignored to preserve uniqueness and
	 * prevent ambiguity during lookup and removal operations.
	 *
	 * @param pContent
	 * Element to be inserted into the tree.
	 * Must not be null.
	 */
	public void insert(ContentType pContent) {

		// Defensive validation prevents invalid content from entering the tree.
		if (pContent == null) {
			return;
		}

		// Start recursive insertion at the root node.
		node = insertRec(node, pContent);
	}

	/**
	 * Recursively locates the correct insertion position for a new element.
	 *
	 * The method traverses the tree according to the binary search tree
	 * ordering rules until a vacant location is found. If an equivalent
	 * element already exists, no insertion occurs.
	 *
	 * @param current
	 * Current node being evaluated during traversal.
	 * May be null when an insertion point has been reached.
	 *
	 * @param value
	 * Element that should be inserted into the tree.
	 *
	 * @return
	 * The root node of the updated subtree after insertion processing.
	 */
	private BSTNode insertRec(BSTNode current, ContentType value) {

		// Reaching a null reference indicates a valid insertion position.
		if (current == null) {

			// Create a new node to store the supplied value.
			return new BSTNode(value);
		}

		// Traverse into the left subtree when the new value is smaller.
		if (value.isLess(current.content)) {

			// Recursively continue insertion within the left branch.
			current.left = insertRec(current.left, value);

		}
		// Traverse into the right subtree when the new value is greater.
		else if (value.isGreater(current.content)) {

			// Recursively continue insertion within the right branch.
			current.right = insertRec(current.right, value);
		}

		// Duplicate values are intentionally ignored to maintain uniqueness.

		// Return the subtree root after insertion processing completes.
		return current;
	}

	/**
	 * Searches the tree for an element equivalent to the supplied value.
	 *
	 * The search operation uses the ordering contract defined by
	 * ComparableContent to eliminate irrelevant branches and efficiently
	 * locate matching nodes.
	 *
	 * @param pContent
	 * Value used as the search target.
	 * Must not be null.
	 *
	 * @return
	 * The matching stored object when found.
	 * Returns null when no equivalent element exists.
	 */
	public ContentType search(ContentType pContent) {

		// Null search requests cannot produce valid matches.
		if (pContent == null) {
			return null;
		}

		// Execute recursive search starting at the root node.
		BSTNode result = searchRec(node, pContent);

		// Return the stored content when a matching node is found.
		return result == null ? null : result.content;
	}

	/**
	 * Recursively searches for a matching node.
	 *
	 * Tree navigation is performed according to the binary search tree
	 * ordering rules, allowing large portions of the structure to be skipped.
	 *
	 * @param current
	 * Current node being evaluated.
	 *
	 * @param value
	 * Target value being searched for.
	 *
	 * @return
	 * Matching node when found; otherwise null.
	 */
	private BSTNode searchRec(BSTNode current, ContentType value) {

		// Reaching a null node indicates that the value does not exist.
		if (current == null) {
			return null;
		}

		// Return immediately when an equivalent value has been found.
		if (value.isEqual(current.content)) {
			return current;
		}

		// Continue searching within the left subtree when the target value
		// would appear before the current node.
		if (value.isLess(current.content)) {

			return searchRec(current.left, value);

		} else {

			// Continue searching within the right subtree when the target value
			// would appear after the current node.
			return searchRec(current.right, value);
		}
	}

	/**
	 * Removes an element from the binary search tree.
	 *
	 * The tree is automatically restructured when necessary to preserve all
	 * binary search tree ordering guarantees after deletion.
	 *
	 * @param pContent
	 * Element to remove from the tree.
	 * Must not be null.
	 */
	public void remove(ContentType pContent) {

		// Ignore invalid removal requests.
		if (pContent == null) {
			return;
		}

		// Begin recursive deletion from the root node.
		node = removeRec(node, pContent);
	}

	/**
	 * Recursively removes a matching node from the tree.
	 *
	 * Deletion supports three structural scenarios:
	 * 1. Leaf node removal.
	 * 2. Removal of a node with one child.
	 * 3. Removal of a node with two children.
	 *
	 * For nodes with two children, the inorder successor is used to preserve
	 * binary search tree ordering guarantees.
	 *
	 * @param current
	 * Current subtree root being evaluated.
	 *
	 * @param value
	 * Value that should be removed.
	 *
	 * @return
	 * Updated subtree root after deletion processing.
	 */
	private BSTNode removeRec(BSTNode current, ContentType value) {

		// Value cannot exist beyond a null subtree.
		if (current == null) {
			return null;
		}

		// Continue searching within the left subtree.
		if (value.isLess(current.content)) {

			current.left = removeRec(current.left, value);

		}
		// Continue searching within the right subtree.
		else if (value.isGreater(current.content)) {

			current.right = removeRec(current.right, value);

		}
		// Matching node located.
		else {

			// Case 1:
			// The node is a leaf and can be removed directly.
			if (current.left == null && current.right == null) {

				return null;
			}

			// Case 2:
			// Replace the node with its right child when no left child exists.
			if (current.left == null) {
				return current.right;
			}

			// Case 2:
			// Replace the node with its left child when no right child exists.
			if (current.right == null) {
				return current.left;
			}

			/*
			 * Case 3:
			 * The node contains two children.
			 *
			 * To preserve tree ordering, locate the inorder successor
			 * (smallest value within the right subtree), copy its content
			 * into the current node, and subsequently remove the duplicate
			 * successor node from its original location.
			 */

			// Locate the inorder successor.
			BSTNode successor = findMin(current.right);

			// Replace the deleted value with the successor value.
			current.content = successor.content;

			// Remove the duplicated successor node.
			current.right = removeRec(current.right, successor.content);
		}

		// Return the updated subtree root.
		return current;
	}

	/**
	 * Locates the smallest element contained within a subtree.
	 *
	 * Because all smaller values are positioned toward the left side of a
	 * binary search tree, the minimum value is always found by following left
	 * child references until no further left child exists.
	 *
	 * @param current
	 * Root node of the subtree being examined.
	 *
	 * @return
	 * Node containing the minimum value within the subtree.
	 */
	private BSTNode findMin(BSTNode current) {

		// Continue traversing left while smaller values may still exist.
		while (current.left != null) {

			current = current.left;
		}

		// Return the leftmost node representing the minimum value.
		return current;
	}

	/**
	 * Performs an inorder traversal of the tree.
	 *
	 * Processing order:
	 * Left Subtree -> Current Node -> Right Subtree
	 *
	 * This traversal produces values in ascending sorted order when the
	 * comparison implementation is consistent.
	 */
	public void inOrder() {

		// Begin traversal at the root node.
		inOrderRec(node);
	}

	/**
	 * Recursive implementation of inorder traversal.
	 *
	 * @param current
	 * Current node being processed.
	 */
	private void inOrderRec(BSTNode current) {

		// Terminate recursion when no node exists.
		if (current == null) {
			return;
		}

		// Process all smaller values first.
		inOrderRec(current.left);

		// Process the current node.
		visit(current.content);

		// Process all larger values afterwards.
		inOrderRec(current.right);
	}

	/**
	 * Performs a preorder traversal of the tree.
	 *
	 * Processing order:
	 * Current Node -> Left Subtree -> Right Subtree
	 *
	 * Commonly used when tree structure information must be preserved during
	 * export or serialization operations.
	 */
	public void preOrder() {

		// Begin traversal at the root node.
		preOrderRec(node);
	}

	/**
	 * Recursive implementation of preorder traversal.
	 *
	 * @param current
	 * Current node being processed.
	 */
	private void preOrderRec(BSTNode current) {

		// Stop processing when no node exists.
		if (current == null) {
			return;
		}

		// Process the current node before its children.
		visit(current.content);

		// Process the left subtree.
		preOrderRec(current.left);

		// Process the right subtree.
		preOrderRec(current.right);
	}

	/**
	 * Performs a postorder traversal of the tree.
	 *
	 * Processing order:
	 * Left Subtree -> Right Subtree -> Current Node
	 *
	 * Commonly used when child elements must be processed before their parent
	 * nodes, such as during cleanup or deletion operations.
	 */
	public void postOrder() {

		// Begin traversal at the root node.
		postOrderRec(node);
	}

	/**
	 * Recursive implementation of postorder traversal.
	 *
	 * @param current
	 * Current node being processed.
	 */
	private void postOrderRec(BSTNode current) {

		// Stop processing when no node exists.
		if (current == null) {
			return;
		}

		// Process the entire left subtree first.
		postOrderRec(current.left);

		// Process the entire right subtree second.
		postOrderRec(current.right);

		// Process the current node last.
		visit(current.content);
	}

	/**
	 * Processes a tree element during traversal operations.
	 *
	 * This method acts as a centralized output hook that can later be replaced
	 * with alternative processing strategies without modifying traversal
	 * algorithms.
	 *
	 * @param content
	 * Element currently being visited.
	 */
	private void visit(ContentType content) {

		// Output the visited element for demonstration purposes.
		System.out.println(content);
	}

	/**
	 * Provides access to the left subtree of the current root node.
	 *
	 * The returned tree shares the same underlying node structure and does not
	 * represent an independent copy of the data.
	 *
	 * @return
	 * View of the left subtree when available.
	 * Returns null when the tree is empty.
	 */
	public BinarySearchTree<ContentType> getLeftTree() {

		// An empty tree does not contain a left subtree.
		if (isEmpty()) {
			return null;
		}

		// Create a structural view referencing the existing subtree.
		BinarySearchTree<ContentType> t = new BinarySearchTree<>();

		// Share the underlying subtree root.
		t.node = node.left;

		// Return the subtree view.
		return t;
	}

	/**
	 * Provides access to the right subtree of the current root node.
	 *
	 * The returned tree shares the same underlying node structure and does not
	 * represent an independent copy of the data.
	 *
	 * @return
	 * View of the right subtree when available.
	 * Returns null when the tree is empty.
	 */
	public BinarySearchTree<ContentType> getRightTree() {

		// An empty tree does not contain a right subtree.
		if (isEmpty()) {
			return null;
		}

		// Create a structural view referencing the existing subtree.
		BinarySearchTree<ContentType> t = new BinarySearchTree<>();

		// Share the underlying subtree root.
		t.node = node.right;

		// Return the subtree view.
		return t;
	}

	/**
	 * Retrieves the content stored within the root node.
	 *
	 * This method provides read-only access to the current root element
	 * without exposing the underlying node structure.
	 *
	 * @return
	 * Content stored at the root node.
	 * Returns null when the tree is empty.
	 */
	public ContentType getContent() {

		// Return the root value when available.
		return isEmpty() ? null : node.content;
	}
}

