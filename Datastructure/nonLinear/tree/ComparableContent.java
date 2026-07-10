package nonLinear.tree;

/**
 * Purpose:
 * Defines the comparison contract required by objects that are intended to be
 * stored and managed within a BinarySearchTree implementation.
 *
 * This interface establishes a customizable ordering mechanism that allows
 * domain-specific objects to define how they are compared against one another.
 * The comparison methods are used by tree insertion, search, traversal, and
 * deletion operations to maintain the structural ordering guarantees of the
 * binary search tree.
 *
 * Implementing classes are responsible for providing a consistent and
 * transitive ordering relationship. Failure to implement these methods
 * correctly may result in invalid tree structures, incorrect search results,
 * or undefined traversal behavior.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0.0
 */
public interface ComparableContent<ContentType> {

  /**
   * Determines whether the current object should be ordered after the
   * provided object according to the comparison rules defined by the
   * implementing class.
   *
   * This method is primarily used by binary search tree insertion and
   * navigation algorithms to determine whether traversal should continue
   * toward the right subtree. The comparison logic may be based on
   * identifiers, timestamps, names, priorities, or any other domain-specific
   * ordering criteria.
   *
   * Implementations should ensure that the comparison logic remains
   * consistent with the corresponding isEqual(...) and isLess(...) methods
   * to preserve the integrity of the tree structure.
   *
   * @param pContent
   * The object to compare against the current instance.
   * Must represent a valid comparable object of the same logical type.
   * Null handling should be explicitly defined by the implementing class.
   *
   * @return
   * True when the current object has a greater ordering value than
   * pContent according to the implemented comparison strategy.
   * Returns false when the current object is equal to or less than
   * pContent.
   */
  public boolean isGreater(ContentType pContent);

  /**
   * Determines whether the current object is considered equal to the
   * provided object according to the ordering rules defined by the
   * implementing class.
   *
   * This method is used to identify logically equivalent elements during
   * tree operations such as searching, duplicate detection, and node
   * replacement. Equality should be based on the same comparison criteria
   * used by the greater-than and less-than operations.
   *
   * Implementations should guarantee that two objects identified as equal
   * are not simultaneously reported as greater than or less than one
   * another.
   *
   * @param pContent
   * The object to compare against the current instance.
   * Must represent a valid comparable object of the same logical type.
   * Null handling should be explicitly defined by the implementing class.
   *
   * @return
   * True when both objects are considered equal according to the
   * implemented ordering definition.
   * Returns false when any ordering difference exists between the objects.
   */
  public boolean isEqual(ContentType pContent);

  /**
   * Determines whether the current object should be ordered before the
   * provided object according to the comparison rules defined by the
   * implementing class.
   *
   * This method is primarily used by binary search tree insertion and
   * navigation algorithms to determine whether traversal should continue
   * toward the left subtree. The ordering logic must remain consistent with
   * the implementations of isGreater(...) and isEqual(...) to ensure that
   * the binary search tree maintains a valid and deterministic structure.
   *
   * Incorrect implementation of this method may cause misplaced nodes,
   * failed searches, or violations of binary search tree ordering
   * guarantees.
   *
   * @param pContent
   * The object to compare against the current instance.
   * Must represent a valid comparable object of the same logical type.
   * Null handling should be explicitly defined by the implementing class.
   *
   * @return
   * True when the current object has a lower ordering value than
   * pContent according to the implemented comparison strategy.
   * Returns false when the current object is equal to or greater than
   * pContent.
   */
  public boolean isLess(ContentType pContent);

}