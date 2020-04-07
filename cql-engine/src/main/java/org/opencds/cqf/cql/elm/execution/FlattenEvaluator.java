package org.opencds.cqf.cql.elm.execution;

import java.util.ArrayList;
import java.util.List;

import org.opencds.cqf.cql.exception.InvalidOperatorArgument;
import org.opencds.cqf.cql.execution.Context;

/*
flatten(argument List<List<T>>) List<T>

The flatten operator flattens a list of lists into a single list.
*/

public class FlattenEvaluator extends org.cqframework.cql.elm.execution.Flatten {

    public static Object flatten(Object operand) {
        if (operand == null) {
            return null;
        }

        if (operand instanceof Iterable) {
            List<Object> resultList = new ArrayList<>();
            for (Object element : (Iterable) operand) {
                for (Object subElement : (Iterable) element) {
                    resultList.add(subElement);
                }
            }

            return resultList;
        }

        throw new InvalidOperatorArgument(
                "Flatten(List<List<T>>)",
                String.format("Flatten(%s)", operand.getClass().getName())
        );
    }


    @Override
    protected Object internalEvaluate(Context context) {
        Object operand = getOperand().evaluate(context);

        return flatten(operand);
    }
}
