package com.example.calcite.metadata;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.ReflectiveRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableIntList;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.example.calcite.logical.LogicalTableScanWithFilterAndProject;
import com.example.calcite.physical.PhysicalTableScan;

/** Population size for table scans with filter and projection. */
public class RelMdScanPopulationSize
    implements MetadataHandler<BuiltInMetadata.PopulationSize> {
  public static final RelMetadataProvider SOURCE =
      ReflectiveRelMetadataProvider.reflectiveSource(new RelMdScanPopulationSize(),
          BuiltInMetadata.PopulationSize.Handler.class);

  @Override public MetadataDef<BuiltInMetadata.PopulationSize> getDef() {
    return BuiltInMetadata.PopulationSize.DEF;
  }

  /** Returns population size of columns of logical scan. */
  public @Nullable Double getPopulationSize(LogicalTableScanWithFilterAndProject scan,
      RelMetadataQuery mq, ImmutableBitSet groupKey) {
    return getPopulationSize(scan.getTable(), scan.getProjection(), groupKey);
  }

  /** Returns population size of columns of physical scan. */
  public @Nullable Double getPopulationSize(PhysicalTableScan scan,
      RelMetadataQuery mq, ImmutableBitSet groupKey) {
    return getPopulationSize(scan.getTable(), scan.getProjection(), groupKey);
  }

  /**
   * Returns row count of table without scan filter if columns are unique in table,
   * otherwise returns null.
   */
  private static @Nullable Double getPopulationSize(RelOptTable table,
      @Nullable ImmutableIntList projection, ImmutableBitSet groupKey) {
    ImmutableBitSet columns = RelMdScanColumnUniqueness.toTableColumns(groupKey, projection);
    return table.isKey(columns) ? table.getRowCount() : null;
  }
}
