package com.example.calcite;

import java.sql.Connection;
import java.sql.DriverManager;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.lookup.LikePattern;
import com.example.calcite.schema.JdbcSchema;
import com.example.calcite.schema.SchemaCatalog;

import static java.util.Objects.requireNonNull;


public class CalciteTutorial {
  public static void main(String[] args) throws Exception {
    // loading JDBC database schemas
    SchemaCatalog catalog;
    try (Connection connection = DriverManager.getConnection(System.getProperty("jdbc.url"))) {
      catalog = JdbcSchema.readCatalog(connection);
    }

    // creating type factory
    RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl();

    // dumping schemas to stdout
    for (String schemaName : catalog.subSchemas().getNames(LikePattern.any())) {
      Schema jdbcSchema = requireNonNull(catalog.subSchemas().get(schemaName));
      System.out.println(schemaName);
      for (String tableName : jdbcSchema.tables().getNames(LikePattern.any())) {
        Table table = requireNonNull(jdbcSchema.tables().get(tableName));
        System.out.println("  " + tableName + " (" + table.getStatistic().getRowCount()
            + " rows)");
        for (RelDataTypeField field : table.getRowType(typeFactory).getFieldList()) {
          System.out.println("    " + field.getName() + " "
              + field.getType().getFullTypeString());
        }
      }
    }

    // creating database
    CalciteTutorialDatabase db = new CalciteTutorialDatabase(catalog);

    // converting query to relation tree
    String sql = "select x + 1 from (select y + 2 as x from (values (10), (20), (30)) as t(y)) as s";
    RelRoot root = db.processQuery(sql);

    // dumping relational tree to stdout
    System.out.println(RelOptUtil.toString(root.rel));
  }
}
