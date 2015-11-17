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
package org.opentravel.schemacompiler.codegen.xsd;

import org.opentravel.schemacompiler.codegen.impl.CodegenArtifacts;
import org.opentravel.schemacompiler.codegen.util.FacetCodegenUtils;
import org.opentravel.schemacompiler.codegen.xsd.facet.FacetCodegenDelegate;
import org.opentravel.schemacompiler.codegen.xsd.facet.FacetCodegenDelegateFactory;
import org.opentravel.schemacompiler.codegen.xsd.facet.FacetCodegenElements;
import org.opentravel.schemacompiler.model.TLChoiceObject;
import org.opentravel.schemacompiler.model.TLFacet;
import org.opentravel.schemacompiler.model.TLFacetType;

/**
 * Performs the translation from <code>TLChoiceObject</code> objects to the JAXB nodes used to
 * produce the schema output.
 * 
 * @author S. Livezey
 */
public class TLChoiceObjectCodegenTransformer extends
		AbstractXsdTransformer<TLChoiceObject, CodegenArtifacts> {
	
	/**
	 * @see org.opentravel.schemacompiler.transform.ObjectTransformer#transform(java.lang.Object)
	 */
	@Override
	public CodegenArtifacts transform(TLChoiceObject source) {
        FacetCodegenDelegateFactory delegateFactory = new FacetCodegenDelegateFactory(context);
        FacetCodegenElements elementArtifacts = new FacetCodegenElements();
        CodegenArtifacts otherArtifacts = new CodegenArtifacts();

        generateFacetArtifacts(delegateFactory.getDelegate(source.getSharedFacet()), elementArtifacts, otherArtifacts);
        
        for (TLFacet choiceFacet : source.getChoiceFacets()) {
            generateFacetArtifacts(delegateFactory.getDelegate(choiceFacet), elementArtifacts, otherArtifacts);
        }
        for (TLFacet ghostFacet : FacetCodegenUtils.findGhostFacets(source, TLFacetType.CHOICE)) {
            generateFacetArtifacts(delegateFactory.getDelegate(ghostFacet), elementArtifacts, otherArtifacts);
        }

        return buildCorrelatedArtifacts(source, elementArtifacts, otherArtifacts);
	}
	
    /**
     * Utility method that generates both element and non-element schema content for the source
     * facet of the given delegate.
     * 
     * @param facetDelegate  the facet code generation delegate
     * @param elementArtifacts  the container for all generated schema elements
     * @param otherArtifacts  the container for all generated non-element schema artifacts
     */
    private void generateFacetArtifacts(FacetCodegenDelegate<TLFacet> facetDelegate,
            FacetCodegenElements elementArtifacts, CodegenArtifacts otherArtifacts) {
        elementArtifacts.addAll(facetDelegate.generateElements());
        otherArtifacts.addAllArtifacts(facetDelegate.generateArtifacts());
    }

}