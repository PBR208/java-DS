package linear.priorityqueue;

/**
 * Generic priority queue implementation based on a priority-ordered singly
 * linked list.
 *
 * A priority queue relaxes the strict arrival ordering of a FIFO queue: elements
 * are still inserted one at a time and removed one at a time, but the element
 * handed out next is the most urgent one rather than the oldest one. This is the
 * structure behind task schedulers, event simulations and shortest-path
 * algorithms such as Dijkstra, where the next unit of work is always the one
 * with the smallest key rather than the one that happened to be submitted first.
 *
 * Priorities are expressed as plain integers passed alongside the element, and a
 * numerically smaller value means more urgent. That convention is the one used
 * by the algorithms named above, where the priority is a distance, a cost or a
 * timestamp and the smallest value is the one to process next. Priorities are
 * supplied by the caller rather than derived from the element itself, which
 * keeps this class usable with element types that carry no ordering of their own
 * and avoids coupling the linear package to the comparison contract defined for
 * the tree structures.
 *
 * The ordering invariant is maintained on insertion: the chain is kept sorted by
 * priority at all times, so removal only ever has to detach the front node. That
 * places the entire cost of the structure on enqueue, which has to locate the
 * insertion point by traversal. The opposite trade is made by a binary heap,
 * which spreads the cost evenly across both operations at O(log n) each; that
 * variant is tracked separately in the backlog as the Heap structure, and the
 * two are worth comparing directly. The sorted chain wins whenever removals
 * dominate insertions or whenever the queue stays short, and it has the
 * incidental advantage of keeping the elements in a fully sorted order rather
 * than a partially ordered one.
 *
 * Elements of equal priority are served in the order they were enqueued. This
 * stability is a deliberate guarantee rather than an accident of the
 * implementation, because a priority queue that reorders equally urgent work can
 * starve individual items indefinitely under sustained load.
 *
 * This class is not thread-safe. Concurrent modification from multiple threads
 * can interleave two insertions at the same position and lose one of them;
 * external synchronisation is required whenever instances are shared across
 * threads.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class PriorityQueue<ContentType> {

  /* ---------- Start of private inner class ---------- */

  /**
   * Internal node representation of the priority-ordered chain.
   *
   * Each node pairs one element with the priority it was enqueued under and
   * refers to the next, less urgent node. The priority is stored per node rather
   * than recomputed from the element, because the caller supplies it externally
   * and the element itself carries no ordering information.
   *
   * A singly linked node is sufficient here: the ordering invariant means that
   * removal always happens at the front, and the insertion traversal only ever
   * moves forward, so no node ever needs to reach its predecessor.
   */
  private class PriorityQueueNode {

    /** Payload stored in this node; never null, because enqueue rejects null. */
    private ContentType content = null;

    /**
     * Urgency of the stored element, where a numerically smaller value means the
     * element is served earlier. The value is fixed for the lifetime of the
     * node: re-prioritising an element would invalidate the position the node
     * was linked into, so it is deliberately not supported.
     */
    private int priority = 0;

    /**
     * Reference to the next node in priority order, or null when this node holds
     * the least urgent element currently stored.
     */
    private PriorityQueueNode nextNode = null;

    /**
     * Creates a new node pairing an element with its priority.
     *
     * The node starts detached; linking it into the chain at the position
     * dictated by its priority is the responsibility of the enqueue operation.
     * Keeping the constructor free of that logic ensures a node can never be
     * reachable from the queue before its position has been determined.
     *
     * @param pContent
     * Element to store in this node. Must not be null; enqueue filters null out
     * before a node is ever created, so this constructor does not repeat the
     * check.
     *
     * @param pPriority
     * Urgency of the element, where smaller means more urgent. Any int value is
     * accepted, including zero and negative values, so that callers can use
     * natural domain quantities such as costs, distances or timestamps directly
     * without remapping them into a restricted range.
     */
    private PriorityQueueNode(ContentType pContent, int pPriority) {
      content = pContent;
      priority = pPriority;
      nextNode = null;
    }

    /**
     * Returns the element stored in this node.
     *
     * @return
     * The payload of this node, never null for a node that is part of the queue.
     */
    public ContentType getContent() {
      return content;
    }

    /**
     * Returns the priority this node was enqueued under.
     *
     * @return
     * The urgency value of the stored element, where smaller means more urgent.
     */
    public int getPriority() {
      return priority;
    }

    /**
     * Returns the next node in priority order.
     *
     * @return
     * The successor node, or null when this node currently holds the least
     * urgent element.
     */
    public PriorityQueueNode getNext() {
      return nextNode;
    }

    /**
     * Updates the reference to the next node in priority order.
     *
     * @param pNext
     * Node that should follow this node, or null to mark this node as the end of
     * the chain.
     */
    public void setNext(PriorityQueueNode pNext) {
      nextNode = pNext;
    }
  }

  /* ---------- End of private inner class ---------- */

  /**
   * Reference to the node holding the most urgent element.
   *
   * Null exactly while the queue is empty. Because the chain is kept sorted by
   * priority, this node is by definition the one that front reports and that
   * dequeue removes, which is what makes both of those operations constant-time.
   */
  private PriorityQueueNode head;

  /**
   * Number of elements currently stored in the queue.
   *
   * The counter is maintained incrementally by enqueue and dequeue so that the
   * element count is available without walking the chain. This matters more here
   * than in a plain queue, because callers commonly use the backlog length to
   * decide whether to keep draining the queue.
   */
  private int size;

  /**
   * Creates an empty priority queue.
   *
   * No storage is reserved up front, because a linked representation allocates
   * per element rather than in blocks; an unused queue therefore costs nothing
   * beyond the object itself. The queue accepts elements of type ContentType
   * under any integer priority and imposes no capacity limit other than the
   * available heap.
   *
   * Time complexity: O(1) - two field assignments.
   * Space complexity: O(1) - no element storage is allocated.
   */
  public PriorityQueue() {
    // An empty queue has no chain at all, so there is no most urgent element.
    head = null;

    // No elements are stored yet.
    size = 0;
  }

  /**
   * Checks whether the priority queue currently holds any elements.
   *
   * This is the guard clause callers are expected to use before reading or
   * removing, because both of those operations are defined to fail silently on
   * an empty queue rather than to raise an exception. It is also the natural
   * loop condition for draining the queue, which is how a priority queue is
   * normally consumed: repeatedly take the most urgent element until none
   * remain.
   *
   * Time complexity: O(1) - a single reference comparison.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * True when no element is stored, false as soon as at least one element has
   * been enqueued and not yet removed.
   */
  public boolean isEmpty() {
    // The head reference is null exactly while the chain is empty, which tests
    // the structure itself rather than the bookkeeping counter.
    return head == null;
  }

  /**
   * Returns the number of elements currently stored in the priority queue.
   *
   * The value is maintained incrementally by enqueue and dequeue, so obtaining
   * it never walks the chain. Callers typically use it to report or bound the
   * outstanding backlog, for example to decide whether a scheduler is falling
   * behind its producers.
   *
   * Time complexity: O(1) - the count is maintained incrementally rather than
   * derived by traversal.
   * Space complexity: O(1) - no auxiliary storage.
   *
   * @return
   * Element count, zero for an empty queue and never negative.
   */
  public int size() {
    // The size counter is the single source of truth for the element count.
    return size;
  }

  /**
   * Inserts an element under the given priority.
   *
   * This operation carries the entire cost of the structure. It walks the chain
   * to find the position dictated by the priority and links the new node there,
   * so that the sorted invariant holds again when the method returns. Because
   * the invariant is restored on every insertion, removal never has to search.
   *
   * Among elements of equal priority the new one is placed behind those already
   * present, which is what gives the queue its FIFO stability for equal
   * urgencies. The alternative of inserting in front of equals would be slightly
   * cheaper, since the traversal could stop one comparison earlier, but it would
   * let a steady stream of equally urgent work indefinitely postpone the items
   * that arrived first.
   *
   * Null elements leave the queue unchanged instead of raising an exception.
   * This mirrors the behaviour of the other linear structures in this library
   * and keeps null out of the chain entirely, which is precisely what allows
   * front and frontPriority to use null unambiguously as their empty-queue
   * signal.
   *
   * Time complexity: O(n) worst case, when the new element is the least urgent
   * and the traversal reaches the end of the chain. Inserting an element that is
   * more urgent than everything stored is O(1), which makes a descending
   * sequence of priorities the best case and an ascending one the worst.
   * Space complexity: O(1) - exactly one node is allocated per stored element,
   * and the traversal itself uses a single cursor reference.
   *
   * @param pContent
   * Element to store. May be any ContentType instance; passing null is tolerated
   * and silently ignored, so callers that must distinguish "stored nothing" from
   * "stored a value" have to check for null before calling.
   *
   * @param pPriority
   * Urgency of the element, where a numerically smaller value is served earlier.
   * Any int value is valid, including zero and negative values, so that costs,
   * distances or timestamps can be passed through unmodified. Equal values are
   * permitted and resolve to insertion order.
   */
  public void enqueue(ContentType pContent, int pPriority) {
    // Reject null early: storing it would break the contract of front, which
    // reports an empty queue by returning null.
    if (pContent == null) {
      return;
    }

    // Build the node detached from the chain, so that a node whose position has
    // not been determined yet is never reachable from the queue.
    PriorityQueueNode newNode = new PriorityQueueNode(pContent, pPriority);

    if (isEmpty() || pPriority < head.getPriority()) {
      /*
       * Front insertion, covering two cases that need the same handling:
       * 1. The queue is empty, so the new node is trivially the most urgent.
       * 2. The new element outranks the current front, which is the only
       *    situation in which the head reference itself has to change.
       * The comparison is strict, so an element of equal priority to the current
       * front does not displace it and instead falls through to the traversal.
       */
      newNode.setNext(head);
      head = newNode;
    } else {
      /*
       * Locate the insertion point by walking forward while the following node
       * is at least as urgent as the new element:
       * - Inputs: a non-empty chain sorted by ascending priority, and a new
       *   element that is known not to outrank the front.
       * - The cursor stops on the last node that must still precede the new one.
       * - Using "less than or equal" as the continue condition is what places
       *   the new element behind its equals and therefore preserves arrival
       *   order among them.
       */
      PriorityQueueNode currentNode = head;

      while (currentNode.getNext() != null
          && currentNode.getNext().getPriority() <= pPriority) {
        currentNode = currentNode.getNext();
      }

      // Splice the new node in behind the cursor. The former successor is
      // reattached first so that no element is dropped out of the chain.
      newNode.setNext(currentNode.getNext());
      currentNode.setNext(newNode);
    }

    // Account for the inserted element.
    size++;
  }
}
