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

import java.util.List;

import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.TLAdditionalDocumentationItem;
import org.opentravel.schemacompiler.model.TLDocumentation;
import org.opentravel.schemacompiler.model.TLDocumentationItem;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.validate.FindingType;
import org.opentravel.schemacompiler.validate.ValidationFindings;
import org.opentravel.schemacompiler.validate.impl.TLValidationBuilder;
import org.opentravel.schemacompiler.validate.impl.TLValidatorBase;

/**
 * Validator for the <code>TLDocumentation</code> class.
 * 
 * @author S. Livezey
 */
public class TLDocumentationCompileValidator extends TLValidatorBase<TLDocumentation> {

    private static final int MAX_DESCRIPTION_LENGTH = 10000;

    /**
     * @see org.opentravel.schemacompiler.validate.impl.TLValidatorBase#validateFields(org.opentravel.schemacompiler.validate.Validatable)
     */
    @Override
    protected ValidationFindings validateFields(TLDocumentation target) {
        TLValidationBuilder builder = newValidationBuilder(target);

        builder.setProperty("DESCRIPTION", target.getDescription())
                .setFindingType(FindingType.ERROR).assertMaximumLength(MAX_DESCRIPTION_LENGTH);

        builder.setProperty("deprecations", target.getDeprecations())
                .setFindingType(FindingType.ERROR).assertContainsNoNullElements();

        builder.setProperty("references", target.getReferences()).setFindingType(FindingType.ERROR)
                .assertContainsNoNullElements();

        builder.setProperty("implementers", target.getImplementers())
                .setFindingType(FindingType.ERROR).assertContainsNoNullElements();

        builder.setProperty("moreInfos", target.getMoreInfos()).setFindingType(FindingType.ERROR)
                .assertContainsNoNullElements();

        builder.setProperty("otherDocs", target.getOtherDocs()).setFindingType(FindingType.ERROR)
                .assertContainsNoNullElements();

        validateMaxLength(target.getDeprecations(), "deprecations.text", builder);
        validateMaxLength(target.getImplementers(), "implementers.text", builder);
        validateMaxLength(target.getOtherDocs(), "otherDocs.text", builder);

        for (TLAdditionalDocumentationItem otherDoc : target.getOtherDocs()) {
            if (otherDoc != null) {
                // Make sure that the context value is among the declared contexts for the owning
                // library
                if ((otherDoc.getContext() != null) && (otherDoc.getContext().length() > 0)) {
                    AbstractLibrary owningLibrary = target.getOwningLibrary();

                    if (owningLibrary instanceof TLLibrary) {
                        TLLibrary library = (TLLibrary) owningLibrary;

                        if (library.getContext(otherDoc.getContext()) == null) {
                            builder.addFinding(FindingType.ERROR, "otherDocs.context",
                                    TLContextCompileValidator.ERROR_INVALID_CONTEXT,
                                    otherDoc.getContext());
                        }
                    }
                }
            }
        }

        /*
         * Supress warning messages until the editor GUI can be used to correct the problem.
         * 
         * if (hasBlankDocumentation(target.getDeprecations())) {
         * builder.addFinding(FindingType.WARNING, "deprecations.text",
         * TLValidationBuilder.ERROR_NULL_OR_BLANK); }
         * 
         * if (hasBlankDocumentation(target.getDescriptions())) {
         * builder.addFinding(FindingType.WARNING, "descriptions.text",
         * TLValidationBuilder.ERROR_NULL_OR_BLANK); }
         * 
         * if (hasBlankDocumentation(target.getReferences())) {
         * builder.addFinding(FindingType.WARNING, "references.text",
         * TLValidationBuilder.ERROR_NULL_OR_BLANK); }
         * 
         * if (hasBlankDocumentation(target.getImplementers())) {
         * builder.addFinding(FindingType.WARNING, "implementers.text",
         * TLValidationBuilder.ERROR_NULL_OR_BLANK); }
         * 
         * if (hasBlankDocumentation(target.getMoreInfos())) {
         * builder.addFinding(FindingType.WARNING, "moreInfos.text",
         * TLValidationBuilder.ERROR_NULL_OR_BLANK); }
         * 
         * if (hasBlankDocumentation(target.getOtherDocs())) {
         * builder.addFinding(FindingType.WARNING, "otherDocs.text",
         * TLValidationBuilder.ERROR_NULL_OR_BLANK); }
         */

        return builder.getFindings();
    }

    /**
     * Returns true if one or more of the items in the list provided have null or blank text field
     * values.
     * 
     * @param docItems
     *            the list of documentation items to validate
     * @return boolean
     */
    boolean hasBlankDocumentation(List<? extends TLDocumentationItem> docItems) {
        boolean hasBlankItem = false;

        for (TLDocumentationItem docItem : docItems) {
            if (docItem == null) {
                continue;
            }
            if ((docItem.getText() == null) || docItem.getText().equals("")) {
                hasBlankItem = true;
                break;
            }
        }
        return hasBlankItem;
    }

    /**
     * Performs validation checks to ensure all of the documentation values are less than or equal
     * to the maximum allowable length.
     * 
     * @param docItems
     *            the documentation items to validate
     * @param propertyName
     *            the name of the property being validated
     * @param builder
     *            the validation builder
     */
    private void validateMaxLength(List<? extends TLDocumentationItem> docItems,
            String propertyName, TLValidationBuilder builder) {
        boolean exceedsMaxLength = false;
        int actualLength = 0;

        for (TLDocumentationItem docItem : docItems) {
            if ((docItem.getText() != null)
                    && (docItem.getText().length() > MAX_DESCRIPTION_LENGTH)) {
                exceedsMaxLength = true;
                actualLength = docItem.getText().length();
                break;
            }
        }
        if (exceedsMaxLength) {
            builder.addFinding(FindingType.ERROR, propertyName,
                    TLValidationBuilder.ERROR_EXCEEDS_MAXIMUM_LENGTH, actualLength,
                    MAX_DESCRIPTION_LENGTH);
        }
    }

}
