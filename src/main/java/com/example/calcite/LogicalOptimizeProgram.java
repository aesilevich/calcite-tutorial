package com.example.calcite;

import java.util.List;

import org.apache.calcite.plan.RelOptLattice;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.sql2rel.RelDecorrelator;
import org.apache.calcite.sql2rel.RelFieldTrimmer;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.RelBuilder;

/** Program that optimizes logical relational tree with HEP planner and decorrelates it. */
public class LogicalOptimizeProgram implements Program {
  public static final LogicalOptimizeProgram INSTANCE = new LogicalOptimizeProgram();

  @Override public RelNode run(RelOptPlanner planner, RelNode rel,
      RelTraitSet requiredOutputTraits, List<RelOptMaterialization> materializations,
      List<RelOptLattice> lattices) {

    // trimming unused fields
    RelBuilder relBuilder = RelFactories.LOGICAL_BUILDER.create(rel.getCluster(), null);
    rel = new RelFieldTrimmer(null, relBuilder).trim(rel);

    // creating HEP planner
    HepProgram program = HepProgram.builder()
        .addRuleCollection(List.of(
            CoreRules.FILTER_SUB_QUERY_TO_CORRELATE,
            CoreRules.PROJECT_SUB_QUERY_TO_CORRELATE,
            CoreRules.JOIN_SUB_QUERY_TO_CORRELATE,

            CoreRules.FILTER_REDUCE_EXPRESSIONS,
            CoreRules.PROJECT_REDUCE_EXPRESSIONS,
            CoreRules.JOIN_REDUCE_EXPRESSIONS,
            CoreRules.CALC_REDUCE_EXPRESSIONS,
            CoreRules.WINDOW_REDUCE_EXPRESSIONS))
        .addRuleCollection(List.of(
            CoreRules.FILTER_PROJECT_TRANSPOSE,
            CoreRules.FILTER_INTO_JOIN,
            CoreRules.JOIN_CONDITION_PUSH,
            CoreRules.FILTER_AGGREGATE_TRANSPOSE,
            CoreRules.FILTER_SET_OP_TRANSPOSE,
            CoreRules.FILTER_MERGE,
            CoreRules.JOIN_PUSH_EXPRESSIONS))
        .build();
    HepPlanner hepPlanner = new HepPlanner(program);
    hepPlanner.setRoot(rel);

    // optimizing relation tree with HEP planner
    rel = hepPlanner.findBestExp();

    // decorrelating query
    return RelDecorrelator.decorrelateQuery(rel, relBuilder);
  }
}
