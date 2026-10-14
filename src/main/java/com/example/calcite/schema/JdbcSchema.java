package com.example.calcite.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.type.SqlTypeName;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/** Custom schema for Calcite tutorial. */
public class JdbcSchema extends AbstractSchema {
  /** Map of tables in schema. */
  private final Map<String, Table> tables;

  /**
   * Creates new Calcite schema. Reads connection string from jdbc.url property,
   * connects to database and reads list of tables.
   */
  public JdbcSchema() throws SQLException {
    String jdbcUrl = System.getProperty("jdbc.url");
    if (jdbcUrl == null) {
      throw new IllegalStateException("System property jdbc.url is not set");
    }

    tables = new HashMap<>();

    String[] types = {"TABLE"};
    try (Connection con = DriverManager.getConnection(jdbcUrl);
         ResultSet tablesSet = con.getMetaData().getTables(null, null, null, types)) {

      while (tablesSet.next()) {
        // getting table and schema name
        String schemaName = tablesSet.getString("TABLE_SCHEM");
        String name = tablesSet.getString("TABLE_NAME");

        // converting table
        tables.put(name, convertTable(con, schemaName, name));
      }
    }
  }

  @Override protected Map<String, Table> getTableMap() {
    return tables;
  }

  @Override protected Map<String, RelProtoDataType> getTypeMap() {
    return Map.of();
  }

  @Override protected Multimap<String, Function> getFunctionMultimap() {
    return ImmutableMultimap.of();
  }

  @Override protected Map<String, Schema> getSubSchemaMap() {
    return Map.of();
  }

  /** Reads metadata of single JDBC table and converts it to Table. */
  private static Table convertTable(Connection connection, String schemaName,
      String tableName) throws SQLException {

    List<SchemaTable.Column> columns = new ArrayList<>();

    try (ResultSet columnsSet =
             connection.getMetaData().getColumns(null, schemaName, tableName, null)) {
      while (columnsSet.next()) {
        String name = columnsSet.getString("COLUMN_NAME");
        SqlTypeName type = SqlTypeName.getNameForJdbcType(columnsSet.getInt("DATA_TYPE"));
        if (type == null) {
          type = SqlTypeName.ANY;
        }

        boolean isNullable = columnsSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;

        columns.add(new SchemaTable.Column(name, type, isNullable));
      }
    }

    return new SchemaTable(columns);
  }
}
