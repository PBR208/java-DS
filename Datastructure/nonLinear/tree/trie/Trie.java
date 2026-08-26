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
}
