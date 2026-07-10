package linear.list;

/**
 * Generic circular singly linked list implementation with a single movable cursor
 * ("current" element).
 *
 * The list manages a sequence of ContentType elements in a circular structure where
 * the tail node points back to the head, creating a ring. Supports cursor-based
 * access, insertion, deletion, and traversal.
 *
 * Only one element can be active (current) at any time. If the list is empty,
 * or the current element is removed, no active element exists.
 * Cursor can traverse indefinitely in the circular structure.
 *
 * The implementation is optimized for pointer manipulation; most operations
 * run in constant time except where traversal is explicitly required.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class CircularSinglyLinkedList<ContentType> {

  /* ---------- Start of private inner class ---------- */

  /**
   * Internal node representation of the circular linked list.
   */
  private class ListNode {

    private ContentType contentObject;
    private ListNode next;

    /**
     * Creates a new node holding the given content.
     *
     * @param pContent the value stored in this node
     */
    private ListNode(ContentType pContent) {
      contentObject = pContent;
      next = null;
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
     * Returns the next node in the list.
     * In a circular list, this will eventually wrap back to the head.
     *
     * @return successor node or null if list is empty
     */
    public ListNode getNextNode() {
      return this.next;
    }

    /**
     * Sets the next node reference.
     *
     * @param pNext the node to link as successor
     */
    public void setNextNode(ListNode pNext) {
      this.next = pNext;
    }
  }

  /* ---------- End of private inner class ---------- */

  // Head (first element) of the list
  ListNode head;

  // Tail (last element) of the list; always points to head in a non-empty circular list
  ListNode tail;

  // Currently active element (cursor)
  ListNode current;

  /**
   * Creates an empty circular list.
   */
  public CircularSinglyLinkedList() {
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
   * In a circular list, after the tail, the cursor wraps to the head.
   * If there is no current element, the list loses its active element.
   */
  public void next() {
    if (this.hasAccess()) {
      current = current.getNextNode();
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
   * No operation is performed if there is no current element or if
   * the provided content is null.
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
   * If the list is empty, the new element becomes the only element
   * (pointing to itself) but no current element is set.
   *
   * If no current element exists, the operation is ignored unless
   * the list is empty.
   *
   * @param pContent element to insert
   */
  public void insert(ContentType pContent) {
    if (pContent != null) {

      if (this.hasAccess()) {

        ListNode newNode = new ListNode(pContent);

        if (current != head) {
          ListNode previous = this.getPrevious(current);
          newNode.setNextNode(previous.getNextNode());
          previous.setNextNode(newNode);
        } else {
          newNode.setNextNode(head);
          head = newNode;
          tail.setNextNode(head); // Maintain circular structure
        }

      } else {

        if (this.isEmpty()) {
          ListNode newNode = new ListNode(pContent);
          head = newNode;
          tail = newNode;
          head.setNextNode(head); // Single node points to itself
        }
      }
    }
  }

  /**
   * Appends an element at the end of the list.
   *
   * The current element remains unchanged.
   * The new element becomes the new tail and points back to head.
   *
   * @param pContent element to append
   */
  public void append(ContentType pContent) {
    if (pContent != null) {

      if (this.isEmpty()) {
        this.insert(pContent);
      } else {
        ListNode newNode = new ListNode(pContent);

        tail.setNextNode(newNode);
        tail = newNode;
        tail.setNextNode(head); // Maintain circular structure
      }
    }
  }

  /**
   * Concatenates another circular list to the end of this list.
   *
   * The source list is cleared after the operation.
   * The current cursor position of this list remains unchanged.
   *
   * @param pList circular list to append
   */
  public void concat(CircularSinglyLinkedList pList) {
    if (pList != this && pList != null && !pList.isEmpty()) {

      if (this.isEmpty()) {
        this.head = pList.head;
        this.tail = pList.tail;
      } else {
        this.tail.setNextNode(pList.head);
        this.tail = pList.tail;
        this.tail.setNextNode(this.head); // Restore circular link
      }

      pList.head = null;
      pList.tail = null;
      pList.current = null;
    }
  }

  /**
   * Removes the current element from the list.
   *
   * After removal, the next element becomes current.
   * If the last element is removed, no current element remains and the list becomes empty.
   */
  public void remove() {
    if (this.hasAccess() && !this.isEmpty()) {

      if (current == head && current == tail) {
        // Only one element in the list - remove it and break the circle
        head = null;
        tail = null;
        current = null;
      } else if (current == head) {
        // Removing head
        head = head.getNextNode();
        tail.setNextNode(head); // Update circular link to point to new head
        ListNode temp = current.getNextNode();
        current.setContentObject(null);
        current.setNextNode(null);
        current = temp;
      } else {
        // Removing a middle or tail node
        ListNode previous = this.getPrevious(current);
        if (current == tail) {
          tail = previous;
        }
        previous.setNextNode(current.getNextNode());
        ListNode temp = current.getNextNode();
        current.setContentObject(null);
        current.setNextNode(null);
        current = temp;
      }
    }
  }

  /**
   * Returns the predecessor of a given node.
   * In a circular list, every node has a predecessor (no null-termination).
   *
   * @param pNode target node
   * @return previous node or null if not found
   */
  private ListNode getPrevious(ListNode pNode) {
    if (pNode != null && !this.isEmpty()) {
      ListNode temp = head;
      while (temp.getNextNode() != pNode) {
        temp = temp.getNextNode();
      }
      return temp;
    } else {
      return null;
    }
  }
}
