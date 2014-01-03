/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.loader.impl;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import com.sabre.schemacompiler.ioc.SchemaDeclaration;
import com.sabre.schemacompiler.loader.BuiltInLibraryLoader;
import com.sabre.schemacompiler.loader.LibraryInputSource;
import com.sabre.schemacompiler.loader.LibraryLoaderException;

/**
 * Base class for <code>BuiltInLibraryLoader</code> components that obtain content from
 * a file on the file system or from the local classpath.
 * 
 * @author S. Livezey
 */
public abstract class AbstractBuiltInLibraryLoader implements BuiltInLibraryLoader {
	
	private SchemaDeclaration libraryDeclaration;
	
	/**
	 * Returns an input source for the schema location that has been specified for the built-in
	 * library file.
	 * 
	 * @return LibraryInputSource<InputStream>
	 * @throws LibraryLoaderException  thrown if an error occurs while constructing the input source or
	 * 								   the library file does not exist
	 */
	protected LibraryInputSource<InputStream> getInputSource() throws LibraryLoaderException {
		LibraryInputSource<InputStream> inputSource = null;
		String libraryUrl = getLibraryUrl();
		
		if (libraryDeclaration == null) {
			throw new LibraryLoaderException("No library declaration assigned for the built-in library loader.");
		}
		try {
			inputSource = new LibraryStreamInputSource(new URL(libraryUrl), libraryDeclaration);
			
		} catch (MalformedURLException e) {
			throw new LibraryLoaderException("Invalid library URL: " + libraryUrl +
					" (the namespace and/or library name needs to be modified).");
		} catch (Throwable t) {
			throw new LibraryLoaderException("Unknown error loading built-in library: " + libraryDeclaration.getName(), t);
		}
		return inputSource;
	}
	
	/**
	 * Returns the library URL as a concatenation of the namespace and name for the assigned
	 * schema declaration.
	 * 
	 * @return String
	 */
	private String getLibraryUrl() {
		StringBuilder libraryUrl = new StringBuilder();
		
		if (libraryDeclaration != null) {
			if (libraryDeclaration.getNamespace() != null) {
				libraryUrl.append(libraryDeclaration.getNamespace());
			}
			if (!libraryUrl.toString().endsWith("/")) {
				libraryUrl.append("/");
			}
			String location = libraryDeclaration.getLocation().replaceAll("\\\\", "/");
			int pathPos = location.lastIndexOf('/');
			String filename;
			
			if (pathPos >= 0) {
				if ((pathPos + 1) < location.length()) {
					filename = location.substring(pathPos + 1);
				} else {
					filename = ""; // no filename - path ends with a '/'
				}
			} else {
				filename = location;
			}
			libraryUrl.append(filename);
		}
		return libraryUrl.toString();
	}

	/**
	 * Returns the declaration for the built-in library to be loaded.
	 *
	 * @return SchemaDeclaration
	 */
	public SchemaDeclaration getLibraryDeclaration() {
		return libraryDeclaration;
	}

	/**
	 * Assigns the declaration for the built-in library to be loaded.
	 *
	 * @param libraryDeclaration  the declaration to assign
	 */
	public void setLibraryDeclaration(SchemaDeclaration libraryDeclaration) {
		this.libraryDeclaration = libraryDeclaration;
	}
	
}