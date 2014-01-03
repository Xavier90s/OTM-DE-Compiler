/*
 * Copyright (c) 2013, Sabre Corporation and affiliates.
 * All Rights Reserved.
 * Use is subject to license agreement.
 */
package com.sabre.schemacompiler.extension;

import java.io.InputStream;
import java.util.Collection;

import org.springframework.beans.BeansException;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Interface to be implemented by components that wish to contribute extensions to the
 * OTA2.0 compiler.  Implementations of this class should follow the SPI pattern by providing
 * a file named 'com.sabre.schemacompiler.ioc.CompilerExtensionProvider' in the '/META-INF/services'
 * directory of its jar file.
 * 
 * @author S. Livezey
 */
public interface CompilerExtensionProvider {
	
	/**
	 * Returns the compiler extensions provided by the classpath component.
	 * 
	 * @return Collection<CompilerExtension>
	 */
	public Collection<CompilerExtension> getCompilerExtensions();
	
	/**
	 * Returns true if the given extension ID is supported by this provider.
	 * 
	 * @param extensionId  the unique ID of the extension to check
	 * @return boolean
	 */
	public boolean isSupportedExtension(String extensionId);
	
	/**
	 * Loads the extension with the given ID into the Spring application context provided.
	 * 
	 * @param context  the context into which the compiler extension beans should be loaded
	 * @param extensionId  the unique ID of the extension to load
	 * @throws BeansException  thrown if the application context configuration file(s) cannot be loaded
	 */
	public void loadCompilerExtension(GenericApplicationContext context, String extensionId) throws BeansException;
	
	/**
	 * Loads any compiler extensions that are not associated with a specific extension ID.
	 * These extensions will be loaded into the compiler's application context regardless
	 * of which extension is selected, so care should be taken not to define any beans that
	 * will conflict with existing bean definitions.
	 * 
	 * @param context  the context into which the compiler extension beans should be loaded
	 * @throws BeansException
	 */
	public void loadGeneralCompilerExtensions(GenericApplicationContext context) throws BeansException;
	
	/**
	 * Returns an input stream to the specified classpath resource.  This method is similar to
	 * 'class.getResourceAsStream(...)' except that the resource lookup is delegated to the extension
	 * provider's codebase.  This is important in OSGi environments since resources local to an
	 * extension's jar file are not typically visible to external bundles.
	 * 
	 * <p>If the requested resource cannot be loaded by the provider, this method will return
	 * null.
	 * 
	 * @param resourcePath  the classpath location of the resource to load
	 * @return InputStream
	 */
	public InputStream getExtensionResource(String resourcePath);
	
}