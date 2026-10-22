package com.example.calcite;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelVisitor;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.checkerframework.checker.nullness.qual.Nullable;

/** RelNode visitor that checks if filter, project and join nodes contain subqueries. */
public class SubQueryChecker extends RelVisitor {
  private boolean found = false;

  /** Returns true if relational tree contains subqueries. */
  public static boolean containsSubQuery(RelNode root) {
    SubQueryChecker checker = new SubQueryChecker();
    checker.go(root);
    return checker.found;
  }

  @Override public void visit(RelNode node, int ordinal, @Nullable RelNode parent) {
    if (node instanceof LogicalFilter filter) {
      check(filter.getCondition());
    } else if (node instanceof LogicalProject project) {
      project.getProjects().forEach(this::check);
    } else if (node instanceof LogicalJoin join) {
      check(join.getCondition());
    }

    super.visit(node, ordinal, parent);
  }

  private void check(RexNode node) {
    if (SubQueryRexChecker.containsSubQuery(node)) {
      found = true;
    }
  }
}
