package com.example.calcite;

import java.util.List;

import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.plan.RelOptLattice;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.tools.Program;

import com.example.calcite.logical.LogicalFilterToScanRule;
import com.example.calcite.logical.LogicalProjectToScanRule;
import com.example.calcite.logical.LogicalTableScanWithFilterAndProjectRule;
import com.example.calcite.physical.PhysicalFilterRule;
import com.example.calcite.physical.PhysicalProjectRule;
import com.example.calcite.physical.PhysicalTableScanRule;
import com.example.calcite.physical.PhysicalToEnumerableConverterRule;

/**
 * Program that converts logical relational tree to tree with required traits with Volcano
 * planner and optimizes it.
 */
public class PhysicalOptimizeProgram implements Program {
  public static final PhysicalOptimizeProgram INSTANCE = new PhysicalOptimizeProgram();

  @Override public RelNode run(RelOptPlanner planner, RelNode rel,
      RelTraitSet requiredOutputTraits, List<RelOptMaterialization> materializations,
      List<RelOptLattice> lattices) {

    planner.clear();
    planner.addRule(LogicalTableScanWithFilterAndProjectRule.INSTANCE);
    planner.addRule(LogicalFilterToScanRule.INSTANCE);
    planner.addRule(LogicalProjectToScanRule.INSTANCE);
    planner.addRule(PhysicalTableScanRule.INSTANCE);
    planner.addRule(PhysicalProjectRule.INSTANCE);
    planner.addRule(PhysicalFilterRule.INSTANCE);
    planner.addRule(PhysicalToEnumerableConverterRule.INSTANCE);
    planner.addRule(CoreRules.FILTER_PROJECT_TRANSPOSE);
    planner.addRule(CoreRules.PROJECT_MERGE);
    planner.addRule(CoreRules.FILTER_AGGREGATE_TRANSPOSE);
    planner.addRule(CoreRules.FILTER_SET_OP_TRANSPOSE);

    for (RelOptRule rule : EnumerableRules.ENUMERABLE_RULES) {
      planner.addRule(rule);
    }

    if (!rel.getTraitSet().equals(requiredOutputTraits)) {
      rel = planner.changeTraits(rel, requiredOutputTraits);
    }

    planner.setRoot(rel);
    return planner.findBestExp();
  }
}
