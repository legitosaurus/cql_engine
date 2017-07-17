package org.opencds.cqf.cql.data.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.IValueSetEnumBinder;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.PeriodDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.primitive.*;
import ca.uhn.fhir.rest.gclient.IQuery;
import org.apache.commons.lang3.EnumUtils;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.runtime.Time;
import org.opencds.cqf.cql.terminology.ValueSetInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Christopher on 5/2/2017.
 */
public class FhirDataProviderDstu2 extends BaseDataProviderDstu2 {

    public FhirDataProviderDstu2() {
        setPackageName("ca.uhn.fhir.model.dstu2.resource");
        setFhirContext(FhirContext.forDstu2());
    }

    @Override
    public Iterable<Object> retrieve(String context, Object contextValue, String dataType,
                                     String templateId, String codePath, Iterable<Code> codes,
                                     String valueSet, String datePath, String dateLowPath,
                                     String dateHighPath, Interval dateRange)
    {
        IQuery<Bundle> search;

        // TODO: It's unclear from the FHIR documentation whether we need to use a URLEncoder.encode call on the embedded system and valueset uris here...
        StringBuilder params = new StringBuilder();

        if (templateId != null && !templateId.equals("")) {
            params.append(String.format("_profile=%s", templateId));
        }

        if (codePath == null && (codes != null || valueSet != null)) {
            throw new IllegalArgumentException("A code path must be provided when filtering on codes or a valueset.");
        }

        if (context != null && context.equals("Patient") && contextValue != null) {
            if (params.length() > 0) {
                params.append("&");
            }

            params.append(String.format("%s=%s", getPatientSearchParam(dataType), contextValue));
        }

        if (codePath != null && !codePath.equals("")) {
            if (params.length() > 0) {
                params.append("&");
            }
            if (valueSet != null && !valueSet.equals("")) {
                if (getTerminologyProvider() != null && isExpandValueSets()) {
                    ValueSetInfo valueSetInfo = new ValueSetInfo().withId(valueSet);
                    codes = getTerminologyProvider().expand(valueSetInfo);
                }
                else {
                    params.append(String.format("%s:in=%s", convertPathToSearchParam(codePath), valueSet));
                }
            }

            if (codes != null) {
                StringBuilder codeList = new StringBuilder();
                for (Code code : codes) {
                    if (codeList.length() > 0) {
                        codeList.append(",");
                    }

                    if (code.getSystem() != null) {
                        codeList.append(code.getSystem());
                        codeList.append("|");
                    }

                    codeList.append(code.getCode());
                }
                params.append(String.format("%s=%s", convertPathToSearchParam(codePath), codeList.toString()));
            }
        }

        if (dateRange != null) {
            if (dateRange.getLow() != null) {
                String lowDatePath = convertPathToSearchParam(dateLowPath != null ? dateLowPath : datePath);
                if (lowDatePath.equals("")) {
                    throw new IllegalArgumentException("A date path or low date path must be provided when filtering on a date range.");
                }

                params.append(String.format("&%s=%s%s",
                        lowDatePath,
                        dateRange.getLowClosed() ? "ge" : "gt",
                        dateRange.getLow().toString()));
            }

            if (dateRange.getHigh() != null) {
                String highDatePath = convertPathToSearchParam(dateHighPath != null ? dateHighPath : datePath);
                if (highDatePath.equals("")) {
                    throw new IllegalArgumentException("A date path or high date path must be provided when filtering on a date range.");
                }

                params.append(String.format("&%s=%s%s",
                        highDatePath,
                        dateRange.getHighClosed() ? "le" : "lt",
                        dateRange.getHigh().toString()));
            }
        }

        // TODO: Use compartment search for patient context?
        if (params.length() > 0) {
            search = getFhirClient().search().byUrl(String.format("%s?%s", dataType, params.toString()));
        }
        else {
            search = getFhirClient().search().byUrl(String.format("%s", dataType));
        }

        ca.uhn.fhir.model.dstu2.resource.Bundle results = cleanEntry(search.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class).execute(), dataType);

        return new FhirBundleCursorDstu2(getFhirClient(), results);
    }

    public IValueSetEnumBinder<Enum<?>> getBinder(Class clazz) {
        try {
            Field field = clazz.getField("VALUESET_BINDER");
            return (IValueSetEnumBinder<Enum<?>>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("Enumeration field access error in class " + clazz.getSimpleName());
        }
    }

    @Override
    protected Object fromJavaPrimitive(Object value, Object target) {
        if (target instanceof DateTimeDt) {
            return new Date();
        }
        else if (target instanceof DateDt) {
            return new Date();
        }
        else if (target instanceof TimeDt) {
            if (value instanceof Time) {
                return ((Time) value).getPartial().toString();
            }
            return new Date();
        }
        else {
            return value;
        }
    }

    protected Class resolveReturnType(Class clazz, Object target, String path) {
        if (target instanceof PeriodDt) {
            if (path.equals("start") || path.equals("end")) {
                return DateTimeDt.class;
            }
        }
        else if (target instanceof QuantityDt) {
            if (path.equals("value")) {
                return DecimalDt.class;
            }
            else if (path.equals("unit")) {
                return StringDt.class;
            }
        }
        else if (target instanceof CodingDt) {
            if (path.equals("code")) {
                return CodeDt.class;
            }
            else if (path.equals("system")) {
                return UriDt.class;
            }
            else if (path.equals("version") || path.equals("display")) {
                return StringDt.class;
            }
        }
        else if (target instanceof CodeableConceptDt) {
            if (path.equals("text")) {
                return StringDt.class;
            }
        }
        return clazz;
    }

    @Override
    public void setValue(Object target, String path, Object value) {
        if (target == null) {
            return;
        }

        Class<?> clazz = target.getClass();

        if (clazz.getSimpleName().contains("Enum") && path.equals("value")) {
            target = getBinder(clazz).fromCodeString(value.toString());
            return;
        }

        try {
            String readAccessorMethodName = String.format("%s%s%s", "get", path.substring(0, 1).toUpperCase(), path.substring(1));
            Method readAccessor = clazz.getMethod(readAccessorMethodName);

            String accessorMethodName = String.format("%s%s%s", "set", path.substring(0, 1).toUpperCase(), path.substring(1));
            Method accessor = clazz.getMethod(accessorMethodName, resolveReturnType(readAccessor.getReturnType(), target, path));
            accessor.invoke(target, fromJavaPrimitive(value, target));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not determine accessor function for property %s of type %s", path, clazz.getSimpleName()));
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(String.format("Errors occurred attempting to invoke the accessor function for property %s of type %s", path, clazz.getSimpleName()));
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not invoke the accessor function for property %s of type %s", path, clazz.getSimpleName()));
        }
    }

    protected boolean pathIsChoice(String path) {
        // Pretty consistent format: lowercase root followed by Type.
        if (pathIsChoiceOutlier(path)) return true;

        // get the substring from first uppercase to end of string
        Pattern pattern = Pattern.compile("[A-Z].*");
        Matcher matcher = pattern.matcher(path);
        String type = path;
        if (matcher.find()) {
            type = matcher.group();
        }

        try {
            Class.forName(String.format("%s.%s", getPackageName(), type));
        } catch (ClassNotFoundException e) {
            return false;
        }

        return true;
    }

    protected boolean pathIsChoiceOutlier(String path) {
        // outliers
        if (path.startsWith("notDoneReason") || path.startsWith("valueSet")
                || path.startsWith("multipleBirth") || path.startsWith("asNeeded")
                || path.startsWith("onBehalfOf") || path.startsWith("defaultValue")) {
            return true;
        }
        return false;
    }

    protected String resolveChoiceOutlier(String path) {
        if (path.startsWith("notDoneReason")) return path.replace("notDoneReason", "");
        else if (path.startsWith("valueSet")) return path.replace("valueSet", "");
        else if (path.startsWith("multipleBirth")) return path.replace("multipleBirth", "");
        else if (path.startsWith("asNeeded")) return path.replace("asNeeded", "");
        else if (path.startsWith("onBehalfOf")) return path.replace("onBehalfOf", "");
        else if (path.startsWith("defaultValue")) return path.replace("defaultValue", "");
        return path;
    }

    protected Object resolveChoiceProperty(Object target, String path, String typeName) {
        String rootPath = path.substring(0, path.indexOf(typeName));
        return resolveProperty(target, rootPath);
    }

    protected Object resolveChoiceProperty(Object target, String path) {
        String type;
        if (pathIsChoiceOutlier(path)) {
            type = resolveChoiceOutlier(path);
        }

        else {
            Pattern pattern = Pattern.compile("[A-Z].*");
            Matcher matcher = pattern.matcher(path);
            type = path;
            if (matcher.find()) {
                type = matcher.group();
            }
        }

        return resolveChoiceProperty(target, path, type);
    }

    @Override
    protected Object resolveProperty(Object target, String path) {
        if (target == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        if (clazz.getSimpleName().contains("Enum") && path.equals("value")) {
            return getBinder(clazz).toCodeString((Enum<?>) target);
        }

        try {
            String accessorMethodName = String.format("%s%s%s", "get", path.substring(0, 1).toUpperCase(), path.substring(1));
            String elementAccessorMethodName = String.format("%sElement", accessorMethodName);
            Method accessor;
            try {
                accessor = clazz.getMethod(elementAccessorMethodName);
            }
            catch (NoSuchMethodException e) {
                accessor = clazz.getMethod(accessorMethodName);
            }

            return mapPrimitive(accessor.invoke(target));

        } catch (NoSuchMethodException e) {
            if (pathIsChoice(path)) {
                return resolveChoiceProperty(target, path);
            }

            else {
                throw new IllegalArgumentException(String.format("Could not determine accessor function for property %s of type %s", path, clazz.getSimpleName()));
            }
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(String.format("Errors occurred attempting to invoke the accessor function for property %s of type %s", path, clazz.getSimpleName()));
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not invoke the accessor function for property %s of type %s", path, clazz.getSimpleName()));
        }
    }

    protected Object mapPrimitive(Object target) {
        if (target instanceof PeriodDt) {
            PeriodDt period = (PeriodDt) target;
            return new Interval(DateTime.fromJavaDate(period.getStart()), true, DateTime.fromJavaDate(period.getEnd()), true);
        }

        else if (target instanceof Date) {
            return DateTime.fromJavaDate((Date) target);
        }

        return target;
    }

    protected ca.uhn.fhir.model.dstu2.resource.Bundle cleanEntry(ca.uhn.fhir.model.dstu2.resource.Bundle bundle, String dataType) {
        List<ca.uhn.fhir.model.dstu2.resource.Bundle.Entry> entry = new ArrayList<>();
        for (ca.uhn.fhir.model.dstu2.resource.Bundle.Entry comp : bundle.getEntry()){
            if (comp.getResource().getResourceName().equals(dataType)) {
                entry.add(comp);
            }
        }
        bundle.setEntry(entry);
        return bundle;
    }

    @Override
    public Class resolveType(Object value) {
        if (value == null) {
            return Object.class;
        }

        return value.getClass();
    }

    @Override
    public Object createInstance(String typeName) {
        Class clazz = resolveType(typeName);
        if (clazz.getSimpleName().contains("Enum")) {
            return EnumUtils.getEnumList(clazz).get(0);
        }
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not create an instance of class %s.", clazz.getName()));
        }
    }

    @Override
    public Class resolveType(String typeName) {
        String truePackage = "ca.uhn.fhir.model.dstu2.composite";
        try {
            // TODO: Obviously would like to be able to automate this, but there is no programmatic way of which I'm aware
            // For the primitive types, not such a big deal.
            // For the enumerations, the type names are generated from the binding name in the spreadsheet, which doesn't make it to the StructureDefinition,
            // and the schema has no way of indicating whether the enum will be common (i.e. in Enumerations) or per resource
            switch (typeName) {
                // ca.uhn.fhir.model.dstu2.composite
                case "Coding": typeName = "CodingDt"; break;
                case "Quantity": typeName = "QuantityDt"; break;
                case "Period": typeName = "PeriodDt"; break;
                case "Range": typeName = "RangeDt"; break;
                case "CodeableConcept": typeName = "CodeableConceptDt"; break;
                case "AddressType": typeName = "AddressDt"; break;
                case "Timing": typeName = "TimingDt"; break;
                case "Money": typeName = "MoneyDt"; break;
                case "Count": typeName = "CountDt"; break;
                case "Distance": typeName = "DistanceDt"; break;
                case "Duration": typeName = "DurationDt"; break;
                case "SimpleQuantity": typeName = "SimpleQuantityDt"; break;
                case "Age": typeName = "AgeDt"; break;
                // ca.uhn.fhir.model.primitive
                case "base64Binary": typeName = "Base64BinaryDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "boolean": typeName = "BooleanDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "dateTime": typeName = "DateTimeDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "date": typeName = "DateDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "decimal": typeName = "DecimalDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "instant": typeName = "InstantDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "integer": typeName = "IntegerDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "positiveInt": typeName = "PositiveIntDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "unsignedInt": typeName = "UnsignedIntDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "string": typeName = "StringDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "code": typeName = "CodeDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "markdown": typeName = "MarkdownDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "time": typeName = "TimeDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "uri": typeName = "UriDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "uuid": typeName = "UriDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "id": typeName = "IdDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                case "oid": typeName = "OidDt"; truePackage = "ca.uhn.fhir.model.primitive"; break;
                // ca.uhn.fhir.model.dstu2.valueset -- Enums
                default: typeName += "Enum"; truePackage = "ca.uhn.fhir.model.dstu2.valueset"; break;

            }
            return Class.forName(String.format("%s.%s", truePackage, typeName));
        }
        catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(String.format("Could not resolve type %s.%s.", truePackage, typeName));
        }
    }
}