package com.example.calcite;

import java.util.List;

import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.plan.RelOptLattice;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.rules.FilterCalcMergeRule;
import org.apache.calcite.rel.rules.ProjectCalcMergeRule;
import org.apache.calcite.tools.Program;

import com.example.calcite.physical.PhysicalCalc;
import com.example.calcite.physical.PhysicalFilter;
import com.example.calcite.physical.PhysicalFilterToCalcRule;
import com.example.calcite.physical.PhysicalProject;
import com.example.calcite.physical.PhysicalProjectToCalcRule;

/** Program that converts physical projects and filters to physical calcs with HEP planner. */
public class ConvertToCalcProgram implements Program {
  public static final ConvertToCalcProgram INSTANCE = new ConvertToCalcProgram();

  @Override public RelNode run(RelOptPlanner planner, RelNode rel,
      RelTraitSet requiredOutputTraits, List<RelOptMaterialization> materializations,
      List<RelOptLattice> lattices) {
    HepProgram program = HepProgram.builder()
        .addRuleCollection(List.of(
            PhysicalProjectToCalcRule.INSTANCE,
            PhysicalFilterToCalcRule.INSTANCE,
            EnumerableRules.ENUMERABLE_PROJECT_TO_CALC_RULE,
            EnumerableRules.ENUMERABLE_FILTER_TO_CALC_RULE,
            CoreRules.CALC_MERGE,
            FilterCalcMergeRule.Config.DEFAULT
                .withOperandFor(PhysicalFilter.class, PhysicalCalc.class)
                .toRule(),
            ProjectCalcMergeRule.Config.DEFAULT
                .withOperandFor(PhysicalProject.class, PhysicalCalc.class)
                .toRule()))
        .build();
    HepPlanner hepPlanner = new HepPlanner(program);
    hepPlanner.setRoot(rel);
    return hepPlanner.findBestExp();
  }
}
