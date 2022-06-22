package com.linkedin.metadata.test.eval.operation;

import com.linkedin.metadata.test.definition.operation.OperationParam;
import com.linkedin.metadata.test.definition.operation.OperationParams;
import com.linkedin.metadata.test.eval.ResolvedParams;
import com.linkedin.metadata.test.exception.OperationParamsInvalidException;

import static com.linkedin.metadata.test.definition.operation.ParamKeyConstants.PREDICATES;


/**
 * Or operation evaluator. Checks whether any of input predicates returns true
 */
public class OrEvaluator extends BaseOperationEvaluator {

  @Override
  public String getOperation() {
    return "or";
  }

  @Override
  public void validate(OperationParams params) throws OperationParamsInvalidException {
    if (params.hasKeyOfType(PREDICATES, OperationParam.Type.PREDICATE)) {
      return;
    }
    throw new OperationParamsInvalidException(
        "or operation requires param \"predicates\" containing the list of predicates to compose");
  }

  @Override
  public boolean evaluate(ResolvedParams resolvedParams) throws OperationParamsInvalidException {
    if (resolvedParams.hasKeyOfType(PREDICATES, OperationParam.Type.PREDICATE)) {
      return resolvedParams.getResolvedParam(PREDICATES)
          .getResolvedPredicateParam()
          .stream()
          .anyMatch(Boolean::valueOf);
    }
    throw new OperationParamsInvalidException(
        "or operation requires param \"predicates\" containing the list of predicates to compose");
  }
}
