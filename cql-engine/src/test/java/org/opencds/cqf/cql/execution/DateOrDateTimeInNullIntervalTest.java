package org.opencds.cqf.cql.execution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.testng.annotations.Test;

public class DateOrDateTimeInNullIntervalTest extends CqlExecutionTestBase {
    @Test
    public void testInterval() {
        Context context = new Context(library);

        Object result = context.resolveExpressionRef("Date in Null Interval Test").getExpression().evaluate(context);
        assertThat(result, is(nullValue()));

        result = context.resolveExpressionRef("DateTime in Null Interval Test").getExpression().evaluate(context);
        assertThat(result, is(nullValue()));
    }
}
