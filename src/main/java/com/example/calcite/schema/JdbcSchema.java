package com.example.calcite.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.sql.type.SqlTypeName;

/** Helper class for reading schemas catalog from JDBC database. */
public class JdbcSchema {
  private JdbcSchema() {}

  /** Reads all schemas from database using specified JDBC connection. */
  public static SchemaCatalog readCatalog(Connection connection) throws SQLException {
    Map<String, Schema> schemas = new HashMap<>();

    try (ResultSet schemasSet = connection.getMetaData().getSchemas()) {
      while (schemasSet.next()) {
        String name = schemasSet.getString("TABLE_SCHEM");
        schemas.put(name, readSchema(connection, name));
      }
    }

    return new SchemaCatalog(schemas);
  }

  /** Reads tables of database schema with specified name. */
  private static Schema readSchema(Connection connection, String schemaName)
      throws SQLException {
    Map<String, SchemaTable> tables = new HashMap<>();

    String[] types = {"TABLE"};
    try (ResultSet tablesSet = connection.getMetaData().getTables(null,
        escapeName(connection.getMetaData(), schemaName), null, types)) {

      while (tablesSet.next()) {
        // getting table name
        String name = tablesSet.getString("TABLE_NAME");

        // converting table
        tables.put(name, convertTable(connection, schemaName, name));
      }
    }

    return new Schema(tables);
  }

  /** Reads metadata of single JDBC table and converts it to Table. */
  private static SchemaTable convertTable(Connection connection, String schemaName,
      String tableName) throws SQLException {

    List<SchemaTable.Column> columns = new ArrayList<>();

    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet columnsSet = metaData.getColumns(null, escapeName(metaData, schemaName),
        escapeName(metaData, tableName), null)) {
      while (columnsSet.next()) {
        String name = columnsSet.getString("COLUMN_NAME");
        SqlTypeName type = SqlTypeName.getNameForJdbcType(columnsSet.getInt("DATA_TYPE"));
        if (type == null) {
          type = SqlTypeName.ANY;
        }

        boolean isNullable = columnsSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;

        // reading column size (precision/length)
        Integer columnSize = columnsSet.getInt("COLUMN_SIZE");
        if (columnsSet.wasNull()) {
          columnSize = null;
        }

        // reading decimal digits (scale)
        Integer decimalDigits = columnsSet.getInt("DECIMAL_DIGITS");
        if (columnsSet.wasNull()) {
          decimalDigits = null;
        }

        // converting column size and decimal digits to precision and scale
        Integer precision = null;
        Integer scale = null;
        switch (type) {
        case DECIMAL:
          // column size is 0 for NUMERIC without precision
          if (columnSize != null && columnSize > 0) {
            precision = columnSize;
            scale = decimalDigits;
          }
          break;

        case CHAR:
        case VARCHAR:
        case BINARY:
        case VARBINARY:
          // set precision only for values <= getMaxPrecision
          if (columnSize != null && columnSize > 0
              && columnSize <= RelDataTypeSystem.DEFAULT.getMaxPrecision(type)) {
            precision = columnSize;
          }
          break;

        case TIME:
        case TIMESTAMP:
          // column size is display width
          precision = decimalDigits;
          break;

        default:
          break;
        }

        columns.add(new SchemaTable.Column(name, type, isNullable, precision, scale));
      }
    }

    return new SchemaTable(columns, getRowCount(connection, schemaName, tableName));
  }

  /** Escapes '_' and '%' characters in name. */
  private static String escapeName(DatabaseMetaData metaData, String name)
      throws SQLException {
    String escape = metaData.getSearchStringEscape();
    return name.replace(escape, escape + escape)
        .replace("_", escape + "_")
        .replace("%", escape + "%");
  }

  /** Calculates and returns number of rows in table using COUNT(*) query. */
  private static double getRowCount(Connection connection, String schemaName,
      String tableName) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      String sql = "select count(*) from " + statement.enquoteIdentifier(schemaName, true)
          + "." + statement.enquoteIdentifier(tableName, true);

      try (ResultSet resultSet = statement.executeQuery(sql)) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }
}
