package linear.list;

/**
 * Optimized doubly linked list implementation with a movable cursor.
 *
 * The list maintains bidirectional links between nodes to support efficient
 * traversal in both directions. A single "current" pointer acts as a cursor
 * for navigation and in-place modifications.
 *
 * Key characteristics:
 * - O(1) insertion and removal at known positions
 * - O(1) access to next/previous node from current position
 * - Cursor-based operations (similar to editor-style lists)
 *
 * Designed for predictable pointer performance rather than index-based access.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 *  Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class DoublyLinkedList<ContentType> {

    /**
     * Internal node representing one element in the list.
     */
    private class Node {

        private ContentType content;
        private Node next;
        private Node prev;

        /**
         * Creates a new standalone node containing the given value.
         *
         * @param pContent value stored in this node
         */
        Node(ContentType pContent) {
            this.content = pContent;
        }
    }

    private Node head;
    private Node tail;
    private Node current;

    /**
     * Initializes an empty list.
     */
    public DoublyLinkedList() {
        head = null;
        tail = null;
        current = null;
    }

    /**
     * Checks whether the list contains no elements.
     *
     * @return true if the list is empty, otherwise false
     */
    public boolean isEmpty() {
        return head == null;
    }

    /**
     * Checks whether a valid cursor position exists.
     *
     * @return true if a current element is selected, otherwise false
     */
    public boolean hasAccess() {
        return current != null;
    }

    /**
     * Moves the cursor to the first element in the list.
     */
    public void toFirst() {
        current = head;
    }

    /**
     * Moves the cursor to the last element in the list.
     */
    public void toLast() {
        current = tail;
    }

    /**
     * Advances the cursor to the next element.
     *
     * If the cursor is already at the end, it becomes undefined (null).
     */
    public void next() {
        if (current != null) {
            current = current.next;
        }
    }

    /**
     * Moves the cursor to the previous element.
     *
     * If the cursor is already at the beginning, it becomes undefined (null).
     */
    public void previous() {
        if (current != null) {
            current = current.prev;
        }
    }

    /**
     * Returns the content at the current cursor position.
     *
     * @return current element content or null if no active cursor exists
     */
    public ContentType getContent() {
        return (current != null) ? current.content : null;
    }

    /**
     * Replaces the content of the current element.
     *
     * No operation is performed if there is no active cursor or if the
     * provided value is null.
     *
     * @param pContent new value to store in the current node
     */
    public void setContent(ContentType pContent) {
        if (current != null && pContent != null) {
            current.content = pContent;
        }
    }

    /**
     * Inserts a new element before the current position.
     *
     * Behavior:
     * - Empty list: new node becomes head, tail, and current
     * - No cursor: node is appended to the end
     * - Active cursor: node is inserted directly before current
     *
     * @param pContent value to insert
     */
    public void insert(ContentType pContent) {
        if (pContent == null) return;

        Node node = new Node(pContent);

        if (isEmpty()) {
            head = tail = current = node;
            return;
        }

        if (current == null) {
            // No active cursor: append to maintain deterministic behavior
            tail.next = node;
            node.prev = tail;
            tail = node;
            return;
        }

        node.next = current;
        node.prev = current.prev;

        if (current.prev != null) {
            current.prev.next = node;
        } else {
            head = node;
        }

        current.prev = node;
    }

    /**
     * Appends a new element at the end of the list.
     *
     * The cursor position remains unchanged.
     *
     * @param pContent value to append
     */
    public void append(ContentType pContent) {
        if (pContent == null) return;

        Node node = new Node(pContent);

        if (isEmpty()) {
            head = tail = current = node;
            return;
        }

        tail.next = node;
        node.prev = tail;
        tail = node;
    }

    /**
     * Removes the element at the current cursor position.
     *
     * After removal:
     * - Cursor moves to the next element if available
     * - Otherwise it moves to the previous element
     * - If the list becomes empty, the cursor becomes undefined
     */
    public void remove() {
        if (current == null) return;

        Node next = current.next;
        Node prev = current.prev;

        if (prev != null) {
            prev.next = next;
        } else {
            head = next;
        }

        if (next != null) {
            next.prev = prev;
        } else {
            tail = prev;
        }

        current.next = null;
        current.prev = null;

        current = (next != null) ? next : prev;
    }

    /**
     * Clears the cursor without modifying the list structure.
     *
     * After this operation, no element is selected.
     */
    public void clearAccess() {
        current = null;
    }
}