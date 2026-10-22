package com.example.calcite.sql;

import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.runtime.CalciteContextException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.calcite.CalciteTutorialDatabase;
import com.example.calcite.schema.JsonSchema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for advanced ABS function. */
class AdvancedAbsTest {
  private static final String SCHEMA_JSON = """
      {
        "tables": [
          {
            "name": "numbers",
            "columns": [
              {"name": "int16", "type": "smallint", "nullable": false},
              {"name": "int32", "type": "integer", "nullable": false},
              {"name": "int64", "type": "bigint", "nullable": false},
              {"name": "float32", "type": "real", "nullable": false},
              {"name": "float64", "type": "double", "nullable": false},
              {"name": "str", "type": "varchar", "nullable": false},
              {"name": "int16_null", "type": "smallint"},
              {"name": "int32_null", "type": "integer"},
              {"name": "int64_null", "type": "bigint"},
              {"name": "float32_null", "type": "real"},
              {"name": "float64_null", "type": "double"},
              {"name": "str_null", "type": "varchar"}
            ]
          }
        ]
      }
      """;

  private CalciteTutorialDatabase database;

  @BeforeEach void setUp() throws Exception {
    database = new CalciteTutorialDatabase(JsonSchema.parse(SCHEMA_JSON));
  }

  /** Tests advanced ABS function for SMALLINT. */
  @Test void testAbsInt16() throws Exception {
    checkAbs("ABS", "int16", "INTEGER NOT NULL");
  }

  /** Tests advanced ABS function for nullable SMALLINT. */
  @Test void testAbsInt16Null() throws Exception {
    checkAbs("ABS", "int16_null", "INTEGER");
  }

  /** Tests advanced ABS function for INTEGER. */
  @Test void testAbsInt32() throws Exception {
    checkAbs("ABS", "int32", "BIGINT NOT NULL");
  }

  /** Tests advanced ABS function for nullable INTEGER. */
  @Test void testAbsInt32Null() throws Exception {
    checkAbs("ABS", "int32_null", "BIGINT");
  }

  /** Tests advanced ABS function for BIGINT. */
  @Test void testAbsInt64() throws Exception {
    checkAbs("ABS", "int64", "BIGINT NOT NULL");
  }

  /** Tests advanced ABS function for nullable BIGINT. */
  @Test void testAbsInt64Null() throws Exception {
    checkAbs("ABS", "int64_null", "BIGINT");
  }

  /** Tests advanced ABS function for NULL. */
  @Test void testAbsNull() throws Exception {
    checkAbs("ABS", "null", "INTEGER");
  }

  /** Tests advanced ABS function for ?. */
  @Test void testAbsUnknown() throws Exception {
    checkAbs("ABS", "?", "INTEGER");
  }

  /** Tests that advanced ABS function can't be used with REAL. */
  @Test void testAbsFloat() {
    CalciteContextException e = assertThrows(CalciteContextException.class,
        () -> database.processQuery("select abs(float32) from numbers"));

    assertThat(e.getMessage(),
        containsString("Cannot apply 'ABS' to arguments of type 'ABS(<REAL>)'"));
  }

  /** Tests that advanced ABS function can't be used with VARCHAR. */
  @Test void testAbsString() {
    CalciteContextException e = assertThrows(CalciteContextException.class,
        () -> database.processQuery("select abs(str) from numbers"));

    assertThat(e.getMessage(),
        containsString("Cannot apply 'ABS' to arguments of type 'ABS(<VARCHAR>)'"));
  }

  /**
   * Checks that ABS for column with specified name uses
   * custom function and has expected return type.
   */
  private void checkAbs(String function, String operand, String expectedType) throws Exception {
    RelRoot root =
        database.convertQuery("select " + function + "(" + operand + ") from numbers");

    // checking that custom function is used
    LogicalProject project = (LogicalProject) root.rel;
    RexCall call = (RexCall) project.getProjects().get(0);
    assertThat(call.getOperator().getName(), is(function));
    assertThat(OperatorTable.instance().getOperatorList(),
        hasItem(sameInstance(call.getOperator())));

    // checking abs return type
    assertThat(call.getType().getFullTypeString(), is(expectedType));
  }
}
