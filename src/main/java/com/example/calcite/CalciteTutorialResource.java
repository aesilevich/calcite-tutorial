package com.example.calcite;

import org.apache.calcite.runtime.Resources;
import org.apache.calcite.runtime.Resources.BaseMessage;
import org.apache.calcite.runtime.Resources.ExInst;
import org.apache.calcite.sql.validate.SqlValidatorException;

/** Error messages for tutorial database. */
public interface CalciteTutorialResource {
  CalciteTutorialResource INSTANCE = Resources.create(CalciteTutorialResource.class);

  @BaseMessage("{0} operation is not supported in this engine")
  ExInst<SqlValidatorException> unsupportedOperation(String operation);
}
