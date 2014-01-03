/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.transform.jaxb14_2tl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import org.opentravel.ns.ota2.librarymodel_v01_04.ContextDeclaration;
import org.opentravel.ns.ota2.librarymodel_v01_04.Library;
import org.opentravel.ns.ota2.librarymodel_v01_04.LibraryStatus;
import org.opentravel.ns.ota2.librarymodel_v01_04.NamespaceImport;
import org.opentravel.ns.ota2.librarymodel_v01_04.Service;

import com.sabre.schemacompiler.model.LibraryMember;
import com.sabre.schemacompiler.model.TLContext;
import com.sabre.schemacompiler.model.TLInclude;
import com.sabre.schemacompiler.model.TLLibrary;
import com.sabre.schemacompiler.model.TLLibraryStatus;
import com.sabre.schemacompiler.model.TLService;
import com.sabre.schemacompiler.transform.ObjectTransformer;
import com.sabre.schemacompiler.transform.symbols.DefaultTransformerContext;
import com.sabre.schemacompiler.transform.util.BaseTransformer;

/**
 * Handles the transformation of objects from the <code>Library</code> type to the
 * <code>TLLibrary</code> type.
 * 
 * @author S. Livezey
 */
public class LibraryTransformer extends BaseTransformer<Library,TLLibrary,DefaultTransformerContext> {
	
	public static final String DEFAULT_CONTEXT_ID = "default";
	
	/**
	 * @see com.sabre.schemacompiler.transform.ObjectTransformer#transform(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public TLLibrary transform(Library source) {
		ObjectTransformer<ContextDeclaration,TLContext,DefaultTransformerContext> contextTransformer =
				getTransformerFactory().getTransformer(ContextDeclaration.class, TLContext.class);
		String credentialsUrl = trimString(source.getAlternateCredentials());
		TLLibrary target = new TLLibrary();
		
		target.setName( trimString(source.getName()) );
		target.setVersionScheme( trimString(source.getVersionScheme()) );
		target.setNamespace( getAdjustedNamespaceURI(trimString(source.getNamespace()),
				trimString(source.getPatchLevel()), target.getVersionScheme()) );
		target.setPreviousVersionUri( trimString(source.getPreviousVersionLocation()) );
		target.setStatus( transformStatus(source.getStatus()) );
		target.setPrefix( trimString(source.getPrefix()) );
		target.setComments( trimString(source.getComments()) );
		
		if (credentialsUrl != null) {
			try {
				target.setAlternateCredentialsUrl( new URL(credentialsUrl) );
				
			} catch (MalformedURLException e) {
				// Ignore exception - no credentials URL will be assigned
			}
		}
		
		for (String _include : trimStrings(source.getIncludes())) {
			TLInclude include = new TLInclude();
			
			include.setPath(_include);
			target.addInclude(include);
		}
		
		for (NamespaceImport nsImport : source.getImport()) {
			String[] fileHints = null;
			
			if ((nsImport.getFileHints() != null) && (nsImport.getFileHints().trim().length() > 0)) {
				fileHints = nsImport.getFileHints().split("\\s+");
			}
			target.addNamespaceImport( trimString(nsImport.getPrefix()),
					trimString(nsImport.getNamespace()), fileHints);
		}
		
		for (ContextDeclaration sourceContext : source.getContext()) {
			target.addContext( contextTransformer.transform(sourceContext) );
		}
		
		// Perform transforms for all library members
		for (Object sourceMember : source.getTerms()) {
			Set<Class<?>> targetTypes = getTransformerFactory().findTargetTypes(sourceMember);
			Class<LibraryMember> targetType = (Class<LibraryMember>)
					((targetTypes.size() == 0) ? null : targetTypes.iterator().next());
			
			if (targetType != null) {
				ObjectTransformer<Object,LibraryMember,DefaultTransformerContext> memberTransformer =
						getTransformerFactory().getTransformer(sourceMember, targetType);
				
				if (memberTransformer != null) {
					target.addNamedMember( memberTransformer.transform(sourceMember) );
				}
			}
		}
		if (source.getService() != null) {
			ObjectTransformer<Service,TLService,DefaultTransformerContext> serviceTransformer =
					getTransformerFactory().getTransformer(Service.class, TLService.class);
			
			target.setService( serviceTransformer.transform(source.getService()) );
		}
		
		return target;
	}
	
	/**
	 * Converts the JAXB status enumeration value into its equivalent value for the TL model.
	 * 
	 * @param jaxbStatus  the JAXB status enumeration value
	 * @return TLLibraryStatus
	 */
	private TLLibraryStatus transformStatus(LibraryStatus jaxbStatus) {
		TLLibraryStatus tlStatus;
		
		// Default value is DRAFT in the case of a null
		if (jaxbStatus == null) {
			jaxbStatus = LibraryStatus.DRAFT;
		}
		
		switch (jaxbStatus) {
			case FINAL:
				tlStatus = TLLibraryStatus.FINAL;
				break;
				
			case DRAFT:
			default:
				tlStatus = TLLibraryStatus.DRAFT;
		}
		return tlStatus;
	}
	
}