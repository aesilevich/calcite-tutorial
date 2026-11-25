package com.example.calcite.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;

import com.example.calcite.distribution.Distribution;

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

    return new SchemaTable(columns, getRowCount(connection, schemaName, tableName),
        readKeys(metaData, schemaName, tableName, columns), Distribution.SINGLETON);
  }

  /**
   * Reads primary key and unique indexes of table and returns them as sets of column indexes.
   * Skips partial indexes, indexes on expressions and indexes with nullable columns,
   * because unique index allows several rows with nulls.
   */
  private static List<ImmutableBitSet> readKeys(DatabaseMetaData metaData, String schemaName,
      String tableName, List<SchemaTable.Column> columns) throws SQLException {
    // creating map of table columns
    Map<String, Integer> columnIndexes = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      columnIndexes.put(columns.get(i).name, i);
    }

    // final set of unique keys to return
    Set<ImmutableBitSet> keys = new LinkedHashSet<>();

    // reading columns of primary key
    ImmutableBitSet.Builder primaryKey = ImmutableBitSet.builder();
    try (ResultSet keySet = metaData.getPrimaryKeys(null, schemaName, tableName)) {
      while (keySet.next()) {
        Integer index = columnIndexes.get(keySet.getString("COLUMN_NAME"));
        if (index == null) {
          throw new SQLException("Unknown primary key column '"
              + keySet.getString("COLUMN_NAME") + "' in table '" + tableName + "'");
        }

        primaryKey.set(index);
      }
    }

    // adding primary key index if set of columns is not empty
    if (!primaryKey.isEmpty()) {
      keys.add(primaryKey.build());
    }

    // map of set of index columns by index name
    Map<String, ImmutableBitSet.Builder> indexes = new LinkedHashMap<>();

    // list of names of indexes that we don't support and should skip
    Set<String> skippedIndexes = new HashSet<>();

    // reading columns of unique indexes, it's returned as flat set containing columns
    // for all indexes in a table
    try (ResultSet indexSet = metaData.getIndexInfo(null, schemaName, tableName, true, true)) {
      while (indexSet.next()) {
        // skipping table statistics row, it has no index name
        String indexName = indexSet.getString("INDEX_NAME");
        if (indexName == null) {
          continue;
        }

        // skipping non-unique indexes
        if (indexSet.getBoolean("NON_UNIQUE")) {
          continue;
        }

        // skipping partial index, it's unique only for rows matching filter condition
        if (indexSet.getString("FILTER_CONDITION") != null) {
          skippedIndexes.add(indexName);
          continue;
        }

        // skipping index on expression, it has no column name or name of unknown column
        Integer index = columnIndexes.get(indexSet.getString("COLUMN_NAME"));
        if (index == null) {
          skippedIndexes.add(indexName);
          continue;
        }

        // skipping index with nullable column, it allows several rows with nulls
        if (columns.get(index).isNullable) {
          skippedIndexes.add(indexName);
          continue;
        }

        // adding column to set of columns in index map
        indexes.computeIfAbsent(indexName, name -> ImmutableBitSet.builder()).set(index);
      }
    }

    // adding indexes into final list if they should not be skipped
    for (Map.Entry<String, ImmutableBitSet.Builder> entry : indexes.entrySet()) {
      if (!skippedIndexes.contains(entry.getKey())) {
        keys.add(entry.getValue().build());
      }
    }

    return List.copyOf(keys);
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
