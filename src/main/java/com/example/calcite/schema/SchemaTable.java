package com.example.calcite.schema;

import java.util.List;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Custom table schema */
public class SchemaTable extends AbstractTable {
  /** List of table columns */
  private final List<Column> columns;

  /** Number of rows in table or null if unknown */
  private final @Nullable Double rowCount;

  /** Creates table schema from list of columns and number of rows */
  public SchemaTable(List<Column> columns, @Nullable Double rowCount) {
    this.columns = columns;
    this.rowCount = rowCount;
  }

  /** Returns table statistics */
  @Override public Statistic getStatistic() {
    return rowCount == null ? Statistics.UNKNOWN : Statistics.of(rowCount, null);
  }

  /** Returns type of row for table */
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    RelDataTypeFactory.Builder builder = typeFactory.builder();
    for (Column column : columns) {
      if (column.precision == null) {
        builder.add(column.name, column.type);
      } else if (column.scale == null) {
        builder.add(column.name, column.type, column.precision);
      } else {
        builder.add(column.name, column.type, column.precision, column.scale);
      }
      builder.nullable(column.isNullable);
    }

    return builder.build();
  }

  /** Table column definition */
  public static class Column {
    public final String name;
    public final SqlTypeName type;
    public final boolean isNullable;

    /** Precision (or length for string types) or null if not specified */
    public final @Nullable Integer precision;

    /** Scale or null if not specified */
    public final @Nullable Integer scale;

    public Column(String name, SqlTypeName type, boolean isNullable) {
      this(name, type, isNullable, null, null);
    }

    public Column(String name, SqlTypeName type, boolean isNullable,
        @Nullable Integer precision, @Nullable Integer scale) {
      this.name = name;
      this.type = type;
      this.isNullable = isNullable;
      this.precision = precision;
      this.scale = scale;
    }
  }
}
