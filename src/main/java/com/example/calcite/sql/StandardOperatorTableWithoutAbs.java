package com.example.calcite.sql;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.validate.SqlNameMatcher;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Standard operator table without standard ABS function. */
public class StandardOperatorTableWithoutAbs extends SqlStdOperatorTable {
  @Override public void lookupOperatorOverloads(SqlIdentifier opName,
      @Nullable SqlFunctionCategory category, SqlSyntax syntax,
      List<SqlOperator> operatorList, SqlNameMatcher nameMatcher) {
    super.lookupOperatorOverloads(opName, category, syntax, operatorList, nameMatcher);
    operatorList.removeIf(op -> op == SqlStdOperatorTable.ABS);
  }

  @Override public List<SqlOperator> getOperatorList() {
    List<SqlOperator> operators = new ArrayList<>(super.getOperatorList());
    operators.removeIf(op -> op == SqlStdOperatorTable.ABS);
    return operators;
  }
}
