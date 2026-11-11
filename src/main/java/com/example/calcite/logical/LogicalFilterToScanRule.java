package com.example.calcite.logical;

import java.util.List;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexPermuteInputsShuttle;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.util.mapping.Mappings;
import org.immutables.value.Value;

/** Rule that pushes logical filter condition into logical scan with filter and project. */
@Value.Enclosing
public class LogicalFilterToScanRule extends RelRule<LogicalFilterToScanRule.Config> {
  public static final LogicalFilterToScanRule INSTANCE = Config.DEFAULT.toRule();

  protected LogicalFilterToScanRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    LogicalFilter filter = call.rel(0);
    LogicalTableScanWithFilterAndProject scan = call.rel(1);

    // converting references to scan output into references to table columns
    RexNode condition = filter.getCondition();
    if (scan.getProjection() != null) {
      int columnCount = scan.getTable().getRowType().getFieldCount();
      Mappings.TargetMapping mapping =
          Mappings.source(scan.getProjection().toIntegerList(), columnCount);
      condition = condition.accept(RexPermuteInputsShuttle.of(mapping));
    }

    // combining existing scan filter with filter condition
    if (scan.getFilter() != null) {
      condition = RexUtil.composeConjunction(scan.getCluster().getRexBuilder(),
          List.of(condition, scan.getFilter()));
    }

    call.transformTo(new LogicalTableScanWithFilterAndProject(scan.getCluster(), scan.getTraitSet(),
        scan.getHints(), scan.getTable(), condition, scan.getProjection()));
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutableLogicalFilterToScanRule.Config.of()
        .withOperandSupplier(b0 ->
            b0.operand(LogicalFilter.class).oneInput(b1 ->
                b1.operand(LogicalTableScanWithFilterAndProject.class).noInputs()))
        .as(Config.class);

    @Override default LogicalFilterToScanRule toRule() {
      return new LogicalFilterToScanRule(this);
    }
  }
}
