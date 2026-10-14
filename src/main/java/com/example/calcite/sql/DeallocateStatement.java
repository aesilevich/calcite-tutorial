package com.example.calcite.sql;

import java.util.List;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.checkerframework.checker.nullness.qual.Nullable;

/** DEALLOCATE statement. */
public class DeallocateStatement extends SqlCall {
  private static final SqlOperator OPERATOR =
      new SqlSpecialOperator("DEALLOCATE", SqlKind.OTHER_DDL) {
        /** SqlCall clone method calls createCall to make copy of node so we need override it*/
        @Override public SqlCall createCall(@Nullable SqlLiteral functionQualifier,
            SqlParserPos pos, @Nullable SqlNode... operands) {
          boolean prepare = ((SqlLiteral)operands[0]).booleanValue();
          if (operands.length == 1) {
            // DEALLOCATE ALL statement
            return new DeallocateStatement(pos, prepare, null);
          } else {
            // DEALLOCATE with identifier statement
            return new DeallocateStatement(pos, prepare, (SqlIdentifier)operands[1]);
          }
        }
      };

  public final boolean prepare;

  /* Identifier passed to deallocate statement or null for DEALLOCATE ALL */
  public final @Nullable SqlIdentifier id;

  public DeallocateStatement(SqlParserPos pos, boolean prepare, @Nullable SqlIdentifier id) {
    super(pos);
    this.prepare = prepare;
    this.id = id;
  }

  @Override public SqlOperator getOperator() {
    return OPERATOR;
  }

  @Override public List<SqlNode> getOperandList() {
    SqlLiteral prepareLiteral = SqlLiteral.createBoolean(prepare, SqlParserPos.ZERO);
    return id == null ? List.of(prepareLiteral) : List.of(prepareLiteral, id);
  }

  @Override public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    writer.keyword("DEALLOCATE");

    if (prepare) {
      writer.keyword("PREPARE");
    }

    if (id == null) {
      writer.keyword("ALL");
    } else {
      id.unparse(writer, leftPrec, rightPrec);
    }
  }
}
