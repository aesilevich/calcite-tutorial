package com.example.calcite;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.lookup.LikePattern;
import com.example.calcite.schema.JsonSchema;

import static java.util.Objects.requireNonNull;


public class CalciteTutorial {
  public static void main(String[] args) throws Exception {
    // loading JDBC database schema
    JsonSchema jdbcSchema = new JsonSchema();

    // creating type factory
    RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl();

    // dumping schema to stdout
    for (String tableName : jdbcSchema.tables().getNames(LikePattern.any())) {
      Table table = requireNonNull(jdbcSchema.tables().get(tableName));
      System.out.println(tableName);
      for (RelDataTypeField field : table.getRowType(typeFactory).getFieldList()) {
        System.out.println("  " + field.getName() + " " + field.getType().getFullTypeString());
      }
    }

    // creating database
    CalciteTutorialDatabase db = new CalciteTutorialDatabase();

    // converting query to relation tree
    String sql = "select u.name, d.name as dep_name\n"
        + "from users u join departments d on u.dep_id = d.id\n"
        + "where u.id > 10";
    RelRoot root = db.processQuery(sql);

    // dumping relational tree to stdout
    System.out.println(RelOptUtil.toString(root.rel));
  }
}
