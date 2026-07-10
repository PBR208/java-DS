package linear.list;

public class CircularDoublyLinkedList<ContentType> {

    private class ListNode {

        private ContentType contentObject;
        private ListNode next;

        private ListNode(ContentType pContent) {
            contentObject = pContent;
            next = null;
        }

        public ContentType getContentObject() {
            return contentObject;
        }

        public void setContentObject(ContentType pContent) {
            contentObject = pContent;
        }

        public ListNode getNextNode() {
            return this.next;
        }

        public void setNextNode(ListNode pNext) {
            this.next = pNext;
        }
    }

    ListNode head;
    ListNode tail;
    ListNode current;

    public CircularDoublyLinkedList() {
        head = null;
        tail = null;
        current = null;
    }

    public boolean isEmpty() {
        return head == null;
    }

    public boolean hasAccess() {
        return current != null;
    }

    public void next() {
        if (this.hasAccess()) {
            current = current.getNextNode();
        }
    }

    public void toFirst() {
        if (!isEmpty()) {
            current = head;
        }
    }

    public void toLast() {
        if (!isEmpty()) {
            current = tail;
        }
    }

    public ContentType getContent() {
        if (this.hasAccess()) {
            return current.getContentObject();
        } else {
            return null;
        }
    }

    public void setContent(ContentType pContent) {
        if (pContent != null && this.hasAccess()) {
            current.setContentObject(pContent);
        }
    }

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
                    tail.setNextNode(head);
                }
            } else {
                if (this.isEmpty()) {
                    ListNode newNode = new ListNode(pContent);
                    head = newNode;
                    tail = newNode;
                    head.setNextNode(head);
                }
            }
        }
    }

    public void append(ContentType pContent) {
        if (pContent != null) {
            if (this.isEmpty()) {
                this.insert(pContent);
            } else {
                ListNode newNode = new ListNode(pContent);

                tail.setNextNode(newNode);
                tail = newNode;
                tail.setNextNode(head);
            }
        }
    }

    public void concat(CircularDoublyLinkedList pList) {
        if (pList != this && pList != null && !pList.isEmpty()) {
            if (this.isEmpty()) {
                this.head = pList.head;
                this.tail = pList.tail;
            } else {
                this.tail.setNextNode(pList.head);
                this.tail = pList.tail;
                this.tail.setNextNode(this.head);
            }

            pList.head = null;
            pList.tail = null;
            pList.current = null;
        }
    }

    public void remove() {
        if (this.hasAccess() && !this.isEmpty()) {
            if (current == head && current == tail) {
                head = null;
                tail = null;
                current = null;
            } else if (current == head) {
                head = head.getNextNode();
                tail.setNextNode(head);
                ListNode temp = current.getNextNode();
                current.setContentObject(null);
                current.setNextNode(null);
                current = temp;
            } else {
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