package com.example.calcite.schema;

import java.util.List;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;

/** Custom table schema */
public class SchemaTable extends AbstractTable {
  /** List of table columns */
  private final List<Column> columns;

  /** Creates table schema from list of columns */
  public SchemaTable(List<Column> columns) {
    this.columns = columns;
  }

  /** Returns type of row for table */
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    RelDataTypeFactory.Builder builder = typeFactory.builder();
    for (Column column : columns) {
      builder.add(column.name, column.type).nullable(column.isNullable);
    }

    return builder.build();
  }

  /** Table column definition */
  public static class Column {
    public final String name;
    public final SqlTypeName type;
    public final boolean isNullable;

    public Column(String name, SqlTypeName type, boolean isNullable) {
      this.name = name;
      this.type = type;
      this.isNullable = isNullable;
    }
  }
}
