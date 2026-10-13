package com.example.calcite.sql;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;

/** Factory class for DEALLOCATE statements */
public class DeallocateStatements {
  private DeallocateStatements() {}

  /** Creates DEALLOCATE id statement */
  public static DeallocateStatement deallocate(SqlParserPos pos, boolean prepare,
      SqlIdentifier id) {
    return new DeallocateStatement(pos, prepare, id);
  }

  /** Creates DEALLOCATE ALL statement */
  public static DeallocateStatement deallocateAll(SqlParserPos pos, boolean prepare) {
    return new DeallocateStatement(pos, prepare, null);
  }
}
