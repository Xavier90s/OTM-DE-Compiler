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

import org.opentravel.schemacompiler.codegen.util.EnumCodegenUtils;
import org.opentravel.schemacompiler.model.TLEnumValue;
import org.opentravel.schemacompiler.validate.FindingType;
import org.opentravel.schemacompiler.validate.ValidationFindings;
import org.opentravel.schemacompiler.validate.impl.IdentityResolver;
import org.opentravel.schemacompiler.validate.impl.TLValidationBuilder;
import org.opentravel.schemacompiler.validate.impl.TLValidatorBase;

/**
 * Validator for the <code>TLEnumValue</code> class.
 * 
 * @author S. Livezey
 */
public class TLEnumValueCompileValidator extends TLValidatorBase<TLEnumValue> {

    /**
     * @see org.opentravel.schemacompiler.validate.impl.TLValidatorBase#validateChildren(org.opentravel.schemacompiler.validate.Validatable)
     */
    @Override
    protected ValidationFindings validateChildren(TLEnumValue target) {
        TLValidationBuilder builder = newValidationBuilder(target);

        builder.setProperty("literal", target.getLiteral()).setFindingType(FindingType.ERROR)
                .assertNotNullOrBlank().assertMaximumLength(80);

        builder.setProperty("literal", EnumCodegenUtils.getInheritedValues(target.getOwningEnum()))
                .setFindingType(FindingType.ERROR)
                .assertNoDuplicates(new IdentityResolver<TLEnumValue>() {
                    public String getIdentity(TLEnumValue enumValue) {
                        return (enumValue == null) ? null : enumValue.getLiteral();
                    }
                });

        builder.setProperty("equivalents", target.getEquivalents())
                .setFindingType(FindingType.ERROR).assertNotNull().assertContainsNoNullElements();

        return builder.getFindings();
    }

}
