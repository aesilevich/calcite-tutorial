package com.example.calcite.logical;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.immutables.value.Value;

/** Rule that replaces logical table scan with logical scan with filter and project. */
@Value.Enclosing
public class LogicalTableScanWithFilterAndProjectRule
    extends RelRule<LogicalTableScanWithFilterAndProjectRule.Config> {
  public static final LogicalTableScanWithFilterAndProjectRule INSTANCE =
      Config.DEFAULT.toRule();

  protected LogicalTableScanWithFilterAndProjectRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    LogicalTableScan scan = call.rel(0);
    call.transformTo(new LogicalTableScanWithFilterAndProject(scan.getCluster(),
        scan.getTraitSet(), scan.getHints(), scan.getTable(), null, null));
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutableLogicalTableScanWithFilterAndProjectRule.Config.of()
        .withOperandSupplier(b0 -> b0.operand(LogicalTableScan.class).noInputs())
        .as(Config.class);

    @Override default LogicalTableScanWithFilterAndProjectRule toRule() {
      return new LogicalTableScanWithFilterAndProjectRule(this);
    }
  }
}
