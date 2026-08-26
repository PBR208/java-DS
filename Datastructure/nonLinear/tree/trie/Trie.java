package nonLinear.tree.trie;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Implements a trie, also called a prefix tree, which associates string keys
 * with values by decomposing each key into its individual characters and
 * storing one character per edge on the path from the root down to the node
 * where the key terminates. This is a fundamentally different indexing strategy
 * from the search trees elsewhere in this package: those compare whole values
 * against one another and therefore need an ordering, whereas a trie never
 * compares two keys at all and instead navigates character by character. The
 * consequence is that lookup cost depends on the length of the key rather than
 * on how many keys are stored, and that every key sharing a prefix shares the
 * nodes representing that prefix, which is what allows the structure to answer
 * prefix questions that no hash table and no comparison tree can answer
 * efficiently: whether any stored key begins with a given prefix, and which keys
 * those are. Autocomplete, routing tables, dictionary lookups and spell checking
 * are the standard applications.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Prefix tree mapping string keys to values of an arbitrary content type.
 *
 * Responsibility: Encapsulates insertion, lookup, prefix interrogation and
 * removal of string-keyed entries, maintaining the invariant that the path of
 * characters from the root to any node spells exactly the prefix that node
 * represents, and that a node carries a value precisely when that prefix is
 * itself a stored key.
 *
 * Scope: Used within the nonLinear.tree package wherever keys are strings and
 * prefix relationships matter. It deliberately does not implement the
 * ComparableContent-based contract used by the other trees here, because it
 * imposes no ordering on the values it stores and derives its structure from the
 * keys alone.
 *
 * Dependencies: Depends on SinglyLinkedList from the linear.list package as the
 * collection type used to return groups of keys to callers, matching the
 * convention already established by the graph implementations. Relies otherwise
 * only on its own private nested TrieNode class.
 *
 * Thread-safety: This class is not thread-safe. Insertion may create a chain of
 * nodes that is briefly reachable before the terminal value is attached, and
 * removal unlinks nodes while a concurrent lookup may still be descending
 * through them; external synchronization is required whenever instances are
 * shared across threads.
 *
 * Lifecycle: A Trie instance begins empty and grows and shrinks dynamically
 * through repeated insert and remove operations. Removal prunes any node that
 * has become incapable of contributing to a stored key, so a trie that is filled
 * and then emptied returns to its initial memory footprint rather than retaining
 * the skeleton of the keys it once held.
 *
 * Architectural role: Serves as the string-keyed counterpart to the ordered
 * trees in this package, and as the reference implementation demonstrating that
 * a search structure need not be built on pairwise comparison at all.
 *
 * @param <ContentType>
 * The type of value associated with each stored key. No ordering or comparison
 * capability is required of this type, since the trie derives its entire
 * structure from the keys.
 */
public class Trie<ContentType> {

    /**
     * Purpose:
     * Represents a single position within the trie, corresponding to exactly one
     * prefix of one or more stored keys. Children are held in a first-child /
     * next-sibling chain rather than in an array indexed by character, which is
     * the decisive representation choice of this implementation and is explained
     * on the child fields below.
     *
     * Owner:
     * PBR208 - https://github.com/PBR208/
     *
     * Version:
     * 1.0
     */

    /**
     * Represents a single node of the trie, holding the character it consumes,
     * an optional terminal value, and links to its children and siblings.
     *
     * Responsibility: Stores the character that labels the edge leading into
     * this node, the value of the key terminating here if any, and the links
     * that make the node's children reachable.
     *
     * Scope: Private to the enclosing Trie class; not exposed outside this file.
     *
     * Dependencies: None beyond the generic ContentType parameter of the
     * enclosing class.
     *
     * Thread-safety: Not thread-safe; mutation of any field from multiple
     * threads without external synchronization may result in inconsistent state.
     *
     * Lifecycle: A TrieNode is created when a key introduces a character that
     * its parent does not yet have a child for, and is discarded when removal
     * determines that it can no longer contribute to any stored key. The single
     * root node is the exception: it is created with the trie and lives as long
     * as the trie does.
     *
     * Architectural role: Acts as the internal storage unit backing each prefix
     * position within the trie.
     */
    private class TrieNode {

        // The character labelling the edge that leads into this node. Together
        // with the characters of all ancestors it spells the prefix this node
        // represents. The value is meaningless for the root, which is not
        // reached by traversing any edge.
        char character;

        // The value of the key that terminates at this node, or null when this
        // node represents a prefix that is not itself a stored key. This field
        // doing double duty as the "is a key" marker is why insert refuses null
        // values: a stored null would be indistinguishable from a mere prefix.
        ContentType value;

        // First entry of this node's child chain, or null when the node is a
        // leaf. Children are held as a chain rather than in an array indexed by
        // character because an array must be sized for the whole alphabet in
        // advance: it would either restrict keys to a fixed character range such
        // as the twenty-six lowercase letters, or reserve an entry for every
        // possible char at every single node. The chain instead costs memory
        // proportional to the children that actually exist and accepts any
        // character, at the price of a short scan per level instead of a direct
        // index. Since the number of children of a node is bounded by the size
        // of the alphabet actually in use, that scan is bounded by a constant.
        TrieNode firstChild;

        // Next child of this node's parent, or null when this node is the last
        // entry in its parent's chain. This is the sibling link of the
        // first-child / next-sibling representation, which encodes a tree of
        // arbitrary branching factor using only two references per node.
        TrieNode nextSibling;

        /**
         * Constructs a new node labelled with the specified character.
         *
         * Detailed explanation of:
         * - Purpose: Initializes a trie position for a character that its parent
         *   did not previously have a child for.
         * - Business context: Represents the extension of an existing prefix by
         *   one character, which is the only way a trie ever grows.
         * - Processing steps: Records the labelling character. The value and
         *   both link fields are deliberately left at their defaults: a freshly
         *   created node represents a prefix that is not yet a key and has no
         *   children, and linking it into its parent's chain is the caller's
         *   responsibility.
         * - Assumptions: Assumes the caller links the node into the structure
         *   immediately; a node that is never linked is unreachable and simply
         *   discarded.
         * - Side effects: None beyond initialization of instance state.
         *
         * @param pCharacter
         * The character labelling the edge into this node. Any char value is
         * accepted, including whitespace and non-ASCII characters, because the
         * chain representation imposes no alphabet restriction.
         */
        TrieNode(char pCharacter) {
            this.character = pCharacter;
        }
    }

    /**
     * The root of the trie, representing the empty prefix.
     *
     * The root is never null and is never replaced for the lifetime of the trie,
     * which lets every operation begin its descent without a special case for an
     * empty structure. It carries no meaningful character, since no edge leads
     * into it, but it may carry a value: the empty string is a legitimate key and
     * terminates exactly here.
     */
    private TrieNode root;

    /**
     * Number of keys currently stored in the trie.
     *
     * The counter tracks keys, not nodes: a single key of length ten occupies up
     * to ten nodes but counts once, and prefixes that are not themselves keys do
     * not count at all. It is maintained incrementally so that the entry count is
     * available without walking the structure.
     */
    private int size;

    /**
     * Constructs an empty trie.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the permanent root node that every subsequent
     *   operation descends from.
     * - Business context: Produces a trie that is immediately usable and that
     *   stores no keys, not even the empty string.
     * - Processing steps: Allocates the root with a placeholder character and
     *   leaves its value null, which marks the empty string as not yet stored.
     * - Assumptions: Assumes no caller depends on the root's character field,
     *   which is meaningless because no edge leads into the root.
     * - Side effects: Allocates the single root node shared by every path
     *   through this trie for its entire lifetime.
     *
     * Time complexity: O(1); a single allocation.
     * Space complexity: O(1); one node holding no key.
     */
    public Trie() {
        // The root's character is never inspected, because the root is reached
        // by descending zero edges. A null character is used as an explicit
        // placeholder rather than an arbitrary letter that might be mistaken for
        // meaningful data during debugging.
        root = new TrieNode('\0');

        // No keys are stored yet; the root carries no value.
        size = 0;
    }

    /**
     * Locates the child of the specified node that is labelled with the given
     * character.
     *
     * Detailed explanation of:
     * - Purpose: Performs the single step that every descent through the trie is
     *   built from, translating "advance by this character" into a node.
     * - Business context: Concentrating the chain walk here means the insertion,
     *   lookup and removal procedures all read as descents over characters
     *   rather than as pointer manipulation, and a change to the child
     *   representation would touch only this method and its two companions.
     * - Processing steps: Walks the parent's child chain from its first entry,
     *   following sibling links until a matching character is found or the chain
     *   is exhausted.
     * - Assumptions: Assumes at most one child of a node carries any given
     *   character, which linkChild guarantees by only ever adding a character
     *   that is not already present.
     * - Side effects: None; this method only reads the structure.
     *
     * Time complexity: O(c) in the number of children of the node, which is
     * bounded by the size of the alphabet actually in use and is therefore
     * effectively constant.
     * Space complexity: O(1); the walk is iterative.
     *
     * @param pParent
     * The node whose children are searched. Must not be null; every call site
     * holds a node it has just reached.
     *
     * @param pCharacter
     * The character to advance by. Any char value is valid.
     *
     * @return
     * The child node labelled with that character, or null when the parent has
     * no such child.
     */
    private TrieNode findChild(TrieNode pParent, char pCharacter) {
        // Walk the sibling chain of this parent's children.
        TrieNode currentChild = pParent.firstChild;

        while (currentChild != null) {
            if (currentChild.character == pCharacter) {
                return currentChild;
            }
            currentChild = currentChild.nextSibling;
        }

        // The parent has no child for this character.
        return null;
    }

    /**
     * Adds the specified node to the front of a parent's child chain.
     *
     * Detailed explanation of:
     * - Purpose: Performs the single structural mutation that grows the trie.
     * - Business context: Inserting at the front rather than at the end keeps
     *   the operation constant-time. Child order carries no meaning in a trie:
     *   the structure is navigated by character, never by position, so nothing
     *   in this class depends on the order in which siblings appear. The one
     *   visible consequence is that keysWithPrefix reports keys in an
     *   unspecified order, which its documentation states explicitly.
     * - Processing steps: Points the new node at the parent's current first
     *   child and then makes it the new first child.
     * - Assumptions: Assumes the parent does not already have a child labelled
     *   with the new node's character, which every call site establishes by
     *   consulting findChild first. Adding a duplicate character would make one
     *   of the two nodes permanently unreachable.
     * - Side effects: Mutates the parent's child chain.
     *
     * Time complexity: O(1); two reference assignments.
     * Space complexity: O(1); no allocation occurs here.
     *
     * @param pParent
     * The node to attach the child to. Must not be null.
     *
     * @param pChild
     * The node to attach. Must not be null and must carry a character that the
     * parent does not already have a child for.
     */
    private void linkChild(TrieNode pParent, TrieNode pChild) {
        // Splice the new node in at the head of the chain; the former first
        // child becomes its sibling.
        pChild.nextSibling = pParent.firstChild;
        pParent.firstChild = pChild;
    }

    /**
     * Locates the node reached by spelling out the specified sequence of
     * characters from the root.
     *
     * Detailed explanation of:
     * - Purpose: Provides the shared descent used by every read operation. The
     *   node it returns represents the supplied string as a prefix, regardless
     *   of whether that prefix is also a stored key.
     * - Business context: The distinction between reaching a node and that node
     *   carrying a value is exactly the distinction between "some key starts
     *   with this" and "this is a key", which is why the two questions are
     *   answered by different public methods built on this one helper.
     * - Processing steps: Starts at the root and advances one child per
     *   character, abandoning the descent as soon as a character has no matching
     *   child.
     * - Assumptions: Assumes a non-null argument; the public entry points filter
     *   null out before delegating here. The empty string is valid and yields the
     *   root, since spelling out no characters leaves the descent where it began.
     * - Side effects: None; this method only reads the structure.
     *
     * Time complexity: O(k) in the length of the supplied string, times the
     * bounded per-level chain scan. Notably independent of how many keys the
     * trie holds, which is the characteristic property of this structure.
     * Space complexity: O(1); the descent is iterative.
     *
     * @param pSequence
     * The characters to spell out. Must not be null; may be empty, which yields
     * the root.
     *
     * @return
     * The node representing that prefix, or null when the descent runs out of
     * matching children before the sequence is exhausted.
     */
    private TrieNode findNode(String pSequence) {
        // Begin at the root, which represents the empty prefix.
        TrieNode currentNode = root;

        // Consume one character per level.
        for (int index = 0; index < pSequence.length(); index++) {
            currentNode = findChild(currentNode, pSequence.charAt(index));

            // No child for this character means no stored key carries this
            // prefix, so the descent cannot continue.
            if (currentNode == null) {
                return null;
            }
        }

        return currentNode;
    }
}
