package com.example.calcite.logical;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil.InputFinder;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexPermuteInputsShuttle;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.mapping.Mappings;
import org.immutables.value.Value;

/** Rule that pushes logical project into logical scan with filter and project. */
@Value.Enclosing
public class LogicalProjectToScanRule extends RelRule<LogicalProjectToScanRule.Config> {
  public static final LogicalProjectToScanRule INSTANCE = Config.DEFAULT.toRule();

  protected LogicalProjectToScanRule(Config config) {
    super(config);
  }

  @Override public void onMatch(RelOptRuleCall call) {
    LogicalProject project = call.rel(0);
    LogicalTableScanWithFilterAndProject scan = call.rel(1);

    // building list of column numbers in current scan output taking into account scan projection
    int tableColCount = scan.getTable().getRowType().getFieldCount();
    List<Integer> oldOutputs = scan.getProjection() != null
        ? scan.getProjection().toIntegerList()
        : ImmutableIntList.identity(tableColCount).toIntegerList();

    // collecting indexes of all old scan outputs used in project expressions
    ImmutableBitSet usedOutputs = InputFinder.bits(project.getProjects(), null);

    if (usedOutputs.isEmpty()) {
      // we don't want to have a scan node with an empty list of columns because
      // it can't be converted to SQL, so we skip these cases.
      return;
    }

    List<Integer> usedOutputsList = usedOutputs.toList();

    // creating mapping from indexes in old scan output to indexes in new
    // scan output after removing unused outputs
    Mappings.TargetMapping mapping = Mappings.target(usedOutputsList, oldOutputs.size());

    // building list of expressions for new project with remapping old scan output to new outputs
    RexPermuteInputsShuttle shuttle = RexPermuteInputsShuttle.of(mapping);
    List<RexNode> newProjectExprs = new ArrayList<>();
    for (RexNode expr : project.getProjects()) {
      newProjectExprs.add(expr.accept(shuttle));
    }

    // checking if all new project node expressions are references to the scan output
    boolean allInputRefs = newProjectExprs.stream().allMatch(e -> e instanceof RexInputRef);

    if (allInputRefs) {
      // if all new project node expressions are references to scan output,
      // then reorder the output columns and leave only the scan node

      // building list of columns for projection in new scan node
      List<Integer> newColumns = new ArrayList<>();
      for (RexNode expr : newProjectExprs) {
        newColumns.add(oldOutputs.get(usedOutputsList.get(((RexInputRef) expr).getIndex())));
      }

      // creating new scan node
      LogicalTableScanWithFilterAndProject newScan = new LogicalTableScanWithFilterAndProject(
          scan.getCluster(), scan.getTraitSet(), scan.getHints(), scan.getTable(),
          scan.getFilter(), ImmutableIntList.copyOf(newColumns));

      call.transformTo(newScan);
    } else {
      // building list of columns for projection in new scan node
      List<Integer> newColumns = new ArrayList<>();
      for (int col : usedOutputs) {
        newColumns.add(oldOutputs.get(col));
      }

      // creating new scan node with only used columns
      LogicalTableScanWithFilterAndProject newScan = new LogicalTableScanWithFilterAndProject(
          scan.getCluster(), scan.getTraitSet(), scan.getHints(), scan.getTable(),
          scan.getFilter(), ImmutableIntList.copyOf(newColumns));

      // creating new project node with remapped input numbers
      LogicalProject newProject = LogicalProject.create(newScan, project.getHints(),
          newProjectExprs, project.getRowType(), project.getVariablesSet());

      call.transformTo(newProject);
    }
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = true)
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutableLogicalProjectToScanRule.Config.of()
        .withOperandSupplier(b0 ->
            b0.operand(LogicalProject.class)
                .oneInput(b1 -> b1.operand(LogicalTableScanWithFilterAndProject.class).noInputs()))
        .as(Config.class);

    @Override default LogicalProjectToScanRule toRule() {
      return new LogicalProjectToScanRule(this);
    }
  }
}
