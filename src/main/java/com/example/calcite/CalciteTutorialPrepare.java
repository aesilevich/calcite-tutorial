package com.example.calcite;

import java.lang.reflect.Type;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCostFactory;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.prepare.CalcitePrepareImpl;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.Programs;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.example.calcite.distribution.DistributionTraitDef;

import com.example.calcite.metadata.TutorialRelMetadataQuery;

/**
 * Query preparation for Calcite JDBC connections that optimizes queries with custom
 * optimization programs instead of standard Calcite program.
 */
public class CalciteTutorialPrepare extends CalcitePrepareImpl {
  // we use top-down mode only when we want physical root node because top-down
  // mode does not work with multiple physical conventions
  /** Program with all custom optimization stages. */
  public static final Program PROGRAM = Programs.sequence(LogicalOptimizeProgram.INSTANCE,
      PhysicalOptimizeProgram.BOTTOM_UP, ConvertToCalcProgram.INSTANCE);

  @Override protected RelOptPlanner createPlanner(Context prepareContext,
      org.apache.calcite.plan.@Nullable Context externalContext,
      @Nullable RelOptCostFactory costFactory) {
    RelOptPlanner planner = super.createPlanner(prepareContext, externalContext, costFactory);
    planner.addRelTraitDef(DistributionTraitDef.INSTANCE);
    return planner;
  }

  @Override protected CalcitePreparingStmt getPreparingStmt(Context context, Type elementType,
      CalciteCatalogReader catalogReader, RelOptPlanner planner) {
    JavaTypeFactory typeFactory = context.getTypeFactory();
    EnumerableRel.Prefer prefer = elementType == Object[].class
        ? EnumerableRel.Prefer.ARRAY
        : EnumerableRel.Prefer.CUSTOM;
    RelOptCluster cluster = createCluster(planner, new RexBuilder(typeFactory));
    TutorialRelMetadataQuery.register(cluster);
    return new PreparingStmt(this, context, catalogReader, typeFactory, prefer, cluster);
  }

  /** Statement preparation that runs custom optimization program. */
  private static class PreparingStmt extends CalcitePreparingStmt {
    PreparingStmt(CalciteTutorialPrepare prepare, Context context,
        CalciteCatalogReader catalogReader, JavaTypeFactory typeFactory,
        EnumerableRel.Prefer prefer, RelOptCluster cluster) {
      super(prepare, context, catalogReader, typeFactory, context.getRootSchema(), prefer,
          cluster, EnumerableConvention.INSTANCE, prepare.createConvertletTable());
    }

    @Override protected Program getProgram() {
      return PROGRAM;
    }
  }
}
