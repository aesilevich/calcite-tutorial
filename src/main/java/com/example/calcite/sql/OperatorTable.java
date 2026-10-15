package com.example.calcite.sql;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.SqlBasicFunction;
import org.apache.calcite.sql.SqlCallBinding;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlOperatorBinding;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.SqlOperandTypeInference;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.ReflectiveSqlOperatorTable;

/** Custom operator table for tutorial. */
public class OperatorTable extends ReflectiveSqlOperatorTable {
  /** Advanced ABS function. */
  public static final SqlFunction ABS =
      SqlBasicFunction.create("ABS",
          new AbsReturnTypeInference(),
          OperandTypes.or(
              OperandTypes.typeName(SqlTypeName.SMALLINT),
              OperandTypes.typeName(SqlTypeName.INTEGER),
              OperandTypes.typeName(SqlTypeName.BIGINT),
              OperandTypes.typeName(SqlTypeName.NULL)),
          SqlFunctionCategory.NUMERIC)
          .withOperandTypeInference(new AbsOperandTypeInference());

  private static final OperatorTable INSTANCE = (OperatorTable) new OperatorTable().init();

  /** Returns operator table instance. */
  public static OperatorTable instance() {
    return INSTANCE;
  }

  /**
   * Return type inference for advanced ABS operation:
   * - infers INTEGER (32-bit) for SMALLINT (16-bit) operand
   * - infers BIGINT (64-bit) for INTEGER (32-bit) and BIGINT (64-bit) operands
   */
  private static class AbsReturnTypeInference implements SqlReturnTypeInference {
    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
      RelDataType operandType = opBinding.getOperandType(0);

      // calculating return type from operand type
      SqlTypeName typeName = switch (operandType.getSqlTypeName()) {
        case SMALLINT -> SqlTypeName.INTEGER;
        case INTEGER -> SqlTypeName.BIGINT;
        case BIGINT -> SqlTypeName.BIGINT;
        case NULL -> SqlTypeName.INTEGER;
        default -> throw new AssertionError("Unexpected operand type " + operandType);
      };

      // creating type with nullable flag
      RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
      return typeFactory.createTypeWithNullability(typeFactory.createSqlType(typeName),
          operandType.isNullable());
    }
  }

  /**
   * Operand type inference for advanced ABS operation. Always infers SMALLINT type for operand.
   */
  private static class AbsOperandTypeInference implements SqlOperandTypeInference {
    @Override public void inferOperandTypes(SqlCallBinding callBinding, RelDataType returnType,
        RelDataType[] operandTypes) {
      RelDataTypeFactory typeFactory = callBinding.getTypeFactory();
      operandTypes[0] = typeFactory.createTypeWithNullability(
          typeFactory.createSqlType(SqlTypeName.SMALLINT), true);
    }
  }
}
