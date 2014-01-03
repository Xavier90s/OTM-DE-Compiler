/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.codegen.wsdl;

import javax.xml.bind.JAXBElement;

import org.xmlsoap.schemas.wsdl.TDefinitions;
import org.xmlsoap.schemas.wsdl.TDocumentation;
import org.xmlsoap.schemas.wsdl.TMessage;
import org.xmlsoap.schemas.wsdl.TOperation;
import org.xmlsoap.schemas.wsdl.TPortType;

import com.sabre.schemacompiler.codegen.CodeGenerationContext;
import com.sabre.schemacompiler.codegen.CodeGenerationFilenameBuilder;
import com.sabre.schemacompiler.codegen.impl.CodeGenerationTransformerContext;
import com.sabre.schemacompiler.codegen.impl.CodegenArtifacts;
import com.sabre.schemacompiler.codegen.impl.LibraryMemberTrimmedFilenameBuilder;
import com.sabre.schemacompiler.codegen.impl.LibraryTrimmedFilenameBuilder;
import com.sabre.schemacompiler.model.AbstractLibrary;
import com.sabre.schemacompiler.model.LibraryMember;
import com.sabre.schemacompiler.model.TLDocumentation;
import com.sabre.schemacompiler.model.TLOperation;
import com.sabre.schemacompiler.model.TLService;
import com.sabre.schemacompiler.transform.ObjectTransformer;

/**
 * Performs the translation from <code>TLService</code> objects to the JAXB nodes used
 * to produce the WSDL output.
 * 
 * @author S. Livezey
 */
public class TLServiceCodegenTransformer extends AbstractWsdlTransformer<TLService,JAXBElement<?>> {

	/**
	 * @see com.sabre.schemacompiler.transform.ObjectTransformer#transform(java.lang.Object)
	 */
	@Override
	public JAXBElement<?> transform(TLService source) {
		ObjectTransformer<TLOperation,CodegenArtifacts,CodeGenerationTransformerContext> opTransformer =
				getTransformerFactory().getTransformer(TLOperation.class, CodegenArtifacts.class);
		CodeGenerationFilenameBuilder<LibraryMember> filenameBuilder;
		CodeGenerationContext cgContext = context.getCodegenContext();
		CodegenArtifacts operationArtifacts = new CodegenArtifacts();
		TDefinitions definitions = new TDefinitions();
		
		if (cgContext.getValue(CodeGenerationContext.CK_PROJECT_FILENAME) != null) {
			filenameBuilder = new ProjectLibraryMemberTrimmedFilenameBuilder();
		} else {
			filenameBuilder = new LibraryMemberTrimmedFilenameBuilder<LibraryMember>(source);
		}
		
		definitions.setName(source.getName());
		definitions.setTargetNamespace( getTargetNamespace(source) );
		definitions.getAnyTopLevelOptionalElement().add( createTypes(source, filenameBuilder) );
		
		// Collect the artifacts for the operations of this service, and insert them into the
		// appropriate part of the WSDL document
		String portTypeName = source.getName() + "PortType";
		TPortType portType = new TPortType();
		
		for (TLOperation operation : getInheritedOperations(source)) {
			operationArtifacts.addAllArtifacts( opTransformer.transform(operation) );
		}
		for (TMessage message : operationArtifacts.getArtifactsOfType(TMessage.class)) {
			definitions.getAnyTopLevelOptionalElement().add(message);
		}
		for (TOperation operation : operationArtifacts.getArtifactsOfType(TOperation.class)) {
			portType.getOperation().add(operation);
		}
		if (source.getDocumentation() != null) {
			ObjectTransformer<TLDocumentation,TDocumentation,CodeGenerationTransformerContext> docTransformer =
					getTransformerFactory().getTransformer(TLDocumentation.class, TDocumentation.class);
			
			portType.setDocumentation( docTransformer.transform(source.getDocumentation()) );
		}
		portType.setName(portTypeName);
		definitions.getAnyTopLevelOptionalElement().add(portType);
		
		// Create the binding and service definition for the WSDL document
		addBindingAndService(definitions, portType, operationArtifacts.getArtifactsOfType(TMessage.class),
				context.getCodegenContext());
		
		return wsdlObjectFactory.createDefinitions(definitions);
	}
	
	/**
	 * Handles the generation of library filenames for WSDL imports.
	 */
	private class ProjectLibraryMemberTrimmedFilenameBuilder  implements CodeGenerationFilenameBuilder<LibraryMember> {
		
		private CodeGenerationFilenameBuilder<AbstractLibrary> delegateFilenameBuilder;
		
		/**
		 * Default constructor.
		 */
		public ProjectLibraryMemberTrimmedFilenameBuilder() {
			delegateFilenameBuilder = new LibraryTrimmedFilenameBuilder( null );
		}

		/**
		 * @see com.sabre.schemacompiler.codegen.CodeGenerationFilenameBuilder#buildFilename(java.lang.Object, java.lang.String)
		 */
		@Override
		public String buildFilename(LibraryMember item, String fileExtension) {
			return delegateFilenameBuilder.buildFilename(item.getOwningLibrary(), fileExtension);
		}
		
	}
	
}