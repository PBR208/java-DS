package linear.stack;

/**
 * Generic stack implementation based on the Last-In, First-Out (LIFO)
 * principle.
 *
 * Elements are inserted and removed exclusively from the top of the stack.
 * All operations execute in constant time, regardless of the number of
 * elements stored.
 *
 * @author PBR - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class Stack<ContentType> {

  /* ---------- Start of private inner class ---------- */

  private class StackNode {

    private ContentType content = null;
    private StackNode nextNode = null;

    /**
     * Creates a new stack node containing the specified element.
     *
     * The next node reference is initialized to null.
     *
     * @param pContent the element to store in this node
     */
    public StackNode(ContentType pContent) {
      content = pContent;
      nextNode = null;
    }

    /**
     * Sets the reference to the next node in the stack.
     *
     * @param pNext the node that follows this node
     */
    public void setNext(StackNode pNext) {
      nextNode = pNext;
    }

    /**
     * Returns the next node in the stack.
     *
     * @return the next StackNode, or null if no successor exists
     */
    public StackNode getNext() {
      return nextNode;
    }

    /**
     * Returns the element stored in this node.
     *
     * @return the content of this node
     */
    public ContentType getContent() {
      return content;
    }
  }

  /* ---------- End of private inner class ---------- */

  private StackNode head;

  /**
   * Creates an empty stack.
   *
   * All elements managed by this stack must be of type ContentType.
   */
  public Stack() {
    head = null;
  }

  /**
   * Checks whether the stack contains any elements.
   *
   * @return true if the stack is empty; otherwise false
   */
  public boolean isEmpty() {
    return (head == null);
  }

  /**
   * Pushes an element onto the top of the stack.
   *
   * If the provided element is null, the stack remains unchanged.
   *
   * @param pContent the element to be pushed onto the stack
   */
  public void push(ContentType pContent) {
    if (pContent != null) {
      StackNode node = new StackNode(pContent);
      node.setNext(head);
      head = node;
    }
  }

  /**
   * Removes the element currently located at the top of the stack.
   *
   * If the stack is empty, no action is performed.
   */
  public void pop() {
    if (!isEmpty()) {
      head = head.getNext();
    }
  }

  /**
   * Returns the element at the top of the stack without removing it.
   *
   * @return the top element, or null if the stack is empty
   */
  public ContentType top() {
    if (!this.isEmpty()) {
      return head.getContent();
    } else {
      return null;
    }
  }
}