package org.opencds.cqf.cql.execution;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBException;

import org.cqframework.cql.elm.execution.Library;
import org.opencds.cqf.cql.execution.tests.Expression;
import org.opencds.cqf.cql.execution.tests.Group;
import org.opencds.cqf.cql.execution.tests.Output;
import org.opencds.cqf.cql.execution.tests.Tests;
import org.testng.annotations.Test;

/**
 * Created by Darren on 2018 Jan 16.
 */
public class TestIsolatedCqlExprs {

    private Tests loadTestsFile(String testsFilePath) {
        try {
            InputStream testsFileRaw = TestIsolatedCqlExprs.class.getResourceAsStream(testsFilePath);
            return JAXB.unmarshal(testsFileRaw, Tests.class);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Couldn't load tests file ["+testsFilePath+"]: " + e.toString());
        }
    }

    private Object[] loadResourceDirFileNameList(String resourceDirPath) {
        /* TODO: Should return String[] but how to do the cast that doesn't die at runtime. */
        ByteArrayInputStream fileNamesRaw
            = (ByteArrayInputStream)TestIsolatedCqlExprs.class.getResourceAsStream(resourceDirPath);
        if (fileNamesRaw == null) {
            // The directory is empty / contains no files.
            return new Object[] {};
        }
        Stream<String> fileNames = new BufferedReader(
            new InputStreamReader(fileNamesRaw, StandardCharsets.UTF_8)).lines();
        return fileNames.toArray();
    }

    private Library translate(String cql) {
        ArrayList<String> errors = new ArrayList<>();

        String elm_xml = CqlToElmLib.maybe_cql_to_elm_xml(cql, errors);

        if (elm_xml == null) {
            throw new IllegalArgumentException(errors.toString());
        }

        Library library = null;
        try {
            library = CqlLibraryReader.read(new ByteArrayInputStream(
                elm_xml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        return library;
    }

    private void runIsolatedCqlExprTest(org.opencds.cqf.cql.execution.tests.Test test) {
        Expression testQ = test.getExpression();
        if (testQ == null) {
            throw new RuntimeException("Test has no question (expression).");
        }
        String cqlExprQ = testQ.getValue();
        if (cqlExprQ == null || cqlExprQ.equals("")) {
            throw new RuntimeException("Test has no question (expression).");
        }

        Boolean expectInvalid = testQ.isInvalid() != null && testQ.isInvalid();

        try {
            // If the test expression is invalid, expect an error during
            // translation or evaluation and fail if we don't get one;
            // otherwise fail if we do get one.
            String cqlLibQ = "library TestQ define Q: " + cqlExprQ;
            Library libraryQ = translate(cqlLibQ);
            Context contextQ = new Context(libraryQ);
            contextQ.resolveExpressionRef("Q").getExpression().evaluate(contextQ);
        }
        catch (Exception e) {
            if (expectInvalid) {
                return;
            }
            else {
                throw new RuntimeException("Unexpected exception thrown for test question: " + e.toString());
            }
        }
        if (expectInvalid) {
            throw new RuntimeException("Expected exception not thrown for test question.");
        }

        List<Output> testA = test.getOutput();
        if (testA.size() != 1) {
            throw new RuntimeException("Test has not exactly one answer (output).");
        }
        String cqlExprA = testA.get(0).getValue();
        if (cqlExprA == null || cqlExprA.equals("")) {
            throw new RuntimeException("Test has not exactly one answer (output).");
        }

        try {
            String cqlLibA = "library TestA define A: " + cqlExprA;
            Library libraryA = translate(cqlLibA);
            Context contextA = new Context(libraryA);
            contextA.resolveExpressionRef("A").getExpression().evaluate(contextA);
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected exception thrown for test answer: " + e.toString());
        }

        Object result;
        try {
            String cqlLibE = "library TestE define E: Equivalent(("+cqlExprQ+"),("+cqlExprA+"))";
            Library libraryE = translate(cqlLibE);
            Context contextE = new Context(libraryE);
            result = contextE.resolveExpressionRef("E").getExpression().evaluate(contextE);
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected exception thrown for test comparison: " + e.toString());
        }

        if ((Boolean)result != true) {
            throw new RuntimeException("Actual test answer is not equivalent to expected test answer.");
        }
    }

    @Test
    public void testIsolatedCqlExprs() {
        // Load Test cases from org/hl7/fhirpath/TestIsolatedCqlExprs/tests/*.xml
        String testsDirPath = "TestIsolatedCqlExprs/tests";
        Object[] testsFileNames = loadResourceDirFileNameList(testsDirPath);
        Integer padWidth = Arrays.stream(testsFileNames)
            .map(f -> ((String)f).length()).reduce(0, (x,y) -> x > y ? x : y);
        ArrayList<String> fileResults = new ArrayList<>();
        ArrayList<String> failedTests = new ArrayList<>();
        for (Object testsFileName : testsFileNames) {
            String testsFilePath = testsDirPath + "/" + testsFileName;
            System.out.println(String.format("Running test file %s...", testsFilePath));
            Tests tests = loadTestsFile(testsFilePath);
            int testCounter = 0;
            int passCounter = 0;
            for (Group group : tests.getGroup()) {
                System.out.println(String.format("Running test group %s...", group.getName()));
                for (org.opencds.cqf.cql.execution.tests.Test test : group.getTest()) {
                    testCounter += 1;
                    try {
                        //System.out.println(String.format("Running test %s...", test.getName()));
                        runIsolatedCqlExprTest(test);
                        passCounter += 1;
                        System.out.println(String.format("Test %s passed.", test.getName()));
                    }
                    catch (Exception e) {
                        failedTests.add(testsFileName + " -> " + group.getName() + " -> " + test.getName());
                        System.out.println(String.format("Test %s failed with exception: %s", test.getName(), e.toString()));
                    }
                }
                //System.out.println(String.format("Finished test group %s.", group.getName()));
            }
            fileResults.add(String.format("%-"+padWidth.toString()+"s %3d/%3d", testsFileName, passCounter, testCounter));
            System.out.println(String.format("Tests file %s passed %s of %s tests.", testsFilePath, passCounter, testCounter));
        }
        System.out.println("==================================================");
        System.out.println("TestIsolatedCqlExprs Results Summary:");
        System.out.println(" * Each file's passed/total test count:");
        for (String fileResult : fileResults) {
            System.out.println("   * " + fileResult);
        }
        System.out.println(" * List of failed tests:");
        for (String failedTest : failedTests) {
            System.out.println("   * " + failedTest);
        }
        System.out.println("==================================================");
    }
}
