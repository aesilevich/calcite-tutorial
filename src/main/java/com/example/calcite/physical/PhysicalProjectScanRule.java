package com.example.calcite.physical;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableIntList;
import org.immutables.value.Value;

/** Rule that pushes project into physical table scan node. */
@Value.Enclosing
public class PhysicalProjectScanRule extends RelRule<PhysicalProjectScanRule.Config> {
  public static final PhysicalProjectScanRule INSTANCE = Config.DEFAULT.toRule();

  protected PhysicalProjectScanRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    Project project = call.rel(0);
    PhysicalTableScan scan = call.rel(1);

    // converting project column references to table column indexes
    List<Integer> projection = new ArrayList<>();
    for (RexNode node : project.getProjects()) {
      int index = ((RexInputRef) node).getIndex();
      projection.add(scan.getProjection() == null ? index : scan.getProjection().get(index));
    }

    call.transformTo(new PhysicalTableScan(scan.getCluster(), scan.getTraitSet(),
        scan.getHints(), scan.getTable(), scan.getFilter(), ImmutableIntList.copyOf(projection)));
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutablePhysicalProjectScanRule.Config.of()
        .withOperandSupplier(b0 ->
            b0.operand(Project.class)
                .predicate(project -> project.getProjects().stream()
                    .allMatch(node -> node instanceof RexInputRef))
                .oneInput(b1 -> b1.operand(PhysicalTableScan.class).noInputs()))
        .as(Config.class);

    @Override default PhysicalProjectScanRule toRule() {
      return new PhysicalProjectScanRule(this);
    }
  }
}
