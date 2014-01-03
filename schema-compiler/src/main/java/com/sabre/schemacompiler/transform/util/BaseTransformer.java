/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.transform.util;

import java.util.ArrayList;
import java.util.List;

import com.sabre.schemacompiler.transform.ObjectTransformer;
import com.sabre.schemacompiler.transform.ObjectTransformerContext;
import com.sabre.schemacompiler.transform.TransformerFactory;
import com.sabre.schemacompiler.version.VersionScheme;
import com.sabre.schemacompiler.version.VersionSchemeException;
import com.sabre.schemacompiler.version.VersionSchemeFactory;

/**
 * Base transformer used by all classes that handle object transformations.
 * 
 * @param <S>  the source type of the object transformation
 * @param <T>  the target type of the object transformation
 * @param <C>  the type of context required by the transformer instance
 * @author S. Livezey
 */
public abstract class BaseTransformer<S,T,C extends ObjectTransformerContext> implements ObjectTransformer<S,T,C> {
	
	protected static final String UNLIMITED_TOKEN = "*";
	
	protected C context;
	
	/**
	 * @see com.sabre.schemacompiler.transform.ObjectTransformer#setContext(com.sabre.schemacompiler.transform.ObjectTransformerContext)
	 */
	@Override
	public void setContext(C context) {
		this.context = context;
	}
	
	@SuppressWarnings("unchecked")
	protected TransformerFactory<C> getTransformerFactory() {
		return (context == null) ? null : ((TransformerFactory<C>) context.getTransformerFactory());
	}

	/**
	 * If the string's value is non-null, this method trims any leading/trailing white
	 * space and returns the resulting value.  If the resulting string is zero-length, this
	 * method will return null.
	 * 
	 * @param str  the string value to trim
	 * @return String
	 */
	protected String trimString(String str) {
		return trimString(str, true);
	}
	
	/**
	 * If the string's value is non-null, this method trims any leading/trailing white
	 * space and returns the resulting value.  If the resulting string is zero-length and
	 * the 'convertEmptyStringToNull' parameter is true, this method will return null.
	 * 
	 * @param str  the string value to trim
	 * @param convertEmptyStringToNull  indicates that an empty string should be converted to a null value
	 * @return String
	 */
	protected String trimString(String str, boolean convertEmptyStringToNull) {
		String result = (str == null) ? "" : stripInvalidXMLCharacters( str.trim() );
		
		if (convertEmptyStringToNull && (result.length() == 0)) {
			result = null;
		}
		return result;
	}
	
	/**
	 * Trims each string value in the list provided and returns a list of the updated values.
	 * Any empty or null strings from the given list are omitted from the resulting one.
	 * 
	 * @param strList  the list of strings to process
	 * @return List<String>
	 */
	protected List<String> trimStrings(List<String> strList) {
		List<String> result = new ArrayList<String>();
		
		if (strList != null) {
			for (String str : strList) {
				String trimmedStr = trimString(str, true);
				
				if (trimmedStr != null) {
					result.add(trimmedStr);
				}
			}
		}
		return result;
	}
	
	/**
	 * Concatenates a list of strings into a single string.  The original string values are
	 * separated by new-line characters.
	 * 
	 * @param strList  the list of string values to concatenate
	 * @return String
	 */
	protected String consolidateStrings(List<String> strList) {
		String result = null;
		
		if ((strList != null) && (strList.size() > 0)) {
			StringBuilder strBuilder = new StringBuilder();
			
			for (String str : strList) {
				if (strBuilder.length() > 0) strBuilder.append('\n');
				if (str != null) strBuilder.append(str);
			}
			result = strBuilder.toString();
		}
		return result;
	}
	
	/**
	 * In situations where a 'patchLevel' non-null/non-zero value is defined for a library, the patch
	 * level will be incorporated into the namespace URI.  If the patch level value is null or zero, this
	 * method will return the original namespace.
	 * 
	 * @param namespace  the namespace URI to adjust
	 * @param patchLevel  the patch level of the library version identifier
	 * @param versionScheme  the version scheme identifier to use for the namespace modification
	 * @return String
	 */
	protected String getAdjustedNamespaceURI(String namespace, String patchLevel, String versionScheme) {
		String adjustedNamespace = namespace;
		
		if ((patchLevel != null) && !patchLevel.trim().equals("0")) {
			try {
				VersionScheme vScheme = VersionSchemeFactory.getInstance().getVersionScheme(versionScheme);
				String version = vScheme.getVersionIdentifier(namespace);
				
				version = vScheme.getVersionIdentifier( vScheme.getMajorVersion(version),
						vScheme.getMinorVersion(version), patchLevel);
				adjustedNamespace = vScheme.setVersionIdentifier(namespace, version);
				
			} catch (VersionSchemeException e) {
				// Ignore error - just return the original namespace value
			} catch (IllegalArgumentException e) {
				// Ignore error - just return the original namespace value
			}
		}
		return adjustedNamespace;
	}
	
	/**
	 * Removes invalid XML characters from the given string.
	 * 
	 * @param str  the string value to process
	 * @return String
	 */
	private String stripInvalidXMLCharacters(String str) {
		StringBuffer result = new StringBuffer();
		
		for (char ch : str.toCharArray()) {
			if ((ch == 0x9) || (ch == 0xA) || (ch == 0xD)
					|| ((ch >= 0x20) && (ch <= 0xD7FF))
					|| ((ch >= 0xE000) && (ch <= 0xFFFD))
					|| ((ch >= 0x10000) && (ch <= 0x10FFFF))) {
				result.append(ch);
			}
		}
		return result.toString();
	}
	
}