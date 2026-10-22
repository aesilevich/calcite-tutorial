package com.example.calcite.sql;

import com.example.calcite.CalciteTutorialResource;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlUtil;
import org.apache.calcite.sql.util.SqlBasicVisitor;

/** SQL node visitor that checks for unsupported operations. */
public class UnsupportedOperationValidator extends SqlBasicVisitor<Void> {
  /** Checks for unsupported call operations */
  @Override public Void visit(SqlCall call) {
    if (call.getKind() == SqlKind.ROLLUP) {
      throw SqlUtil.newContextException(call.getParserPosition(),
          CalciteTutorialResource.INSTANCE.unsupportedOperation("ROLLUP"));
    }

    return super.visit(call);
  }
}
