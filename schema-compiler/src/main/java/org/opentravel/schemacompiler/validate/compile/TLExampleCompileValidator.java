/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentravel.schemacompiler.validate.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.XMLConstants;
import javax.xml.bind.DatatypeConverter;

import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.NamedEntity;
import org.opentravel.schemacompiler.model.TLAttribute;
import org.opentravel.schemacompiler.model.TLAttributeType;
import org.opentravel.schemacompiler.model.TLExample;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLModelElement;
import org.opentravel.schemacompiler.model.TLProperty;
import org.opentravel.schemacompiler.model.TLSimple;
import org.opentravel.schemacompiler.model.TLSimpleFacet;
import org.opentravel.schemacompiler.model.TLValueWithAttributes;
import org.opentravel.schemacompiler.model.XSDSimpleType;
import org.opentravel.schemacompiler.validate.FindingType;
import org.opentravel.schemacompiler.validate.ValidationFindings;
import org.opentravel.schemacompiler.validate.impl.IdentityResolver;
import org.opentravel.schemacompiler.validate.impl.TLValidationBuilder;
import org.opentravel.schemacompiler.validate.impl.TLValidatorBase;

/**
 * Validator for the <code>TLExample</code> class.
 * 
 * @author S. Livezey
 */
public class TLExampleCompileValidator extends TLValidatorBase<TLExample> {

    public static final String ERROR_NON_NUMERIC_EXAMPLE = "NON_NUMERIC_EXAMPLE";
    public static final String ERROR_EXCEEDS_FRACTION_DIGITS = "EXCEEDS_FRACTION_DIGITS";
    public static final String ERROR_EXCEEDS_TOTAL_DIGITS = "EXCEEDS_TOTAL_DIGITS";
    public static final String ERROR_INVALID_DATE = "INVALID_DATE";
    public static final String ERROR_INVALID_TIME = "INVALID_TIME";
    public static final String ERROR_INVALID_DATETIME = "INVALID_DATETIME";

    private static final Pattern numericValueRegex = Pattern.compile("\\-?[0-9]+(\\.[0-9]+)?");

    /**
     * @see org.opentravel.schemacompiler.validate.impl.TLValidatorBase#validateFields(org.opentravel.schemacompiler.validate.Validatable)
     */
    @Override
    protected ValidationFindings validateFields(TLExample target) {
        TLValidationBuilder builder = newValidationBuilder(target);
        NamedEntity exampleType = getSimpleType(target);

        builder.setProperty("context", target.getContext()).setFindingType(FindingType.ERROR)
                .assertNotNullOrBlank();

        if ((exampleType != null) && (target.getValue() != null)) {
            String[] exampleValues = getExampleValues(target, exampleType);

            for (String exampleValue : exampleValues) {

                builder.setProperty("value", exampleValue).setFindingType(FindingType.WARNING);

                if (exampleType instanceof TLSimple) { // Validate against a TLSimple data type
                    TLSimple simpleType = (TLSimple) exampleType;

                    if (simpleType.getMaxLength() > 0) {
                        builder.assertMaximumLength(simpleType.getMaxLength());
                    }
                    if (simpleType.getMinLength() > 0) {
                        builder.assertMinimumLength(simpleType.getMinLength());
                    }
                    if ((simpleType.getPattern() != null) && (simpleType.getPattern().length() > 0)) {
                        try {
                            Pattern.compile(simpleType.getPattern());
                            builder.assertPatternMatch(simpleType.getPattern());

                        } catch (PatternSyntaxException e) {
                            // no error - just skip the regular expression check
                        }
                    }
                    if ((simpleType.getFractionDigits() >= 0) || (simpleType.getTotalDigits() >= 0)) {
                        if (!isNumericValue(exampleValue)) {
                            builder.addFinding(FindingType.WARNING, "value",
                                    ERROR_NON_NUMERIC_EXAMPLE, exampleValue);

                        } else {
                            int fractionDigits = simpleType.getFractionDigits();
                            int totalDigits = simpleType.getTotalDigits();

                            if ((fractionDigits >= 0)
                                    && (getFractionDigits(exampleValue) > fractionDigits)) {
                                builder.addFinding(FindingType.WARNING, "value",
                                        ERROR_EXCEEDS_FRACTION_DIGITS, exampleValue, fractionDigits);
                            }
                            if ((totalDigits >= 0) && (getTotalDigits(exampleValue) > totalDigits)) {
                                builder.addFinding(FindingType.WARNING, "value",
                                        ERROR_EXCEEDS_TOTAL_DIGITS, exampleValue, totalDigits);
                            }
                        }
                    }
                } else if (exampleType instanceof XSDSimpleType) { // Validate against an XSDSimple
                                                                   // data type
                    XSDSimpleType simpleType = (XSDSimpleType) exampleType;

                    if (isXmlSchemaType(simpleType, "date")) {
                        try {
                            DatatypeConverter.parseDate(exampleValue);

                        } catch (IllegalArgumentException e) {
                            builder.addFinding(FindingType.WARNING, "value", ERROR_INVALID_DATE,
                                    exampleValue);
                        }
                    } else if (isXmlSchemaType(simpleType, "time")) {
                        try {
                            DatatypeConverter.parseTime(exampleValue);

                        } catch (IllegalArgumentException e) {
                            builder.addFinding(FindingType.WARNING, "value", ERROR_INVALID_TIME,
                                    exampleValue);
                        }
                    } else if (isXmlSchemaType(simpleType, "dateTime")) {
                        try {
                            DatatypeConverter.parseDateTime(exampleValue);

                        } catch (IllegalArgumentException e) {
                            builder.addFinding(FindingType.WARNING, "value",
                                    ERROR_INVALID_DATETIME, exampleValue);
                        }
                    }
                }
            }
        }

        builder.setProperty("context", target.getOwningEntity().getExamples())
                .setFindingType(FindingType.ERROR)
                .assertNoDuplicates(new IdentityResolver<TLExample>() {
                    public String getIdentity(TLExample entity) {
                        return (entity == null) ? null : entity.getContext();
                    }
                });

        // Make sure that the context value is among the declared contexts for the owning library
        if ((target.getContext() != null) && (target.getContext().length() > 0)) {
            AbstractLibrary owningLibrary = target.getOwningLibrary();

            if (owningLibrary instanceof TLLibrary) {
                TLLibrary library = (TLLibrary) owningLibrary;

                if (library.getContext(target.getContext()) == null) {
                    builder.addFinding(FindingType.ERROR, "context",
                            TLContextCompileValidator.ERROR_INVALID_CONTEXT, target.getContext());
                }
            }
        }
        return builder.getFindings();
    }

    /**
     * Returns the <code>TLSimple</code> entity to which this EXAMPLE applies, or null if it applies
     * to a non-TL entity type.
     * 
     * @param target
     *            the EXAMPLE being validated
     * @return TLAttributeType
     */
    private TLAttributeType getSimpleType(TLExample target) {
        List<TLModelElement> visitedElements = new ArrayList<>();
        TLModelElement simpleType = (TLModelElement) target.getOwningEntity();

        while ((simpleType != null) && !(simpleType instanceof TLSimple)
                && !(simpleType instanceof XSDSimpleType)) {
            // Exit with a null value if we encounter a circular reference
            if (visitedElements.contains(simpleType)) {
                simpleType = null;
                break;
            }
            visitedElements.add(simpleType);

            if (simpleType instanceof TLAttribute) {
                simpleType = (TLModelElement) ((TLAttribute) simpleType).getType();

            } else if (simpleType instanceof TLProperty) {
                simpleType = (TLModelElement) ((TLProperty) simpleType).getType();

            } else if (simpleType instanceof TLSimpleFacet) {
                simpleType = (TLModelElement) ((TLSimpleFacet) simpleType).getSimpleType();

            } else if (simpleType instanceof TLValueWithAttributes) {
                simpleType = (TLModelElement) ((TLValueWithAttributes) simpleType).getParentType();

            } else {
                simpleType = null;
            }
        }
        return (simpleType instanceof TLAttributeType) ? ((TLAttributeType) simpleType) : null;
    }

    /**
     * Returns a list of one or more EXAMPLE values to be validated. If the simple type is a
     * list-type, multiple values will be returned in the resulting array.
     * 
     * @param target
     *            the EXAMPLE instance being validated
     * @param simpleType
     *            the simple type against which the EXAMPLE(s) will be validated
     * @return String[]
     */
    private String[] getExampleValues(TLExample target, NamedEntity simpleType) {
        String pattern = (simpleType instanceof TLSimple) ? getPattern((TLSimple) simpleType) : null;
        boolean isListType = (simpleType instanceof TLSimple) && ((TLSimple) simpleType).isListTypeInd();
        String[] values = null;

        if ((target.getValue() == null) || target.getValue().trim().equals("")) {
            values = new String[0];

        } else if ((simpleType != null) && isListType) {

            if (pattern != null) {
                Pattern splitPattern = Pattern.compile("(" + pattern + ")\\s*");
                Matcher m;

                if ((m = splitPattern.matcher(target.getValue())).matches()) {
                    List<String> valueList = new ArrayList<>();
                    valueList.add(m.group(1));

                    while (m.find()) {
                        valueList.add(m.group(1));
                    }
                    values = valueList.toArray(new String[valueList.size()]);

                } else {
                    // If the pattern isn't recognized, we probably have an invalid EXAMPLE.
                    // The fallback is to split the EXAMPLE values using standard white space
                	// separators.
                    values = target.getValue().split("\\s+");
                }

            } else {
                values = target.getValue().split("\\s+");
            }
        } else {
            values = new String[] { target.getValue() };
        }
        return values;
    }

    /**
     * Returns the required regular expression pattern for the simple type, or null if the type does
     * not specify a pattern.
     * 
     * @param simpleType
     *            the simple type for which to return a pattern
     * @return String
     */
    private String getPattern(TLSimple simpleType) {
        TLAttributeType type = simpleType;
        String pattern = null;

        if (simpleType.isListTypeInd()) {
            type = simpleType.getParentType();
        }

        while ((type instanceof TLSimple) && (pattern == null)) {
            pattern = ((TLSimple) type).getPattern();
            type = ((TLSimple) type).getParentType();
        }
        return pattern;
    }

    /**
     * Returns true if the given EXAMPLE value is a numeric representation.
     * 
     * @param exampleValue
     *            the EXAMPLE value to analyze
     * @return boolean
     */
    private boolean isNumericValue(String exampleValue) {
        return numericValueRegex.matcher(exampleValue).matches();
    }

    /**
     * Returns the number of fraction digits to the right of the given EXAMPLE decimal value.
     * 
     * @param exampleValue
     *            the EXAMPLE value to analyze
     * @return int
     */
    private int getFractionDigits(String exampleValue) {
        int dotIdx = exampleValue.indexOf('.');
        int digitCount = 0;

        if ((dotIdx >= 0) && (dotIdx < exampleValue.length())) {
            digitCount = exampleValue.substring(dotIdx + 1).length();
        }
        return digitCount;
    }

    /**
     * Returns the total number of digits in the given EXAMPLE decimal value.
     * 
     * @param exampleValue
     *            the EXAMPLE value to analyze
     * @return int
     */
    private int getTotalDigits(String exampleValue) {
        int digitCount = exampleValue.length();

        if (exampleValue.indexOf('.') >= 0) {
            digitCount--;
        }
        return digitCount;
    }

    /**
     * Returns true if the given simple type's name matches the one passed in the 'typeName'
     * parameter.
     * 
     * @param simpleType
     *            the simple type to analyze
     * @param typeName
     *            the type name to match
     * @return boolean
     */
    private boolean isXmlSchemaType(XSDSimpleType simpleType, String typeName) {
        return (simpleType != null)
                && XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(simpleType.getNamespace())
                && (typeName != null) && typeName.equals(simpleType.getLocalName());
    }

}
