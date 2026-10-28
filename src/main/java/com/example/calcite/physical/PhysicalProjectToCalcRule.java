package com.example.calcite.physical;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexProgram;
import org.immutables.value.Value;

/** Rule that converts physical project into physical calc. */
@Value.Enclosing
public class PhysicalProjectToCalcRule extends RelRule<PhysicalProjectToCalcRule.Config> {
  public static final PhysicalProjectToCalcRule INSTANCE = Config.DEFAULT.toRule();

  protected PhysicalProjectToCalcRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    PhysicalProject project = call.rel(0);
    RelNode input = project.getInput();
    RexProgram program = RexProgram.create(input.getRowType(), project.getProjects(), null,
        project.getRowType(), project.getCluster().getRexBuilder());
    call.transformTo(new PhysicalCalc(project.getCluster(), project.getTraitSet(),
        project.getHints(), input, program));
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutablePhysicalProjectToCalcRule.Config.of()
        .withOperandSupplier(b -> b.operand(PhysicalProject.class).anyInputs())
        .as(Config.class);

    @Override default PhysicalProjectToCalcRule toRule() {
      return new PhysicalProjectToCalcRule(this);
    }
  }
}
