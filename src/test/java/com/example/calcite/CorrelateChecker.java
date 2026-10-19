package com.example.calcite;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelVisitor;
import org.apache.calcite.rel.core.Correlate;
import org.checkerframework.checker.nullness.qual.Nullable;

/** RelNode visitor that checks if relational tree contains correlate nodes. */
public class CorrelateChecker extends RelVisitor {
  private boolean found = false;

  /** Returns true if relational tree contains correlate nodes. */
  public static boolean containsCorrelate(RelNode root) {
    CorrelateChecker checker = new CorrelateChecker();
    checker.go(root);
    return checker.found;
  }

  @Override public void visit(RelNode node, int ordinal, @Nullable RelNode parent) {
    if (node instanceof Correlate) {
      found = true;
    }

    super.visit(node, ordinal, parent);
  }
}
