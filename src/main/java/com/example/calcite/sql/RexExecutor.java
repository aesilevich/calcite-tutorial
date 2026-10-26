package com.example.calcite.sql;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.DataContexts;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexExecutorImpl;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexShuttle;

/** Executor for constant expressions with correct evaluation for advanced ABS function. */
public class RexExecutor extends RexExecutorImpl {
  public RexExecutor() {
    super(DataContexts.EMPTY);
  }

  /*
   * Reduces expressions with standard RexExecutorImpl implementation and performs
   * additional steps for correct reducing of advanced ABS function.
   */
  @Override public void reduce(RexBuilder rexBuilder, List<RexNode> constExps,
      List<RexNode> reducedValues) {

    // adding casts for all operands of ABS calls to result type to avoid overflow
    AbsOperandConverter converter = new AbsOperandConverter(rexBuilder);
    List<RexNode> castedExps = new ArrayList<>();
    for (RexNode exp : constExps) {
      castedExps.add(exp.accept(converter));
    }

    super.reduce(rexBuilder, castedExps, reducedValues);

    // restoring original expressions that were not reduced,
    // this depends on current implementation of RexExecutorImpl class which always
    // creates new nodes (instead of reusing original instances) if reduction is successful
    for (int i = 0; i < constExps.size(); i++) {
      if (reducedValues.get(i) == castedExps.get(i)) {
        reducedValues.set(i, constExps.get(i));
      }
    }
  }

  /** Helper shuttle that adds casts for operands of advanced ABS function to its result type. */
  private static class AbsOperandConverter extends RexShuttle {
    private final RexBuilder rexBuilder;

    AbsOperandConverter(RexBuilder rexBuilder) {
      this.rexBuilder = rexBuilder;
    }

    @Override public RexNode visitCall(RexCall call) {
      RexCall visited = (RexCall) super.visitCall(call);
      if (visited.getOperator() != OperatorTable.ABS) {
        return visited;
      }

      // creating cast from operand type to result type for ABS function
      RexNode operand = rexBuilder.makeCast(visited.getType(), visited.getOperands().get(0));
      return rexBuilder.makeCall(visited.getType(), visited.getOperator(), List.of(operand));
    }
  }
}
