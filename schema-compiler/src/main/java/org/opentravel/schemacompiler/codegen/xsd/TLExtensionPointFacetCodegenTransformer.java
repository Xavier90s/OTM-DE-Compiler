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

import java.util.List;

import javax.xml.namespace.QName;

import org.opentravel.schemacompiler.codegen.impl.CodeGenerationTransformerContext;
import org.opentravel.schemacompiler.codegen.impl.CodegenArtifacts;
import org.opentravel.schemacompiler.codegen.impl.DocumentationFinder;
import org.opentravel.schemacompiler.codegen.util.XsdCodegenUtils;
import org.opentravel.schemacompiler.model.TLAttribute;
import org.opentravel.schemacompiler.model.TLDocumentation;
import org.opentravel.schemacompiler.model.TLExtensionPointFacet;
import org.opentravel.schemacompiler.model.TLIndicator;
import org.opentravel.schemacompiler.model.TLProperty;
import org.opentravel.schemacompiler.transform.ObjectTransformer;
import org.w3._2001.xmlschema.Annotated;
import org.w3._2001.xmlschema.Annotation;
import org.w3._2001.xmlschema.Attribute;
import org.w3._2001.xmlschema.ComplexType;
import org.w3._2001.xmlschema.Element;
import org.w3._2001.xmlschema.ExplicitGroup;
import org.w3._2001.xmlschema.TopLevelComplexType;
import org.w3._2001.xmlschema.TopLevelElement;

/**
 * Performs the translation from <code>TLExtensionPointFacet</code> objects to the JAXB nodes used
 * to produce the schema output.
 * 
 * @author S. Livezey
 */
public class TLExtensionPointFacetCodegenTransformer extends
        AbstractXsdTransformer<TLExtensionPointFacet, CodegenArtifacts> {

    @Override
    public CodegenArtifacts transform(TLExtensionPointFacet source) {
        CodegenArtifacts artifacts = new CodegenArtifacts();

        artifacts.addArtifact(createElement(source));
        artifacts.addArtifact(createType(source));
        return artifacts;
    }

    /**
     * Creates the global element definition for the extension point facet.
     * 
     * @param source
     *            the extension point facet facet being transformed
     * @return Element
     */
    protected Element createElement(TLExtensionPointFacet source) {
        Element element = new TopLevelElement();

        element.setName(XsdCodegenUtils.getGlobalElementName(source).getLocalPart());
        element.setType(new QName(source.getNamespace(), XsdCodegenUtils.getGlobalTypeName(source)));
        return element;
    }

    /**
     * Creates the complex XML schema type for the extension point facet.
     * 
     * @param source
     *            the extension point facet facet being transformed
     * @return ComplexType
     */
    protected ComplexType createType(TLExtensionPointFacet source) {
        ObjectTransformer<TLAttribute, CodegenArtifacts, CodeGenerationTransformerContext> attributeTransformer = getTransformerFactory()
                .getTransformer(TLAttribute.class, CodegenArtifacts.class);
        ObjectTransformer<TLIndicator, Annotated, CodeGenerationTransformerContext> indicatorTransformer = getTransformerFactory()
                .getTransformer(TLIndicator.class, Annotated.class);
        String typeName = XsdCodegenUtils.getGlobalTypeName(source);
        List<TLAttribute> attributeList = source.getAttributes();
        List<TLProperty> elementList = source.getElements();
        List<TLIndicator> indicatorList = source.getIndicators();
        ExplicitGroup sequence = !elementList.isEmpty() ? new ExplicitGroup() : null;
        ComplexType type = new TopLevelComplexType();

        // Declare the type and assemble the structure of objects
        type.setName(typeName);
        type.setSequence(sequence);

        // Generate elements for the sequence (if required)
        if (sequence != null) {
            ObjectTransformer<TLProperty, TopLevelElement, CodeGenerationTransformerContext> elementTransformer = getTransformerFactory()
                    .getTransformer(TLProperty.class, TopLevelElement.class);

            for (TLProperty element : elementList) {
                sequence.getParticle().add(
                        jaxbObjectFactory.createElement(elementTransformer.transform(element)));
            }
            for (TLIndicator indicator : indicatorList) {
                if (indicator.isPublishAsElement()) {
                    sequence.getParticle().add(
                            jaxbObjectFactory.createElement((TopLevelElement) indicatorTransformer
                                    .transform(indicator)));
                }
            }
        }

        // Generate attributes
        for (TLAttribute attribute : attributeList) {
            type.getAttributeOrAttributeGroup().addAll(
                    attributeTransformer.transform(attribute).getArtifactsOfType(Attribute.class));
        }
        for (TLIndicator indicator : indicatorList) {
            if (!indicator.isPublishAsElement()) {
                type.getAttributeOrAttributeGroup().add(indicatorTransformer.transform(indicator));
            }
        }

        // Generate the documentation BLOCK (if required)
        TLDocumentation sourceDoc = DocumentationFinder.getDocumentation( source );
        
        if (sourceDoc != null) {
            ObjectTransformer<TLDocumentation, Annotation, CodeGenerationTransformerContext> docTransformer =
            		getTransformerFactory().getTransformer(sourceDoc, Annotation.class);

            type.setAnnotation(docTransformer.transform(sourceDoc));
        }
        XsdCodegenUtils.addAppInfo(source, type);
        return type;
    }

}
