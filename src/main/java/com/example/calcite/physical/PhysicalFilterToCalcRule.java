package com.example.calcite.physical;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexProgramBuilder;
import org.immutables.value.Value;

/** Rule that converts physical filter into physical calc. */
@Value.Enclosing
public class PhysicalFilterToCalcRule extends RelRule<PhysicalFilterToCalcRule.Config> {
  public static final PhysicalFilterToCalcRule INSTANCE = Config.DEFAULT.toRule();

  protected PhysicalFilterToCalcRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    PhysicalFilter filter = call.rel(0);
    RelNode input = filter.getInput();

    RexProgramBuilder programBuilder =
        new RexProgramBuilder(input.getRowType(), filter.getCluster().getRexBuilder());
    programBuilder.addIdentity();
    programBuilder.addCondition(filter.getCondition());

    call.transformTo(new PhysicalCalc(filter.getCluster(), filter.getTraitSet(),
        filter.getHints(), input, programBuilder.getProgram()));
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutablePhysicalFilterToCalcRule.Config.of()
        .withOperandSupplier(b -> b.operand(PhysicalFilter.class).anyInputs())
        .as(Config.class);

    @Override default PhysicalFilterToCalcRule toRule() {
      return new PhysicalFilterToCalcRule(this);
    }
  }
}
