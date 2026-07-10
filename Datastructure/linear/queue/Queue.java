package linear.queue;

/**
 * Generic FIFO (First-In, First-Out) queue implementation based on a singly
 * linked list.
 *
 * @author PBR208 - https://github.com/PBR208
 * @version 1.0
 *
 * Conventions:
 * - Parameters prefixed with 'p' denote method input parameters.
 */
public class Queue<ContentType> {

    /**
     * Internal node representation used by the queue.
     * Each node stores a content object and a reference to the next node
     * in the sequence.
     */
    private class QueueNode {

        /** Payload stored in this node. */
        private ContentType content = null;

        /** Reference to the next node in the queue. */
        private QueueNode nextNode = null;

        /**
         * Creates a new queue node containing the specified content.
         *
         * @param pContent the value to be stored in this node
         */
        public QueueNode(ContentType pContent) {
            content = pContent;
            nextNode = null;
        }

        /**
         * Updates the reference to the next node in the linked structure.
         *
         * @param pNext the node that should follow this node
         */
        public void setNext(QueueNode pNext) {
            nextNode = pNext;
        }

        /**
         * Returns the next node in the queue.
         *
         * @return the next {@code QueueNode}, or {@code null} if this is the
         *         last node
         */
        public QueueNode getNext() {
            return nextNode;
        }

        /**
         * Returns the content stored in this node.
         *
         * @return the node's payload
         */
        public ContentType getContent() {
            return content;
        }
    }

    /* ---------- End of private inner node class ---------- */

    /** Reference to the first element in the queue. */
    private QueueNode head;

    /** Reference to the last element in the queue. */
    private QueueNode tail;

    /**
     * Creates an empty queue.
     *
     * The queue accepts elements of type {@code ContentType} and maintains
     * FIFO ordering.
     */
    public Queue() {
        head = null;
        tail = null;
    }

    /**
     * Determines whether the queue currently contains any elements.
     *
     * @return {@code true} if the queue is empty; otherwise {@code false}
     */
    public boolean isEmpty() {
        return head == null;
    }

    /**
     * Appends an element to the end of the queue.
     *
     * If the provided element is {@code null}, the operation is ignored and
     * the queue remains unchanged.
     *
     * @param pContent the element to enqueue
     */
    public void enqueue(ContentType pContent) {
        if (pContent != null) {
            QueueNode newNode = new QueueNode(pContent);

            if (this.isEmpty()) {
                head = newNode;
                tail = newNode;
            } else {
                tail.setNext(newNode);
                tail = newNode;
            }
        }
    }

    /**
     * Removes the element at the front of the queue.
     *
     * If the queue is empty, no action is performed.
     * After removing the last remaining element, both head and tail
     * references are reset to {@code null}.
     */
    public void dequeue() {
        if (!this.isEmpty()) {
            head = head.getNext();

            if (this.isEmpty()) {
                head = null;
                tail = null;
            }
        }
    }

    /**
     * Returns, but does not remove, the element at the front of the queue.
     *
     * @return the first element in the queue, or {@code null} if the queue
     *         is empty
     */
    public ContentType front() {
        if (this.isEmpty()) {
            return null;
        } else {
            return head.getContent();
        }
    }
}