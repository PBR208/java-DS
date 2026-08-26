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

    /**
     * Associates the specified value with the specified key, creating whatever
     * part of the path does not exist yet.
     *
     * Detailed explanation of:
     * - Purpose: Adds a key to the trie, or replaces the value of a key that is
     *   already present.
     * - Business context: Serves as the primary entry point for populating the
     *   structure. Nodes are shared with every key that has the same prefix, so
     *   inserting a key that extends or overlaps existing keys allocates only
     *   the nodes for the characters that are genuinely new.
     * - Processing steps:
     *   1. Reject a null key or a null value, consistent with the rest of this
     *      library.
     *   2. Descend from the root, creating and linking a node for any character
     *      whose child does not exist yet.
     *   3. Attach the value at the terminal node, counting the key only when
     *      that node did not already carry one.
     * - Assumptions: Assumes the caller treats the trie as a map, so that
     *   inserting an existing key is intended as a replacement rather than as a
     *   duplicate entry.
     * - Side effects: Allocates one node per genuinely new character, mutates
     *   the child chains along the path, and increments the key count when the
     *   key was not previously present.
     *
     * Time complexity: O(k) in the length of the key, times the bounded
     * per-level chain scan. Independent of the number of keys already stored.
     * Space complexity: O(k) worst case, when no prefix of the key is shared
     * with anything already present; O(1) when the whole path already exists.
     *
     * @param pKey
     * The key to store. Must not be null; a null key is ignored rather than
     * raising an exception. The empty string is a legitimate key and terminates
     * at the root. Any character is accepted, since the child representation
     * imposes no alphabet restriction.
     *
     * @param pValue
     * The value to associate with the key. Must not be null; a null value is
     * ignored, because the value field doubles as the marker distinguishing a
     * stored key from a mere prefix, and storing null would make the two
     * indistinguishable. Callers needing a value-less set of words can store any
     * constant here.
     */
    public void insert(String pKey, ContentType pValue) {
        // Reject arguments that the structure cannot represent. A null value in
        // particular would be read back as "this prefix is not a key".
        if (pKey == null || pValue == null) {
            return;
        }

        // Descend from the empty prefix, extending the path as required.
        TrieNode currentNode = root;

        for (int index = 0; index < pKey.length(); index++) {
            char character = pKey.charAt(index);

            // Reuse the existing branch whenever this prefix is already present,
            // which is what makes keys with common prefixes share their nodes.
            TrieNode child = findChild(currentNode, character);

            if (child == null) {
                // This character extends the trie into new territory.
                child = new TrieNode(character);
                linkChild(currentNode, child);
            }

            currentNode = child;
        }

        // A node that carries no value yet represents a prefix that was not
        // previously a key, so this insertion genuinely adds an entry. When a
        // value is already present the key is merely being re-mapped and the
        // count must not move.
        if (currentNode.value == null) {
            size++;
        }

        // Attach the value, which simultaneously marks this node as terminal.
        currentNode.value = pValue;
    }

    /**
     * Retrieves the value associated with the specified key.
     *
     * Detailed explanation of:
     * - Purpose: Answers the map question, returning what was stored under a
     *   key.
     * - Business context: The cost depends only on the length of the key, never
     *   on how many keys the trie holds, which is the property that makes a trie
     *   attractive for dictionary lookups over very large word sets.
     * - Processing steps: Descends to the node representing the key and reports
     *   its value, which is null precisely when the descent ended on a node that
     *   represents a prefix rather than a stored key.
     * - Assumptions: Assumes the caller distinguishes absence from a stored
     *   null; this cannot arise, since insert refuses null values.
     * - Side effects: None; searching does not modify the trie.
     *
     * Time complexity: O(k) in the length of the key.
     * Space complexity: O(1); the descent is iterative.
     *
     * @param pKey
     * The key to look up. Must not be null; null is answered with null rather
     * than raising an exception. The empty string is a valid key.
     *
     * @return
     * The value stored under the key, or null when the key is absent, when the
     * string names a prefix that is not itself a key, or when pKey is null.
     */
    public ContentType search(String pKey) {
        // A null key cannot name a path through the trie.
        if (pKey == null) {
            return null;
        }

        TrieNode terminal = findNode(pKey);

        // Reaching a node proves the prefix exists; only a value proves the
        // prefix is a stored key.
        if (terminal == null) {
            return null;
        }

        return terminal.value;
    }

    /**
     * Determines whether the specified key is stored in this trie.
     *
     * Detailed explanation of:
     * - Purpose: Answers membership directly, for callers that care whether a
     *   word is present rather than what it maps to.
     * - Business context: Because insert refuses null values, this is equivalent
     *   to testing the result of search against null. It exists as a separate
     *   method because membership is the question a trie is most often asked,
     *   and expressing it as a boolean reads better at the call site than a null
     *   comparison whose significance the reader has to reconstruct.
     * - Processing steps: Delegates to search and tests the result.
     * - Assumptions: Relies on the invariant that a stored key always carries a
     *   non-null value.
     * - Side effects: None; this method does not modify the trie.
     *
     * Time complexity: O(k) in the length of the key.
     * Space complexity: O(1).
     *
     * @param pKey
     * The key to test. Must not be null; null is reported as absent.
     *
     * @return
     * True when the key was inserted and not since removed; false otherwise,
     * including when the string is merely a prefix of stored keys.
     */
    public boolean containsKey(String pKey) {
        return search(pKey) != null;
    }

    /**
     * Determines whether any stored key begins with the specified prefix.
     *
     * Detailed explanation of:
     * - Purpose: Answers the question that distinguishes a trie from a hash
     *   table, which can locate a key but cannot report on prefixes without
     *   inspecting every entry it holds.
     * - Business context: This is the operation behind autocomplete and
     *   incremental search, where each additional character typed must be
     *   answered without rescanning the dictionary. It is also the cheap
     *   rejection test that lets a caller abandon a search path early, which is
     *   how a trie accelerates word puzzles and constrained searches.
     * - Processing steps: Descends to the node representing the prefix. Merely
     *   reaching that node is the answer: nodes exist only because some key
     *   caused them to be created, and removal prunes any node that has stopped
     *   contributing to a key, so a reachable node always lies on the path of at
     *   least one stored key.
     * - Assumptions: Relies on the pruning performed by remove. Without it, a
     *   node left behind by a deleted key would make this method report a prefix
     *   that no stored key actually has.
     * - Side effects: None; this method does not modify the trie.
     *
     * Time complexity: O(p) in the length of the prefix, and notably independent
     * of how many keys share it.
     * Space complexity: O(1); the descent is iterative.
     *
     * @param pPrefix
     * The prefix to test. Must not be null; null is reported as absent. The
     * empty string is a prefix of every key and therefore reports true whenever
     * the trie holds anything at all.
     *
     * @return
     * True when at least one stored key begins with the prefix, which includes
     * the case where the prefix is itself a stored key.
     */
    public boolean startsWith(String pPrefix) {
        // A null prefix cannot name a path through the trie.
        if (pPrefix == null) {
            return false;
        }

        // An empty trie has no keys, so nothing can begin with any prefix. This
        // has to be tested explicitly because the empty prefix always reaches
        // the root, which exists even when no key does.
        if (isEmpty()) {
            return false;
        }

        // Reaching the node is sufficient: pruning guarantees every reachable
        // node lies on the path of at least one stored key.
        return findNode(pPrefix) != null;
    }

    /**
     * Determines whether this trie currently stores no keys.
     *
     * Detailed explanation of:
     * - Purpose: Provides the guard callers use before draining or reporting on
     *   the structure.
     * - Business context: Emptiness is a statement about keys, not about nodes.
     *   The root always exists, so testing the structure itself would report a
     *   trie as non-empty even before anything was inserted.
     * - Processing steps: Compares the key counter against zero.
     * - Assumptions: Assumes insert and remove keep the counter in step with the
     *   number of value-carrying nodes.
     * - Side effects: None; this method does not modify the trie.
     *
     * Time complexity: O(1); a single counter comparison.
     * Space complexity: O(1).
     *
     * @return
     * True when no key is stored, false as soon as at least one key has been
     * inserted and not since removed.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of keys currently stored in this trie.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the entry count without walking the structure.
     * - Business context: The count is of keys, not of nodes: one key of length
     *   ten occupies up to ten nodes but counts once, and prefixes that are not
     *   themselves keys do not count at all. Callers sizing an output buffer
     *   before collecting keys need exactly this quantity.
     * - Processing steps: Returns the incrementally maintained counter.
     * - Assumptions: Assumes insert counts only genuinely new keys and remove
     *   only genuinely present ones, which both establish by inspecting the
     *   terminal node's value before adjusting the counter.
     * - Side effects: None; this method does not modify the trie.
     *
     * Time complexity: O(1); the count is maintained incrementally.
     * Space complexity: O(1).
     *
     * @return
     * Key count, zero for an empty trie and never negative.
     */
    public int size() {
        return size;
    }

    /**
     * Removes the specified key and prunes every node that its removal leaves
     * without purpose.
     *
     * Detailed explanation of:
     * - Purpose: Deletes an entry and restores the invariant that every node in
     *   the trie lies on the path of at least one stored key.
     * - Business context: Detaching the value alone would be enough for search
     *   and containsKey to stop reporting the key, but it would silently break
     *   startsWith, which treats the mere existence of a node as proof that some
     *   key carries that prefix. A trie that is filled and then emptied would
     *   also retain the entire skeleton of its former contents. Pruning is
     *   therefore part of the operation rather than an optimisation.
     * - Processing steps: Delegates to a recursive helper that descends to the
     *   terminal node, clears its value on the way down and then, as the
     *   recursion unwinds, unlinks each node that has become both value-less and
     *   childless. Unwinding is what makes the pruning possible in a single
     *   pass: a node can only be judged once the outcome for the node below it
     *   is known.
     * - Assumptions: Assumes the key count is only decremented when a value was
     *   genuinely present, so that removing an absent key or a bare prefix
     *   leaves the count untouched.
     * - Side effects: Clears one value, may unlink a chain of nodes, and
     *   decrements the key count when the key was present.
     *
     * Time complexity: O(k) in the length of the key, times the bounded
     * per-level chain scan for the descent and for each unlink.
     * Space complexity: O(k) for the recursion stack, bounded by the key length
     * rather than by the number of stored keys.
     *
     * @param pKey
     * The key to remove. Must not be null; null is ignored. Removing a key that
     * is absent, or a string that is only a prefix of stored keys, leaves the
     * trie completely unchanged including its key count.
     */
    public void remove(String pKey) {
        // A null key cannot name a path through the trie.
        if (pKey == null) {
            return;
        }

        // The root itself is never pruned, so its return value is discarded: it
        // must survive as the anchor of an empty trie.
        removeRec(root, pKey, 0);
    }

    /**
     * Clears the value at the end of the given key and reports whether the
     * visited node has become prunable.
     *
     * Detailed explanation of:
     * - Purpose: Implements the descend-then-prune pass underlying the public
     *   remove method.
     * - Business context: The return value carries the pruning decision back up
     *   the recursion. A node may be discarded only when it neither terminates a
     *   key nor leads to one, and both conditions can only be evaluated after
     *   the level below has finished, which is why the work happens as the
     *   recursion unwinds rather than on the way down.
     * - Processing steps:
     *   1. At the end of the key, clear the value if one is present and count
     *      the removal. A node whose value was already absent means the key was
     *      never stored, so nothing is counted.
     *   2. Otherwise advance to the child for the current character. A missing
     *      child means the key is absent, and reporting false stops the unwind
     *      from pruning anything.
     *   3. After the recursive call returns, unlink the child when it reported
     *      itself prunable.
     *   4. Report whether this node has now become prunable itself.
     * - Assumptions: Assumes the index is a valid position within the key, which
     *   the recursion maintains.
     * - Side effects: May clear a value, unlink a child, and decrement the key
     *   count.
     *
     * @param pNode
     * The node currently being visited. Must not be null.
     *
     * @param pKey
     * The key being removed. Must not be null.
     *
     * @param pIndex
     * Position within the key that this node is responsible for consuming.
     * Equals the key length once the terminal node has been reached.
     *
     * @return
     * True when this node carries no value and has no children, and may
     * therefore be unlinked by its parent; false when it must be kept.
     */
    private boolean removeRec(TrieNode pNode, String pKey, int pIndex) {

        if (pIndex == pKey.length()) {
            // The terminal node for this key has been reached.
            if (pNode.value != null) {
                // The key really was stored; drop it and account for it.
                pNode.value = null;
                size--;
            }
        } else {
            char character = pKey.charAt(pIndex);
            TrieNode child = findChild(pNode, character);

            if (child == null) {
                // The key is not stored. Report false so that the unwind leaves
                // the existing structure completely untouched.
                return false;
            }

            // Descend, then act on the verdict for the level below.
            if (removeRec(child, pKey, pIndex + 1)) {
                unlinkChild(pNode, child);
            }
        }

        /*
         * This node may be discarded only if it has stopped serving any purpose:
         * it terminates no key of its own, and it leads to none either. A node
         * that still has children must be kept even without a value, because it
         * remains part of the path of every key below it.
         */
        return pNode.value == null && pNode.firstChild == null;
    }

    /**
     * Detaches the specified child from a parent's child chain.
     *
     * Detailed explanation of:
     * - Purpose: Performs the structural mutation that pruning is built from,
     *   completing the pair begun by linkChild.
     * - Business context: Because children are held in a singly linked chain,
     *   detaching one requires knowing its predecessor, which is located by
     *   walking the chain. The chain is bounded by the alphabet in use, so this
     *   walk does not change the complexity of removal.
     * - Processing steps: Handles the case where the child heads the chain by
     *   advancing the parent's first-child reference; otherwise walks to the
     *   predecessor and bridges across the departing node. The detached node's
     *   own sibling link is cleared so that it cannot serve as a path back into
     *   the surviving structure.
     * - Assumptions: Assumes the child is genuinely present in the parent's
     *   chain, which the single call site guarantees by having just descended
     *   into it.
     * - Side effects: Mutates the parent's child chain and clears the detached
     *   node's sibling reference.
     *
     * Time complexity: O(c) in the number of children of the parent, bounded by
     * the alphabet in use.
     * Space complexity: O(1); the walk is iterative.
     *
     * @param pParent
     * The node to detach from. Must not be null.
     *
     * @param pChild
     * The child to detach. Must not be null and must be present in the parent's
     * chain.
     */
    private void unlinkChild(TrieNode pParent, TrieNode pChild) {

        if (pParent.firstChild == pChild) {
            // The child heads the chain, so the successor becomes the new head.
            pParent.firstChild = pChild.nextSibling;
        } else {
            // Locate the predecessor so that the chain can be bridged across the
            // departing node.
            TrieNode predecessor = pParent.firstChild;

            while (predecessor != null && predecessor.nextSibling != pChild) {
                predecessor = predecessor.nextSibling;
            }

            // A null predecessor would mean the child was not in this chain,
            // which the call site rules out; the guard keeps the assignment safe
            // regardless.
            if (predecessor != null) {
                predecessor.nextSibling = pChild.nextSibling;
            }
        }

        // Sever the detached node's link so that it cannot lead back into the
        // surviving structure and cannot keep former siblings reachable.
        pChild.nextSibling = null;
    }

    /**
     * Collects every stored key that begins with the specified prefix.
     *
     * Detailed explanation of:
     * - Purpose: Produces the actual completions of a prefix, which is the
     *   operation a trie exists to make cheap and which startsWith only answers
     *   as a yes or no.
     * - Business context: This is the autocomplete primitive. The work is
     *   proportional to the size of the matching subtree rather than to the size
     *   of the whole dictionary, because the descent to the prefix isolates
     *   exactly the branch that can contain matches before any collecting
     *   begins. A hash table would have to examine every key it holds.
     * - Processing steps: Descends to the node representing the prefix, then
     *   walks the subtree below it, extending a running buffer with each edge's
     *   character and emitting the buffer whenever a value-carrying node is
     *   reached. An absent prefix yields an empty list rather than null.
     * - Assumptions: Assumes the returned list is the caller's to consume; it
     *   shares no state with the trie, so later mutations of the trie do not
     *   affect it and vice versa.
     * - Side effects: Allocates the result list and one string per matching key.
     *
     * Time complexity: O(p + m) where p is the prefix length and m is the total
     * size of the matching subtree, plus the cost of materialising each key.
     * Independent of the number of stored keys that do not match.
     * Space complexity: O(h) for the recursion stack and the character buffer,
     * where h is the length of the longest matching key, plus the result itself.
     *
     * @param pPrefix
     * The prefix to complete. Must not be null; null yields an empty list. The
     * empty string matches every stored key and therefore returns all of them.
     *
     * @return
     * A list of the matching keys, each a complete key rather than the remaining
     * suffix. Empty when nothing matches. Never null. The order is unspecified
     * and reflects the internal child order, which insertion does not preserve;
     * callers needing a defined order must sort the result themselves.
     */
    public SinglyLinkedList<String> keysWithPrefix(String pPrefix) {
        // The result is built even for degenerate input, so that callers can
        // iterate unconditionally without a null check.
        SinglyLinkedList<String> matches = new SinglyLinkedList<>();

        // A null prefix matches nothing.
        if (pPrefix == null) {
            return matches;
        }

        // Isolate the branch that can contain matches. Everything outside it is
        // never examined, which is where the efficiency of this operation comes
        // from.
        TrieNode branchRoot = findNode(pPrefix);

        if (branchRoot == null) {
            // No stored key carries this prefix.
            return matches;
        }

        // Seed the buffer with the prefix itself, because the subtree walk below
        // only contributes the characters that follow it.
        StringBuilder keyBuffer = new StringBuilder(pPrefix);

        collectKeys(branchRoot, keyBuffer, matches);

        // Position the cursor at the first element so the result is immediately
        // iterable, matching the convention the graph implementations follow for
        // the lists they hand out.
        matches.toFirst();

        return matches;
    }

    /**
     * Walks the subtree below the given node, appending every complete key it
     * finds to the supplied collection.
     *
     * Detailed explanation of:
     * - Purpose: Implements the depth-first collection underlying
     *   keysWithPrefix.
     * - Business context: A single mutable buffer is threaded through the whole
     *   walk rather than building a fresh string at every level, because the
     *   keys in a subtree overwhelmingly share their leading characters and
     *   rebuilding those repeatedly would make collection quadratic in the key
     *   length.
     * - Processing steps: Emits the buffer as a finished key when the current
     *   node carries a value, then visits each child in turn, appending that
     *   child's character before descending and removing it again afterwards.
     *   That removal is what keeps the buffer consistent for the next sibling:
     *   without it, each branch would inherit the characters of the branch
     *   explored before it.
     * - Assumptions: Assumes the buffer already spells the path from the root to
     *   the current node, which the caller establishes by seeding it with the
     *   prefix.
     * - Side effects: Appends to the supplied collection and temporarily mutates
     *   the buffer, which is restored to its incoming state before returning.
     *
     * @param pNode
     * Root of the subtree to walk. Must not be null.
     *
     * @param pKeyBuffer
     * Running buffer spelling the path from the trie root to pNode. Mutated
     * during the walk and restored before this method returns.
     *
     * @param pMatches
     * Collection receiving each complete key found. Must not be null.
     */
    private void collectKeys(TrieNode pNode, StringBuilder pKeyBuffer,
            SinglyLinkedList<String> pMatches) {

        // A value marks the buffer as spelling a complete stored key. The prefix
        // itself is emitted here when it happens to be a key in its own right.
        if (pNode.value != null) {
            pMatches.append(pKeyBuffer.toString());
        }

        // Visit every child, extending the buffer by one character per level.
        TrieNode child = pNode.firstChild;

        while (child != null) {
            pKeyBuffer.append(child.character);

            collectKeys(child, pKeyBuffer, pMatches);

            // Undo this level's contribution so that the next sibling starts
            // from the same prefix this call was entered with.
            pKeyBuffer.deleteCharAt(pKeyBuffer.length() - 1);

            child = child.nextSibling;
        }
    }
}
