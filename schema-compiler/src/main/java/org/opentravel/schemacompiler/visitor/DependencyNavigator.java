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
package org.opentravel.schemacompiler.visitor;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.opentravel.schemacompiler.codegen.util.PropertyCodegenUtils;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.BuiltInLibrary;
import org.opentravel.schemacompiler.model.LibraryMember;
import org.opentravel.schemacompiler.model.NamedEntity;
import org.opentravel.schemacompiler.model.TLAction;
import org.opentravel.schemacompiler.model.TLActionFacet;
import org.opentravel.schemacompiler.model.TLActionResponse;
import org.opentravel.schemacompiler.model.TLAlias;
import org.opentravel.schemacompiler.model.TLAttribute;
import org.opentravel.schemacompiler.model.TLBusinessObject;
import org.opentravel.schemacompiler.model.TLChoiceObject;
import org.opentravel.schemacompiler.model.TLClosedEnumeration;
import org.opentravel.schemacompiler.model.TLContextualFacet;
import org.opentravel.schemacompiler.model.TLCoreObject;
import org.opentravel.schemacompiler.model.TLExtension;
import org.opentravel.schemacompiler.model.TLExtensionPointFacet;
import org.opentravel.schemacompiler.model.TLFacet;
import org.opentravel.schemacompiler.model.TLInclude;
import org.opentravel.schemacompiler.model.TLIndicator;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLListFacet;
import org.opentravel.schemacompiler.model.TLMemberField;
import org.opentravel.schemacompiler.model.TLNamespaceImport;
import org.opentravel.schemacompiler.model.TLOpenEnumeration;
import org.opentravel.schemacompiler.model.TLOperation;
import org.opentravel.schemacompiler.model.TLParamGroup;
import org.opentravel.schemacompiler.model.TLParameter;
import org.opentravel.schemacompiler.model.TLProperty;
import org.opentravel.schemacompiler.model.TLResource;
import org.opentravel.schemacompiler.model.TLRole;
import org.opentravel.schemacompiler.model.TLService;
import org.opentravel.schemacompiler.model.TLSimple;
import org.opentravel.schemacompiler.model.TLSimpleFacet;
import org.opentravel.schemacompiler.model.TLValueWithAttributes;
import org.opentravel.schemacompiler.model.XSDComplexType;
import org.opentravel.schemacompiler.model.XSDElement;
import org.opentravel.schemacompiler.model.XSDLibrary;
import org.opentravel.schemacompiler.model.XSDSimpleType;
import org.opentravel.schemacompiler.util.URLUtils;

/**
 * Navigates all of the direct and indirect model dependencies of a <code>LibraryMember</code>
 * instance in a pre-order, depth-first fashion.
 * 
 * @author S. Livezey
 */
public class DependencyNavigator extends AbstractNavigator<NamedEntity> {

    /**
     * Default constructor.
     */
    public DependencyNavigator() {
    }

    /**
     * Constructor that initializes the visitor to be notified when model elements are encountered
     * during navigation.
     * 
     * @param visitor
     *            the visitor to be notified when model elements are encountered
     */
    public DependencyNavigator(ModelElementVisitor visitor) {
        super(visitor);
    }

    /**
     * Navigates the dependencies of all elements of the given library in a depth-first fashion
     * using the given visitor for notification callbacks.
     * 
     * @param library
     *            the library whose dependencies should be navigated
     * @param visitor
     *            the visitor to be notified when dependencies are encountered
     */
    public static void navigate(AbstractLibrary library, ModelElementVisitor visitor) {
        new DependencyNavigator(visitor).navigateLibrary(library);

    }

    /**
     * Navigates all dependencies of the given element in a depth-first fashion using the given
     * visitor for notification callbacks.
     * 
     * @param target
     *            the library entity whose dependencies should be navigated
     * @param visitor
     *            the visitor to be notified when dependencies are encountered
     */
    public static void navigate(NamedEntity target, ModelElementVisitor visitor) {
        new DependencyNavigator(visitor).navigate(target);
    }

    /**
     * @see org.opentravel.schemacompiler.visitor.AbstractNavigator#navigate(java.lang.Object)
     */
    @Override
    public void navigate(NamedEntity target) {
        navigateDependency(target);
    }

    /**
     * Called when a top-level library is encountered during navigation.
     * 
     * @param library
     *            the library whose dependencies should be navigated
     */
    public void navigateLibrary(AbstractLibrary library) {
    	List<LibraryMember> libraryMembers = new ArrayList<>( library.getNamedMembers() );
    	
        for (NamedEntity libraryMember : libraryMembers) {
            if (library instanceof TLLibrary) {
                visitor.visitUserDefinedLibrary((TLLibrary) library);

            } else if (library instanceof XSDLibrary) {
                visitor.visitLegacySchemaLibrary((XSDLibrary) library);

            } else if (library instanceof BuiltInLibrary) {
                visitor.visitBuiltInLibrary((BuiltInLibrary) library);
            }
            navigate(libraryMember);
        }
    }

    /**
     * Called when a <code>TLService</code> instance is encountered during model navigation.
     * 
     * @param service
     *            the service entity to visit and navigate
     */
    protected void navigateService(TLService service) {
        if (canVisit(service) && visitor.visitService(service)) {
            for (TLOperation operation : service.getOperations()) {
                navigateOperation(operation);
            }
        }
    }

    /**
     * Called when a <code>TLOperation</code> instance is encountered during model navigation.
     * 
     * @param operation
     *            the operation entity to visit and navigate
     */
    protected void navigateOperation(TLOperation operation) {
        if (canVisit(operation) && visitor.visitOperation(operation)) {
            navigateFacet(operation.getRequest());
            navigateFacet(operation.getResponse());
            navigateFacet(operation.getNotification());
            navigateExtension(operation.getExtension());
        }
    }

    /**
     * Called when a <code>TLSimple</code> instance is encountered during model navigation.
     * 
     * @param simple
     *            the simple entity to visit and navigate
     */
    protected void navigateSimple(TLSimple simple) {
        if (canVisit(simple) && visitor.visitSimple(simple)) {
            navigateDependency(simple.getParentType());
        }
    }

    /**
     * Called when a <code>TLValueWithAttributes</code> instance is encountered during model
     * navigation.
     * 
     * @param valueWithAttributes
     *            the simple entity to visit and navigate
     */
    protected void navigateValueWithAttributes(TLValueWithAttributes valueWithAttributes) {
        if (canVisit(valueWithAttributes) && visitor.visitValueWithAttributes(valueWithAttributes)) {
            for (TLAttribute attribute : valueWithAttributes.getAttributes()) {
                navigateAttribute(attribute);
            }
            navigateDependency(valueWithAttributes.getParentType());
        }
    }

    /**
     * Called when a <code>TLClosedEnumeration</code> instance is encountered during model
     * navigation.
     * 
     * @param enumeration
     *            the enumeration entity to visit and navigate
     */
    protected void navigateClosedEnumeration(TLClosedEnumeration enumeration) {
        if (canVisit(enumeration) && visitor.visitClosedEnumeration(enumeration)) {
            navigateExtension(enumeration.getExtension());
        }
    }

    /**
     * Called when a <code>TLOpenEnumeration</code> instance is encountered during model navigation.
     * 
     * @param enumeration
     *            the enumeration entity to visit and navigate
     */
    protected void navigateOpenEnumeration(TLOpenEnumeration enumeration) {
        if (canVisit(enumeration) && visitor.visitOpenEnumeration(enumeration)) {
            navigateExtension(enumeration.getExtension());
        }
    }

    /**
     * Called when a <code>TLChoiceObject</code> instance is encountered during model navigation.
     * 
     * @param choiceObject
     *            the choice object entity to visit and navigate
     */
    protected void navigateChoiceObject(TLChoiceObject choiceObject) {
        if (canVisit(choiceObject) && visitor.visitChoiceObject(choiceObject)) {
            navigateFacet(choiceObject.getSharedFacet());
            navigateExtension(choiceObject.getExtension());

            for (TLContextualFacet choiceFacet : choiceObject.getChoiceFacets()) {
                navigateContextualFacet(choiceFacet);
            }
        }
    }

    /**
     * Called when a <code>TLCoreObject</code> instance is encountered during model navigation.
     * 
     * @param coreObject
     *            the core object entity to visit and navigate
     */
    protected void navigateCoreObject(TLCoreObject coreObject) {
        if (canVisit(coreObject) && visitor.visitCoreObject(coreObject)) {
            navigateSimpleFacet(coreObject.getSimpleFacet());
            navigateFacet(coreObject.getSummaryFacet());
            navigateFacet(coreObject.getDetailFacet());
            navigateListFacet(coreObject.getSimpleListFacet());
            navigateListFacet(coreObject.getSummaryListFacet());
            navigateListFacet(coreObject.getDetailListFacet());
            navigateExtension(coreObject.getExtension());
        }
    }

    /**
     * Called when a <code>TLBusinessObject</code> instance is encountered during model navigation.
     * 
     * @param businessObject
     *            the business object entity to visit and navigate
     */
    protected void navigateBusinessObject(TLBusinessObject businessObject) {
        if (canVisit(businessObject) && visitor.visitBusinessObject(businessObject)) {
            navigateFacet(businessObject.getIdFacet());
            navigateFacet(businessObject.getSummaryFacet());
            navigateFacet(businessObject.getDetailFacet());

            for (TLContextualFacet customFacet : businessObject.getCustomFacets()) {
            	navigateContextualFacet(customFacet);
            }
            for (TLContextualFacet queryFacet : businessObject.getQueryFacets()) {
            	navigateContextualFacet(queryFacet);
            }
            for (TLContextualFacet updateFacet : businessObject.getUpdateFacets()) {
            	navigateContextualFacet(updateFacet);
            }
            navigateExtension(businessObject.getExtension());
        }
    }

    /**
     * Called when a <code>TLResource</code> instance is encountered during model navigation.
     * 
     * @param resource
     *            the resource entity to visit and navigate
     */
    protected void navigateResource(TLResource resource) {
        if (canVisit(resource) && visitor.visitResource(resource)) {
            navigateBusinessObject(resource.getBusinessObjectRef());
            navigateExtension(resource.getExtension());
            
            for (TLActionFacet actionFacet : resource.getActionFacets()) {
            	navigateActionFacet(actionFacet);
            }
            for (TLAction action : resource.getActions()) {
            	navigateAction(action);	
            }
        }
        addVisitedNode(resource);
    }

    /**
     * Called when a <code>TLParamGroup</code> instance is encountered during model navigation.
     * 
     * @param paramGroup
     *            the parameter group entity to visit and navigate
     */
    protected void navigateParamGroup(TLParamGroup paramGroup) {
        if (canVisit(paramGroup) && visitor.visitParamGroup(paramGroup)) {
        	navigateFacet(paramGroup.getFacetRef());
            
            for (TLParameter parameter : paramGroup.getParameters()) {
            	navigateParameter(parameter);
            }
        }
        addVisitedNode(paramGroup);
    }
    
    /**
     * Called when a <code>TLParameter</code> instance is encountered during model navigation.
     * 
     * @param parameter
     *            the parameter entity to visit and navigate
     */
    protected void navigateParameter(TLParameter parameter) {
        if (canVisit(parameter) && visitor.visitParameter(parameter)) {
        	TLMemberField<?> fieldRef = parameter.getFieldRef();
        	
        	if (fieldRef instanceof TLAttribute) {
        		navigateAttribute((TLAttribute) fieldRef);
        		
        	} else if (fieldRef instanceof TLProperty) {
        		navigateElement((TLProperty) fieldRef);
        		
        	} else if (fieldRef instanceof TLIndicator) {
        		navigateIndicator((TLIndicator) fieldRef);
        	}
        }
        addVisitedNode(parameter);
    }
    
    /**
     * Called when a <code>TLAction</code> instance is encountered during model navigation.
     * 
     * @param action
     *            the action entity to visit and navigate
     */
    protected void navigateAction(TLAction action) {
        if (canVisit(action) && visitor.visitAction(action)) {
        	if (action.getRequest() != null) {
            	navigate( action.getRequest().getPayloadType() );
        	}
            
            for (TLActionResponse response : action.getResponses()) {
            	navigate( response.getPayloadType() );
            }
        }
        addVisitedNode(action);
    }
    
    /**
     * Called when a <code>TLExtensionPointFacet</code> instance is encountered during model
     * navigation.
     * 
     * @param extensionPointFacet
     *            the extension point facet entity to visit and navigate
     */
    protected void navigateExtensionPointFacet(TLExtensionPointFacet extensionPointFacet) {
        if (canVisit(extensionPointFacet) && visitor.visitExtensionPointFacet(extensionPointFacet)) {

            if (extensionPointFacet.getExtension() != null) {
                navigateExtension(extensionPointFacet.getExtension());
            }
            for (TLAttribute attribute : extensionPointFacet.getAttributes()) {
                navigateAttribute(attribute);
            }
            for (TLProperty element : extensionPointFacet.getElements()) {
                navigateElement(element);
            }
            for (TLIndicator indicator : extensionPointFacet.getIndicators()) {
                navigateIndicator(indicator);
            }
        }
    }

    /**
     * Called when a n<code>XSDLibrary</code> instance is encountered during model navigation.
     * 
     * @param xsdLibrary
     *            the XSD library to visit and navigate
     */
    protected void navigateXSDLibrary(XSDLibrary xsdLibrary) {
        if (canVisit(xsdLibrary) && (xsdLibrary.getOwningModel() != null)
                && visitor.visitLegacySchemaLibrary(xsdLibrary)) {

            for (TLInclude include : xsdLibrary.getIncludes()) {
                if (include.getPath() != null) {
                    URL includedUrl = getReferencedLibraryURL(include.getPath(), xsdLibrary);
                    AbstractLibrary includedLibrary = xsdLibrary.getOwningModel().getLibrary(
                            includedUrl);

                    if ((includedLibrary != null) && (includedLibrary instanceof XSDLibrary)) {
                        navigateXSDLibrary((XSDLibrary) includedLibrary);
                    }
                }
            }

            for (TLNamespaceImport nsImport : xsdLibrary.getNamespaceImports()) {
                if (nsImport.getFileHints() != null) {
                    for (String fileHint : nsImport.getFileHints()) {
                        URL importedUrl = getReferencedLibraryURL(fileHint, xsdLibrary);
                        AbstractLibrary importedLibrary = xsdLibrary.getOwningModel().getLibrary(
                                importedUrl);

                        if ((importedLibrary != null) && (importedLibrary instanceof XSDLibrary)) {
                            navigateXSDLibrary((XSDLibrary) importedLibrary);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the full URL that is referenced by the specified relative URL path.
     * 
     * @param relativeUrl
     *            the relative URL path to resolve
     * @param referringLibrary
     *            the library that is the
     * @return
     */
    private URL getReferencedLibraryURL(String relativeUrl, AbstractLibrary referringLibrary) {
        URL resolvedUrl = null;
        try {
            URL libraryFolderUrl = URLUtils.getParentURL(referringLibrary.getLibraryUrl());
            resolvedUrl = URLUtils.getResolvedURL(relativeUrl, libraryFolderUrl);

        } catch (MalformedURLException e) {
            // no error - return a null URL
        }
        return resolvedUrl;
    }

    /**
     * Called when an <code>XSDSimpleType</code> instance is encountered during model navigation.
     * 
     * @param xsdSimple
     *            the XSD simple-type entity to visit and navigate
     */
    protected void navigateXSDSimpleType(XSDSimpleType xsdSimple) {
        if (canVisit(xsdSimple) && visitor.visitXSDSimpleType(xsdSimple)) {
            AbstractLibrary owningLibrary = xsdSimple.getOwningLibrary();

            if (owningLibrary instanceof XSDLibrary) {
                navigateXSDLibrary((XSDLibrary) owningLibrary);
            }
        }
    }

    /**
     * Called when a <code>XSDComplexType</code> instance is encountered during model navigation.
     * 
     * @param xsdComplex
     *            the XSD complex-type entity to visit and navigate
     */
    protected void navigateXSDComplexType(XSDComplexType xsdComplex) {
        if (canVisit(xsdComplex) && visitor.visitXSDComplexType(xsdComplex)) {
            AbstractLibrary owningLibrary = xsdComplex.getOwningLibrary();

            if (owningLibrary instanceof XSDLibrary) {
                navigateXSDLibrary((XSDLibrary) owningLibrary);
            }
            navigateDependency(xsdComplex.getIdentityAlias());

            for (XSDElement aliasElement : xsdComplex.getAliases()) {
                navigateDependency(aliasElement);
            }
        }
    }

    /**
     * Called when an <code>XSDElement</code> instance is encountered during model navigation.
     * 
     * @param xsdElement
     *            the XSD element entity to visit and navigate
     */
    protected void navigateXSDElement(XSDElement xsdElement) {
        if (canVisit(xsdElement) && visitor.visitXSDElement(xsdElement)) {
            AbstractLibrary owningLibrary = xsdElement.getOwningLibrary();

            if (owningLibrary instanceof XSDLibrary) {
                navigateXSDLibrary((XSDLibrary) owningLibrary);
            }
            navigateDependency(xsdElement.getAliasedType());
        }
    }

    /**
     * Called when a <code>TLExtension</code> instance is encountered during model navigation.
     * 
     * @param extension
     *            the extension entity to visit and navigate
     */
    protected void navigateExtension(TLExtension extension) {
        if (canVisit(extension) && visitor.visitExtension(extension)) {
            navigateDependency(extension.getExtendsEntity());
        }
    }

    /**
     * Called when a <code>TLFacet</code> instance is encountered during model navigation.
     * 
     * @param facet
     *            the facet entity to visit and navigate
     */
    protected void navigateFacet(TLFacet facet) {
        if (canVisit(facet) && visitor.visitFacet(facet)) {
        	navigateFacetMembers(facet);
        }
    }

    /**
     * Called when a <code>TLContextualFacet</code> instance is encountered during model navigation.
     * 
     * @param facet
     *            the facet entity to visit and navigate
     */
    protected void navigateContextualFacet(TLContextualFacet facet) {
        if (canVisit(facet) && visitor.visitContextualFacet(facet)) {
        	if (!facet.isLocalFacet()) {
        		navigate(facet.getOwningEntity());
        	}
        	navigateFacetMembers(facet);
        }
    }
    
    /**
     * Navigates all field members and aliases as well as the owner of the given facet.
     * 
     * @param facet  the facet whose members are to be navigated
     */
    private void navigateFacetMembers(TLFacet facet) {
        for (TLAlias alias : facet.getAliases()) {
            navigateAlias(alias);
        }
        for (TLAttribute attribute : PropertyCodegenUtils.getInheritedAttributes(facet)) {
            navigateAttribute(attribute);
        }
        for (TLProperty element : PropertyCodegenUtils.getInheritedProperties(facet)) {
            navigateElement(element);
        }
        for (TLIndicator indicator : PropertyCodegenUtils.getInheritedIndicators(facet)) {
            navigateIndicator(indicator);
        }
        navigateDependency(facet.getOwningEntity());
    }

    /**
     * Called when a <code>TLActionFacet</code> instance is encountered during model navigation.
     * 
     * @param actionFacet
     *            the action facet entity to visit and navigate
     */
    protected void navigateActionFacet(TLActionFacet actionFacet) {
        if (canVisit(actionFacet) && visitor.visitActionFacet(actionFacet)) {
            navigateDependency(actionFacet.getBasePayload());
            navigateDependency(actionFacet.getOwningResource());
        }
        addVisitedNode(actionFacet);
    }
    
    /**
     * Called when a <code>TLSimpleFacet</code> instance is encountered during model navigation.
     * 
     * @param simpleFacet
     *            the simple facet entity to visit and navigate
     */
    protected void navigateSimpleFacet(TLSimpleFacet simpleFacet) {
        if (canVisit(simpleFacet) && visitor.visitSimpleFacet(simpleFacet)) {
            navigateDependency(simpleFacet.getSimpleType());
            navigateDependency(simpleFacet.getOwningEntity());
        }
    }

    /**
     * Called when a <code>TLListFacet</code> instance is encountered during model navigation.
     * 
     * @param listFacet
     *            the list facet entity to visit and navigate
     */
    protected void navigateListFacet(TLListFacet listFacet) {
        if (canVisit(listFacet) && visitor.visitListFacet(listFacet)) {
            navigateDependency(listFacet.getItemFacet());
        }
    }

    /**
     * Called when a <code>TLAlias</code> instance is encountered during model navigation.
     * 
     * @param alias
     *            the alias entity to visit and navigate
     */
    protected void navigateAlias(TLAlias alias) {
        if (canVisit(alias) && visitor.visitAlias(alias)) {
            navigateDependency(alias.getOwningEntity());
        }
    }

    /**
     * Called when a <code>TLRole</code> instance is encountered during model navigation.
     * 
     * @param role
     *            the role entity to visit and navigate
     */
    protected void navigateRole(TLRole role) {
        if (canVisit(role) && visitor.visitRole(role)) {
            if (role.getRoleEnumeration() != null) {
                navigateDependency(role.getRoleEnumeration().getOwningEntity());
            }
        }
    }

    /**
     * Called when a <code>TLAttribute</code> instance is encountered during model navigation.
     * 
     * @param attribute
     *            the attribute entity to visit and navigate
     */
    protected void navigateAttribute(TLAttribute attribute) {
        if (canVisit(attribute) && visitor.visitAttribute(attribute)) {
            navigateDependency(attribute.getType());
            navigateDependency(attribute.getOwner());
        }
    }

    /**
     * Called when a <code>TLProperty</code> instance is encountered during model navigation.
     * 
     * @param element
     *            the element entity to visit and navigate
     */
    protected void navigateElement(TLProperty element) {
        if (canVisit(element) && visitor.visitElement(element)) {
            navigateDependency(element.getType());
            navigateDependency(element.getOwner());
        }
    }

    /**
     * Called when a <code>TLIndicator</code> instance is encountered during model navigation.
     * 
     * @param indicator
     *            the indicator entity to visit and navigate
     */
    protected void navigateIndicator(TLIndicator indicator) {
        if (canVisit(indicator) && visitor.visitIndicator(indicator)) {
            navigateDependency(indicator.getOwner());
        }
    }

    /**
     * Navigates the given named entity and (if necessary) any of the entities it references as
     * dependencies.
     * 
     * @param entity
     *            the entity whose dependencies to navigate
     */
    public void navigateDependency(NamedEntity entity) {
        if (entity instanceof TLSimple) {
            navigateSimple((TLSimple) entity);

        } else if (entity instanceof TLValueWithAttributes) {
            navigateValueWithAttributes((TLValueWithAttributes) entity);

        } else if (entity instanceof TLClosedEnumeration) {
            navigateClosedEnumeration((TLClosedEnumeration) entity);

        } else if (entity instanceof TLOpenEnumeration) {
            navigateOpenEnumeration((TLOpenEnumeration) entity);

        } else if (entity instanceof TLChoiceObject) {
            navigateChoiceObject((TLChoiceObject) entity);

        } else if (entity instanceof TLCoreObject) {
            navigateCoreObject((TLCoreObject) entity);

        } else if (entity instanceof TLBusinessObject) {
            navigateBusinessObject((TLBusinessObject) entity);

        } else if (entity instanceof TLResource) {
            navigateResource((TLResource) entity);

        } else if (entity instanceof TLActionFacet) {
            navigateActionFacet((TLActionFacet) entity);

        } else if (entity instanceof XSDSimpleType) {
            navigateXSDSimpleType((XSDSimpleType) entity);

        } else if (entity instanceof XSDComplexType) {
            navigateXSDComplexType((XSDComplexType) entity);

        } else if (entity instanceof XSDElement) {
            navigateXSDElement((XSDElement) entity);

        } else if (entity instanceof TLFacet) {
            navigateFacet((TLFacet) entity);

        } else if (entity instanceof TLSimpleFacet) {
            navigateSimpleFacet((TLSimpleFacet) entity);

        } else if (entity instanceof TLListFacet) {
            navigateListFacet((TLListFacet) entity);

        } else if (entity instanceof TLAlias) {
            navigateAlias((TLAlias) entity);

        } else if (entity instanceof TLService) {
            navigateService((TLService) entity);

        } else if (entity instanceof TLOperation) {
            navigateOperation((TLOperation) entity);

        } else if (entity instanceof TLExtensionPointFacet) {
            navigateExtensionPointFacet((TLExtensionPointFacet) entity);
        }
    }

}
