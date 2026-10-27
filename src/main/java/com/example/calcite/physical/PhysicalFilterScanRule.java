package com.example.calcite.physical;

import java.util.List;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.immutables.value.Value;

/** Rule that pushes physical filter condition into physical table scan. */
@Value.Enclosing
public class PhysicalFilterScanRule extends RelRule<PhysicalFilterScanRule.Config> {
  public static final PhysicalFilterScanRule INSTANCE = Config.DEFAULT.toRule();

  protected PhysicalFilterScanRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    PhysicalFilter filter = call.rel(0);
    PhysicalTableScan scan = call.rel(1);

    // combining existing scan filter with filter condition
    RexNode condition = filter.getCondition();
    if (scan.getFilter() != null) {
      condition = RexUtil.composeConjunction(scan.getCluster().getRexBuilder(),
          List.of(scan.getFilter(), condition));
    }

    call.transformTo(new PhysicalTableScan(scan.getCluster(), scan.getTraitSet(),
        scan.getHints(), scan.getTable(), condition));
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutablePhysicalFilterScanRule.Config.of()
        .withOperandSupplier(b0 ->
            b0.operand(PhysicalFilter.class).oneInput(b1 ->
                b1.operand(PhysicalTableScan.class).noInputs()))
        .as(Config.class);

    @Override default PhysicalFilterScanRule toRule() {
      return new PhysicalFilterScanRule(this);
    }
  }
}
