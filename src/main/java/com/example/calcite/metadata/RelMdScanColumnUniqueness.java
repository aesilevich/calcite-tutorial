package com.example.calcite.metadata;

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

/** Column uniqueness for table scans with projection. */
public class RelMdScanColumnUniqueness
    implements MetadataHandler<BuiltInMetadata.ColumnUniqueness> {
  public static final RelMetadataProvider SOURCE =
      ReflectiveRelMetadataProvider.reflectiveSource(new RelMdScanColumnUniqueness(),
          BuiltInMetadata.ColumnUniqueness.Handler.class);

  @Override public MetadataDef<BuiltInMetadata.ColumnUniqueness> getDef() {
    return BuiltInMetadata.ColumnUniqueness.DEF;
  }

  /** Returns whether columns of logical scan are unique. */
  public Boolean areColumnsUnique(LogicalTableScanWithFilterAndProject scan,
      RelMetadataQuery mq, ImmutableBitSet columns, boolean ignoreNulls) {
    return scan.getTable().isKey(toTableColumns(columns, scan.getProjection()));
  }

  /** Returns whether columns of physical scan are unique. */
  public Boolean areColumnsUnique(PhysicalTableScan scan,
      RelMetadataQuery mq, ImmutableBitSet columns, boolean ignoreNulls) {
    return scan.getTable().isKey(toTableColumns(columns, scan.getProjection()));
  }

  /** Converts indexes of scan output columns to indexes of table columns. */
  private static ImmutableBitSet toTableColumns(ImmutableBitSet columns,
      @Nullable ImmutableIntList projection) {
    if (projection == null) {
      return columns;
    }

    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    for (int column : columns) {
      builder.set(projection.get(column));
    }
    return builder.build();
  }
}
