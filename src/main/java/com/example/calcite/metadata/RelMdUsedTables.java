package com.example.calcite.metadata;

import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.volcano.RelSubset;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.metadata.ReflectiveRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexShuttle;
import org.apache.calcite.rex.RexSubQuery;

import com.google.common.collect.ImmutableSet;

/** Implementation of {@link UsedTables} metadata. */
public class RelMdUsedTables implements UsedTables.Handler {
  public static final RelMetadataProvider SOURCE =
      ReflectiveRelMetadataProvider.reflectiveSource(new RelMdUsedTables(),
          UsedTables.Handler.class);

  /** Returns used tables for table scan. */
  public Set<List<String>> getUsedTables(TableScan scan, RelMetadataQuery mq) {
    return Set.of(scan.getTable().getQualifiedName());
  }

  /** Returns used tables for subset. */
  public Set<List<String>> getUsedTables(RelSubset subset, RelMetadataQuery mq) {
    return ((TutorialRelMetadataQuery) mq).getUsedTables(subset.stripped());
  }

  /** Returns used tables for other nodes. */
  @Override public Set<List<String>> getUsedTables(RelNode rel, RelMetadataQuery mq) {
    TutorialRelMetadataQuery tmq = (TutorialRelMetadataQuery) mq;
    ImmutableSet.Builder<List<String>> tables = ImmutableSet.builder();
    for (RelNode input : rel.getInputs()) {
      tables.addAll(tmq.getUsedTables(input));
    }

    // collecting tables used in sub-queries of node expressions
    rel.accept(new RexShuttle() {
      @Override public RexNode visitSubQuery(RexSubQuery subQuery) {
        tables.addAll(tmq.getUsedTables(subQuery.rel));
        return subQuery;
      }
    });
    return tables.build();
  }
}
