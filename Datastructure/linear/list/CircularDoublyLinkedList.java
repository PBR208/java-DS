package linear.list;

/**
 * Generic circular doubly linked list implementation with a single movable
 * cursor ("current" element).
 *
 * The list manages a sequence of ContentType elements in a ring: the tail node
 * links forward to the head and the head links backward to the tail, so the
 * sequence has no endpoints and the cursor can traverse indefinitely in either
 * direction. Each node holds a reference to both of its neighbours, which is the
 * property that distinguishes this structure from the CircularSinglyLinkedList
 * in the same package.
 *
 * That second reference buys two things the singly linked ring cannot provide.
 * The cursor can move backwards in constant time through previous(), and a node
 * can be unlinked without first locating its predecessor by traversal, which
 * turns insertion and removal at an arbitrary cursor position from linear into
 * constant time. The cost is one additional reference per stored element.
 *
 * Only one element can be active (current) at any time. If the list is empty, or
 * the current element is removed and the list becomes empty, no active element
 * exists.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.1
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class CircularDoublyLinkedList<ContentType> {

    /* ---------- Start of private inner class ---------- */

    /**
     * Internal node representation of the circular doubly linked list.
     *
     * Each node owns one element and knows both of its neighbours. In a
     * non-empty ring neither reference is ever null: a list holding a single
     * element has that element linked to itself in both directions, which
     * removes the endpoint special cases that a null-terminated chain requires.
     */
    private class ListNode {

        /** Payload stored in this node; never null, because insertions reject null. */
        private ContentType contentObject;

        /** Reference to the following node in the ring; never null while linked. */
        private ListNode next;

        /** Reference to the preceding node in the ring; never null while linked. */
        private ListNode previous;

        /**
         * Creates a new node holding the given content.
         *
         * The node starts detached: both neighbour references are null, and the
         * calling insertion operation is responsible for linking it into the
         * ring. Keeping the constructor free of linking logic ensures a
         * half-built node can never be reachable from the list.
         *
         * @param pContent the value stored in this node; must not be null
         */
        private ListNode(ContentType pContent) {
            contentObject = pContent;
            next = null;
            previous = null;
        }

        /**
         * Returns the value stored in this node.
         *
         * @return the content of this node
         */
        public ContentType getContentObject() {
            return contentObject;
        }

        /**
         * Updates the value stored in this node.
         *
         * @param pContent the new content value
         */
        public void setContentObject(ContentType pContent) {
            contentObject = pContent;
        }

        /**
         * Returns the next node in the ring.
         *
         * @return successor node, which wraps to the head once the tail is
         *         reached; null only for a detached node
         */
        public ListNode getNextNode() {
            return this.next;
        }

        /**
         * Sets the next node reference.
         *
         * Callers must keep the ring symmetric: whenever a node is set as the
         * successor of another, that other node has to be set as its
         * predecessor as well, otherwise the two traversal directions would
         * disagree about the contents of the list.
         *
         * @param pNext the node to link as successor
         */
        public void setNextNode(ListNode pNext) {
            this.next = pNext;
        }

        /**
         * Returns the previous node in the ring.
         *
         * @return predecessor node, which wraps to the tail once the head is
         *         reached; null only for a detached node
         */
        public ListNode getPreviousNode() {
            return this.previous;
        }

        /**
         * Sets the previous node reference.
         *
         * The same symmetry obligation as for setNextNode applies.
         *
         * @param pPrevious the node to link as predecessor
         */
        public void setPreviousNode(ListNode pPrevious) {
            this.previous = pPrevious;
        }
    }

    /* ---------- End of private inner class ---------- */

    // Head (first element) of the ring; null exactly while the list is empty.
    ListNode head;

    // Tail (last element) of the ring. In a non-empty list its successor is
    // always the head, which is what closes the ring.
    ListNode tail;

    // Currently active element (cursor); null when no element is active.
    ListNode current;

    /**
     * Creates an empty circular list.
     */
    public CircularDoublyLinkedList() {
        head = null;
        tail = null;
        current = null;
    }

    /**
     * Checks whether the list is empty.
     *
     * @return true if the list contains no elements, otherwise false
     */
    public boolean isEmpty() {
        return head == null;
    }

    /**
     * Checks whether a current element is set.
     *
     * @return true if a current element exists, otherwise false
     */
    public boolean hasAccess() {
        return current != null;
    }

    /**
     * Moves the cursor to the next element.
     *
     * In a ring there is no end to fall off: advancing past the tail wraps the
     * cursor around to the head. If no current element is set the call is
     * ignored.
     */
    public void next() {
        if (this.hasAccess()) {
            current = current.getNextNode();
        }
    }

    /**
     * Moves the cursor to the previous element.
     *
     * This is the operation the singly linked ring cannot offer in constant
     * time, and the reason each node stores a predecessor reference. Moving back
     * from the head wraps the cursor around to the tail. If no current element
     * is set the call is ignored.
     */
    public void previous() {
        if (this.hasAccess()) {
            current = current.getPreviousNode();
        }
    }

    /**
     * Moves the cursor to the first element of the list.
     */
    public void toFirst() {
        if (!isEmpty()) {
            current = head;
        }
    }

    /**
     * Moves the cursor to the last element of the list.
     */
    public void toLast() {
        if (!isEmpty()) {
            current = tail;
        }
    }

    /**
     * Returns the content of the current element.
     *
     * @return current element content or null if no active element exists
     */
    public ContentType getContent() {
        if (this.hasAccess()) {
            return current.getContentObject();
        } else {
            return null;
        }
    }

    /**
     * Replaces the content of the current element.
     *
     * No operation is performed if there is no current element or if the
     * provided content is null.
     *
     * @param pContent new value to store in the current element
     */
    public void setContent(ContentType pContent) {
        if (pContent != null && this.hasAccess()) {
            current.setContentObject(pContent);
        }
    }

    /**
     * Inserts a new element before the current element.
     *
     * Because both neighbours of the current element are known directly, the
     * splice is a fixed number of reference assignments and needs no traversal.
     * If the current element is the head, the new element becomes the new head
     * while the ring stays closed through the tail.
     *
     * If the list is empty, the new element becomes the only element and links
     * to itself in both directions; no current element is set. If no current
     * element exists and the list is not empty, the operation is ignored.
     *
     * @param pContent element to insert; null is ignored
     */
    public void insert(ContentType pContent) {
        if (pContent != null) {

            if (this.hasAccess()) {

                ListNode newNode = new ListNode(pContent);

                // Both neighbours of the insertion point are known without a
                // scan, which is the advantage the predecessor reference buys.
                ListNode predecessor = current.getPreviousNode();

                newNode.setPreviousNode(predecessor);
                newNode.setNextNode(current);

                // Close both directions of the link before touching the cursor.
                predecessor.setNextNode(newNode);
                current.setPreviousNode(newNode);

                // Inserting before the head makes the new node the head; the
                // ring itself already stayed closed through the tail.
                if (current == head) {
                    head = newNode;
                }

            } else {

                if (this.isEmpty()) {
                    ListNode newNode = new ListNode(pContent);
                    head = newNode;
                    tail = newNode;

                    // A single element is its own successor and predecessor,
                    // which keeps the ring invariant free of null cases.
                    newNode.setNextNode(newNode);
                    newNode.setPreviousNode(newNode);
                }
            }
        }
    }

    /**
     * Appends an element at the end of the list.
     *
     * The current element remains unchanged.
     *
     * @param pContent element to append; null is ignored
     */
    public void append(ContentType pContent) {
        if (pContent != null) {

            if (this.isEmpty()) {
                this.insert(pContent);
            } else {
                ListNode newNode = new ListNode(pContent);

                // Link the new node between the former tail and the head, which
                // keeps the ring closed at every step.
                newNode.setPreviousNode(tail);
                newNode.setNextNode(head);

                tail.setNextNode(newNode);
                head.setPreviousNode(newNode);

                tail = newNode;
            }
        }
    }

    /**
     * Concatenates another list to the end of this list.
     *
     * The two rings are opened at their respective seams and rejoined into a
     * single ring. The source list is cleared afterwards so that no two lists
     * ever share nodes, which would otherwise let a change through one list
     * corrupt the other. The current cursor position of this list remains
     * unchanged.
     *
     * A list cannot be concatenated with itself; such a call is ignored, because
     * splicing a ring into itself would leave the structure unusable.
     *
     * @param pList list to append; null, empty, and self are ignored
     */
    public void concat(CircularDoublyLinkedList<ContentType> pList) {
        if (pList != this && pList != null && !pList.isEmpty()) {

            if (this.isEmpty()) {
                // Adopt the other ring wholesale; it is already closed.
                this.head = pList.head;
                this.tail = pList.tail;
            } else {
                // Join this tail to the other head, then close the combined
                // ring between the other tail and this head. All four links are
                // rewritten so that both directions stay consistent.
                this.tail.setNextNode(pList.head);
                pList.head.setPreviousNode(this.tail);

                this.tail = pList.tail;

                this.tail.setNextNode(this.head);
                this.head.setPreviousNode(this.tail);
            }

            pList.head = null;
            pList.tail = null;
            pList.current = null;
        }
    }

    /**
     * Removes the current element from the list.
     *
     * After removal the successor becomes the current element, so a ring can be
     * drained by repeated calls. Removing the only remaining element empties the
     * list and clears the cursor.
     *
     * As with insertion, no traversal is required: both neighbours of the node
     * being unlinked are reachable directly.
     */
    public void remove() {
        if (this.hasAccess() && !this.isEmpty()) {

            if (current == head && current == tail) {
                // The only element is being removed, so the ring disappears
                // entirely and no element can remain active.
                head = null;
                tail = null;
                current = null;
            } else {
                ListNode predecessor = current.getPreviousNode();
                ListNode successor = current.getNextNode();

                // Bridge across the departing node in both directions.
                predecessor.setNextNode(successor);
                successor.setPreviousNode(predecessor);

                // Move the ends along when the node being removed was one of
                // them; the ring stays closed because the bridge above already
                // joined the two survivors.
                if (current == head) {
                    head = successor;
                }
                if (current == tail) {
                    tail = predecessor;
                }

                // Detach the removed node completely so that a caller still
                // holding a reference to it cannot reach the remaining ring.
                current.setContentObject(null);
                current.setNextNode(null);
                current.setPreviousNode(null);

                current = successor;
            }
        }
    }
}
