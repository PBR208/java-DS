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

  /**
   * Creates an empty deque.
   *
   * No storage is reserved up front, because a linked representation allocates
   * per element rather than in blocks; an unused deque therefore costs nothing
   * beyond the object itself. The deque accepts elements of type ContentType and
   * imposes no capacity limit other than the available heap.
   *
   * Time complexity: O(1) - three field assignments.
   * Space complexity: O(1) - no element storage is allocated.
   */
  public Deque() {
    // An empty deque has no chain at all, so neither end refers to a node. Both
    // references must be null together; a state in which only one of them is set
    // would be inconsistent and is never produced by any operation.
    head = null;
    tail = null;

    // No elements are stored yet.
    size = 0;
  }

  /**
   * Checks whether the deque currently holds any elements.
   *
   * This is the guard clause callers are expected to use before reading or
   * removing at either end, because all four of those operations are defined to
   * fail silently on an empty deque rather than to raise an exception. Emptiness
   * is a property of the whole structure, not of one end: a deque is either
   * empty at both ends or at neither.
   *
   * Time complexity: O(1) - a single reference comparison.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * True when no element is stored, false as soon as at least one element has
   * been inserted and not yet removed.
   */
  public boolean isEmpty() {
    // The head reference is null exactly while the chain is empty, which makes it
    // an equally valid criterion as the size counter; it is preferred here
    // because it tests the structure itself rather than the bookkeeping.
    return head == null;
  }

  /**
   * Returns the number of elements currently stored in the deque.
   *
   * The value is maintained incrementally by the insertion and removal
   * operations, so obtaining it never walks the chain. This matters for callers
   * that check the element count inside a loop, for example when a deque is used
   * as a sliding window and the window width has to be tested on every step.
   *
   * Time complexity: O(1) - the count is maintained incrementally rather than
   * derived by traversal.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * Element count, zero for an empty deque and never negative.
   */
  public int size() {
    // The size counter is the single source of truth for the element count.
    return size;
  }

  /**
   * Inserts an element at the front of the deque.
   *
   * The element becomes the one returned by the next call to first and the one
   * discarded by the next call to removeFirst. Combined with removeFirst this
   * operation drives the deque as a stack; combined with removeLast it drives it
   * as a FIFO queue in the reverse direction to addLast.
   *
   * Null arguments leave the deque unchanged instead of raising an exception.
   * This mirrors the behaviour of the other linear structures in this library
   * and keeps null out of the chain entirely, which is precisely what allows
   * first and last to use null unambiguously as their empty-deque signal.
   *
   * Time complexity: O(1) - a fixed number of reference assignments, with no
   * traversal and no reallocation, in the worst case as well as the best.
   * Space complexity: O(1) - exactly one node is allocated per stored element.
   *
   * @param pContent
   * Element to place at the front of the deque. May be any ContentType instance;
   * passing null is tolerated and silently ignored, so callers that must
   * distinguish "stored nothing" from "stored a value" have to check for null
   * before calling.
   */
  public void addFirst(ContentType pContent) {
    // Reject null early: storing it would break the contract of first and last,
    // which report an empty deque by returning null.
    if (pContent == null) {
      return;
    }

    // Build the node detached from the chain, so that a partially linked node is
    // never reachable from the deque.
    DequeNode newNode = new DequeNode(pContent);

    if (isEmpty()) {
      // The first element of an empty deque is simultaneously its front and its
      // back, so both end references have to point at the same node.
      head = newNode;
      tail = newNode;
    } else {
      // Establish both directions of the link in one step. Setting only one of
      // them would leave the chain traversable from a single end and would break
      // the removal operations, which rely on the symmetry.
      newNode.setNext(head);
      head.setPrevious(newNode);

      // The new node now precedes the former front and becomes the front itself.
      head = newNode;
    }

    // Account for the inserted element; the tail reference is deliberately left
    // untouched here, because inserting at the front never changes the back.
    size++;
  }

  /**
   * Inserts an element at the back of the deque.
   *
   * The element becomes the one returned by the next call to last and the one
   * discarded by the next call to removeLast. Combined with removeFirst this
   * operation drives the deque as a FIFO queue, which is its most common use.
   *
   * The implementation is the exact mirror image of addFirst; the two are kept
   * as separate methods rather than being folded into one parameterised helper
   * because the symmetry is easier to verify when both directions are written
   * out explicitly, and because that is where subtle end-reference bugs hide.
   *
   * Time complexity: O(1) - the maintained tail reference makes this a fixed
   * number of assignments instead of a traversal to the end of the chain.
   * Space complexity: O(1) - exactly one node is allocated per stored element.
   *
   * @param pContent
   * Element to append at the back of the deque. May be any ContentType instance;
   * passing null is tolerated and silently ignored, matching addFirst.
   */
  public void addLast(ContentType pContent) {
    // Reject null for the same reason as in addFirst: null is reserved as the
    // empty-deque signal of the read operations.
    if (pContent == null) {
      return;
    }

    // Build the node detached from the chain before linking it in.
    DequeNode newNode = new DequeNode(pContent);

    if (isEmpty()) {
      // A single element is both ends of the deque at once.
      head = newNode;
      tail = newNode;
    } else {
      // Mirror of the linking performed by addFirst, with the roles of the two
      // directions exchanged; both links are again established together.
      newNode.setPrevious(tail);
      tail.setNext(newNode);

      // The new node now follows the former back and becomes the back itself.
      tail = newNode;
    }

    // Account for the inserted element; the head reference stays valid, because
    // appending at the back never changes the front.
    size++;
  }

  /**
   * Removes the element at the front of the deque.
   *
   * Calling this method on an empty deque performs no action instead of raising
   * an underflow exception, matching the defensive contract of the other linear
   * structures in this library. Callers that need to inspect the element before
   * discarding it must read it through first, because this method intentionally
   * does not return the removed value.
   *
   * The detached node is left with its outgoing reference cleared. That is not
   * required for correctness of the deque itself, but it prevents a caller who
   * still holds a reference to the removed node from reaching the elements that
   * remain in the structure, and it avoids keeping a whole chain of removed
   * nodes alive through a single stale reference.
   *
   * Time complexity: O(1) - a fixed number of reference updates at the front.
   * Space complexity: O(1) - no auxiliary storage; the detached node becomes
   * eligible for garbage collection.
   */
  public void removeFirst() {
    // Underflow is treated as a no-op so that callers can drain the deque in a
    // loop without wrapping every call in an emptiness check.
    if (isEmpty()) {
      return;
    }

    // Remember the node being detached so its links can be cleared afterwards.
    DequeNode removedNode = head;

    // Advance the front to the successor of the removed node.
    head = head.getNext();

    if (head == null) {
      // The removed node was the only element. The deque is now empty, so the
      // back reference has to be cleared as well; leaving it pointing at the
      // detached node would resurrect that element on the next addLast.
      tail = null;
    } else {
      // The new front has no predecessor by definition. Clearing this link is
      // what actually detaches the removed node from the surviving chain.
      head.setPrevious(null);
    }

    // Cut the forward link of the detached node so that it cannot be used as a
    // path back into the deque.
    removedNode.setNext(null);

    // Account for the removal.
    size--;
  }

  /**
   * Removes the element at the back of the deque.
   *
   * This is the operation that a singly linked representation could not provide
   * in constant time, and therefore the reason this class maintains a
   * predecessor reference in every node: the new back is read directly from the
   * removed node instead of being located by traversing the chain from the
   * front.
   *
   * As with removeFirst, an empty deque is left unchanged rather than signalling
   * underflow, and the removed value is not returned; callers read it through
   * last beforehand if they need it.
   *
   * Time complexity: O(1) - a fixed number of reference updates at the back,
   * where a singly linked chain would require O(n) to find the new back.
   * Space complexity: O(1) - no auxiliary storage; the detached node becomes
   * eligible for garbage collection.
   */
  public void removeLast() {
    // Underflow is treated as a no-op, matching removeFirst.
    if (isEmpty()) {
      return;
    }

    // Remember the node being detached so its links can be cleared afterwards.
    DequeNode removedNode = tail;

    // Step the back reference to the predecessor of the removed node. This is
    // the constant-time step that the doubly linked representation exists for.
    tail = tail.getPrevious();

    if (tail == null) {
      // The removed node was the only element, so the front reference has to be
      // cleared as well to restore the consistent empty state.
      head = null;
    } else {
      // The new back has no successor by definition; clearing this link detaches
      // the removed node from the surviving chain.
      tail.setNext(null);
    }

    // Cut the backward link of the detached node so that it cannot be used as a
    // path back into the deque.
    removedNode.setPrevious(null);

    // Account for the removal.
    size--;
  }

  /**
   * Returns the element at the front of the deque without removing it.
   *
   * This is the read half of the read-then-discard pattern that the API imposes:
   * because removeFirst returns nothing, consumers inspect the front element
   * here and only afterwards remove it. The deque is left completely unmodified,
   * so repeated invocations always yield the same element.
   *
   * Time complexity: O(1) - a single reference dereference.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * The element currently at the front, or null when the deque is empty. Null is
   * unambiguous as an empty-deque marker because the insertion operations refuse
   * to store null elements.
   */
  public ContentType first() {
    // An empty deque has no front element; report that through null rather than
    // dereferencing the null head reference.
    if (isEmpty()) {
      return null;
    }

    // The head node always carries the front element while the deque is
    // non-empty.
    return head.getContent();
  }

  /**
   * Returns the element at the back of the deque without removing it.
   *
   * Mirror image of first, and the read half belonging to removeLast. Like every
   * other operation on the back, it is constant-time only because the tail
   * reference is maintained by the insertion and removal operations.
   *
   * Time complexity: O(1) - a single reference dereference, with no traversal to
   * the end of the chain.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * The element currently at the back, or null when the deque is empty. For a
   * deque holding exactly one element this returns the same element as first,
   * because both ends then refer to the same node.
   */
  public ContentType last() {
    // An empty deque has no back element; report that through null rather than
    // dereferencing the null tail reference.
    if (isEmpty()) {
      return null;
    }

    // The tail node always carries the back element while the deque is
    // non-empty.
    return tail.getContent();
  }
}
