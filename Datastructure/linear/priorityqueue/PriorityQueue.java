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
}
