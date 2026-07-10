package nonLinear.graph.base;

/**
 * Purpose:
 * Represents a single node (vertex) within a graph data structure,
 * identified by a unique string identifier and carrying a marking flag used
 * by graph algorithms to track visitation or processing state. This class
 * forms one of the two fundamental structural units of the non-linear graph
 * package, alongside Edge, and serves as the endpoint referenced by edges
 * to establish connections between nodes. It provides the minimal state
 * required for identity comparison and algorithmic bookkeeping during
 * traversal, pathfinding, and spanning tree construction, while delegating
 * all relationship and connectivity logic to the surrounding Graph and
 * Edge implementations.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Represents a single node within a graph, identified by a unique string
 * identifier.
 *
 * Responsibility: Encapsulates the identity of a graph node and a boolean
 * marking flag used by graph algorithms (e.g., traversal or spanning tree
 * algorithms) to track processing state.
 *
 * Scope: Used exclusively within the nonLinear.graph package as a
 * structural component of larger graph implementations, referenced by Edge
 * instances as connection endpoints.
 *
 * Dependencies: Has no dependencies on other classes within the package;
 * serves as a foundational type referenced by Edge and Graph.
 *
 * Thread-safety: This class is not thread-safe. Concurrent modification of
 * the mark field from multiple threads without external synchronization
 * may result in inconsistent state.
 *
 * Lifecycle: A Vertex instance is immutable with respect to its identifier
 * once constructed; only the mark attribute may be modified after
 * instantiation.
 *
 * Architectural role: Acts as a foundational data unit consumed by Edge and
 * Graph implementations, as well as graph traversal, pathfinding, and
 * spanning tree algorithms elsewhere in the system.
 */
public class Vertex {

    // Stores the unique identifier of this vertex, used to distinguish it
    // from all other vertices within the graph. Declared final because
    // vertex identity must remain fixed for the lifetime of the instance.
    private final String id;

    // Stores the processing/visitation state of this vertex. Used by graph
    // algorithms to track whether the vertex has already been considered,
    // visited, or included during traversal or construction of derived
    // structures (e.g., spanning trees).
    private boolean mark;

    /**
     * Constructs a new Vertex with the specified unique identifier.
     *
     * Detailed explanation of:
     * - Purpose: Initializes a graph node with a fixed identity and an
     *   initial unmarked processing state.
     * - Business context: Represents a single addressable node within a
     *   graph model, forming the basis for edges and algorithmic graph
     *   analysis.
     * - Processing steps: Assigns the provided identifier to the internal
     *   id field and initializes the mark flag to an unmarked state.
     * - Assumptions: Assumes the caller supplies a valid, non-null
     *   identifier that is unique within the context of the graph in which
     *   this vertex will be used; no validation is performed at
     *   construction time.
     * - Side effects: None beyond initialization of instance state.
     *
     * @param pID
     * Unique identifier of the vertex. Must not be null or empty.
     */
    public Vertex(String pID) {
        // Assign the supplied identifier as the permanent identity of this
        // vertex.
        this.id = pID;

        // Initialize the mark flag to false, indicating this vertex has not
        // yet been processed or visited by any graph algorithm.
        this.mark = false;
    }


    /**
     * Retrieves the unique identifier of this vertex.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the vertex's identity for use in comparison,
     *   lookup, and display operations.
     * - Business context: Used by graph implementations to locate specific
     *   vertices by identifier and by algorithms that need to reference or
     *   report on individual nodes.
     * - Processing steps: Directly returns the internal id field.
     * - Assumptions: Assumes the id field has been properly initialized via
     *   the constructor.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * The unique identifier assigned to this vertex at construction time.
     * Never returns null under normal usage.
     */
    public String getID() {
        return id;
    }

    /**
     * Sets the processing/visitation mark on this vertex.
     *
     * Detailed explanation of:
     * - Purpose: Allows graph algorithms to flag this vertex as processed,
     *   visited, or included during traversal or construction of derived
     *   structures such as spanning trees.
     * - Business context: Supports algorithmic bookkeeping where vertices
     *   must be tracked to avoid redundant processing or to record
     *   inclusion in a result set.
     * - Processing steps: Directly assigns the provided boolean value to
     *   the internal mark field.
     * - Assumptions: Assumes the calling algorithm manages mark state
     *   consistently according to its own traversal logic.
     * - Side effects: Mutates the internal state of this vertex instance.
     *
     * @param pMark
     * The new mark state to assign to this vertex. True indicates the
     * vertex has been processed, visited, or included; false indicates it
     * has not.
     */
    public void setMark(boolean pMark) {
        // Overwrite the current mark state with the newly supplied value.
        this.mark = pMark;
    }

    /**
     * Indicates whether this vertex has been marked by a graph algorithm.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the current mark state for inspection by graph
     *   algorithms performing traversal or construction of derived
     *   structures.
     * - Business context: Used to determine whether this vertex has
     *   already been processed, visited, or included in a result set,
     *   preventing redundant work.
     * - Processing steps: Directly returns the internal mark field.
     * - Assumptions: Assumes the mark field has been properly initialized
     *   via the constructor or updated via setMark.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if this vertex has been marked as processed, visited, or
     * included; false otherwise.
     */
    public boolean isMarked() {
        return mark;
    }
}