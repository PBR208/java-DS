package linear.deque;

/**
 * Generic double-ended queue (deque) implementation based on a doubly linked
 * list.
 *
 * A deque generalises both of the restricted linear structures that already
 * exist in this library: it accepts insertions and removals at the front as well
 * as at the back, so it can be operated as a stack by using a single end, as a
 * FIFO queue by inserting at one end and removing at the other, or as a sliding
 * window by working on both ends at once. Because every one of those access
 * patterns must stay constant-time, no end of the structure may be privileged
 * over the other.
 *
 * That requirement is what dictates the doubly linked representation. A singly
 * linked list can append at the tail in constant time, but it cannot remove the
 * tail in constant time: after unlinking the last node it has no way to identify
 * the new last node without walking the entire list from the front. Storing a
 * predecessor reference in every node buys exactly that missing information, at
 * the cost of one additional reference per element.
 *
 * Elements are never copied or shifted by any operation, and no reallocation
 * ever occurs, which makes the worst case of every operation constant rather
 * than merely amortised. The trade-off against an array-backed deque is the
 * per-element node allocation and the loss of memory locality.
 *
 * This class is not thread-safe. Concurrent modification from multiple threads
 * can leave the head and tail references pointing into inconsistent parts of the
 * chain; external synchronisation is required whenever instances are shared
 * across threads.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class Deque<ContentType> {

  /* ---------- Start of private inner class ---------- */

  /**
   * Internal node representation of the doubly linked chain backing the deque.
   *
   * Each node owns one element and knows both of its neighbours, which is what
   * allows the deque to detach a node from either end without traversing the
   * chain. Nodes are created exclusively by the insertion operations and become
   * unreachable as soon as they are unlinked, so their lifetime is bounded by
   * the lifetime of the element they carry.
   */
  private class DequeNode {

    /** Payload stored in this node; never null, because insertions reject null. */
    private ContentType content = null;

    /** Reference to the preceding node, or null when this node is the front. */
    private DequeNode previousNode = null;

    /** Reference to the following node, or null when this node is the back. */
    private DequeNode nextNode = null;

    /**
     * Creates a new deque node holding the specified element.
     *
     * The node starts detached: both neighbour references are null, and it is
     * the responsibility of the calling insertion operation to link the node
     * into the chain. Keeping the constructor free of linking logic ensures that
     * a half-built node can never be reachable from the deque.
     *
     * @param pContent
     * Element to store in this node. Must not be null; the insertion operations
     * filter null out before a node is ever created, so this constructor does
     * not repeat that check.
     */
    private DequeNode(ContentType pContent) {
      content = pContent;
      previousNode = null;
      nextNode = null;
    }

    /**
     * Returns the element stored in this node.
     *
     * @return
     * The payload of this node, never null for a node that is part of the deque.
     */
    public ContentType getContent() {
      return content;
    }

    /**
     * Returns the node preceding this one, moving towards the front.
     *
     * @return
     * The predecessor node, or null when this node is currently the front of the
     * deque.
     */
    public DequeNode getPrevious() {
      return previousNode;
    }

    /**
     * Updates the reference to the preceding node.
     *
     * Callers must keep the chain symmetric: whenever a node is set as the
     * predecessor of another, that other node has to be set as its successor as
     * well, otherwise traversal in one direction would skip elements.
     *
     * @param pPrevious
     * Node that should precede this node, or null to mark this node as the new
     * front of the deque.
     */
    public void setPrevious(DequeNode pPrevious) {
      previousNode = pPrevious;
    }

    /**
     * Returns the node following this one, moving towards the back.
     *
     * @return
     * The successor node, or null when this node is currently the back of the
     * deque.
     */
    public DequeNode getNext() {
      return nextNode;
    }

    /**
     * Updates the reference to the following node.
     *
     * The same symmetry obligation as for setPrevious applies: both directions
     * of the link have to be established together to keep the chain traversable
     * from either end.
     *
     * @param pNext
     * Node that should follow this node, or null to mark this node as the new
     * back of the deque.
     */
    public void setNext(DequeNode pNext) {
      nextNode = pNext;
    }
  }

  /* ---------- End of private inner class ---------- */

  /**
   * Reference to the node at the front of the deque.
   *
   * Null exactly while the deque is empty. This is the entry point for every
   * operation that works on the front, and it is the only node in the chain
   * whose predecessor reference is null.
   */
  private DequeNode head;

  /**
   * Reference to the node at the back of the deque.
   *
   * Null exactly while the deque is empty, and identical to the head reference
   * while the deque holds a single element. Maintaining this reference is what
   * turns an insertion or removal at the back into a constant-time operation
   * instead of a traversal.
   */
  private DequeNode tail;

  /**
   * Number of elements currently stored in the deque.
   *
   * The counter is maintained incrementally by every insertion and removal so
   * that the element count is available without walking the chain. It is
   * redundant with the structure itself, which is a deliberate trade of a few
   * bytes for a constant-time size query.
   */
  private int size;
}
