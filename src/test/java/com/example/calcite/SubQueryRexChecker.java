package com.example.calcite;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.rex.RexVisitorImpl;

/** RexNode visitor that checks if expression tree contains subqueries. */
public class SubQueryRexChecker extends RexVisitorImpl<Void> {
  private boolean found = false;

  public SubQueryRexChecker() {
    super(true);
  }

  /** Returns true if expression tree contains subqueries. */
  public static boolean containsSubQuery(RexNode node) {
    SubQueryRexChecker checker = new SubQueryRexChecker();
    node.accept(checker);
    return checker.found;
  }

  @Override public Void visitSubQuery(RexSubQuery subQuery) {
    found = true;
    return null;
  }
}
