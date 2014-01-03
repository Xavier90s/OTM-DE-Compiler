/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.ic;

import com.sabre.schemacompiler.event.ModelEventType;
import com.sabre.schemacompiler.event.ValueChangeEvent;
import com.sabre.schemacompiler.model.TLLibrary;
import com.sabre.schemacompiler.model.TLModel;

/**
 * Integrity checker component that automatically manages the list of imports and includes maintained
 * by a <code>TLLibrary</code> instance.  It is invoked whenever the namespace assignment of a library
 * is modified, taking action to add or remove imports/includes as required.
 * 
 * @author S. Livezey
 */
public class NamespaceChangeIntegrityChecker extends ImportManagementIntegrityChecker<ValueChangeEvent<TLLibrary,String>,TLLibrary> {

	/**
	 * @see com.sabre.schemacompiler.event.ModelEventListener#processModelEvent(com.sabre.schemacompiler.event.ModelEvent)
	 */
	@Override
	public void processModelEvent(ValueChangeEvent<TLLibrary, String> event) {
		if (event.getType() == ModelEventType.NAMESPACE_MODIFIED) {
			TLLibrary modifiedLibrary = event.getSource();
			TLModel model = modifiedLibrary.getOwningModel();
			
			for (TLLibrary library : model.getUserDefinedLibraries()) {
				// We need to check all libraries, including the one whose namespace was modified.  This is
				// because a namespace change can force some includes to change into imports (and vice-versa).
				verifyReferencedLibraries((TLLibrary) library);
			}
		}
	}

	/**
	 * @see com.sabre.schemacompiler.event.ModelEventListener#getEventClass()
	 */
	@Override
	public Class<?> getEventClass() {
		return ValueChangeEvent.class;
	}

	/**
	 * @see com.sabre.schemacompiler.event.ModelEventListener#getSourceObjectClass()
	 */
	@Override
	public Class<TLLibrary> getSourceObjectClass() {
		return TLLibrary.class;
	}
	
}