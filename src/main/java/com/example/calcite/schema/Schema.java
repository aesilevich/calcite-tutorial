package com.example.calcite.schema;

import java.util.Collections;
import java.util.Map;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

/** Calcite schema implementation that contains set of tables. */
public class Schema extends AbstractSchema {
  /** Map of tables in schema. */
  private final Map<String, SchemaTable> tables;

  /** Creates schema with specified map of tables. */
  public Schema(Map<String, SchemaTable> tables) {
    this.tables = tables;
  }

  /** Returns table with specified name. */
  public SchemaTable getSchemaTable(String name) {
    SchemaTable table = tables.get(name);
    if (table == null) {
      throw new IllegalArgumentException("Table '" + name + "' not found");
    }

    return table;
  }

  @Override protected Map<String, Table> getTableMap() {
    return Collections.unmodifiableMap(tables);
  }
}
