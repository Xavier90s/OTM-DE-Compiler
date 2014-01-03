/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.validate.compile;

import com.sabre.schemacompiler.model.TLLibrary;
import com.sabre.schemacompiler.model.TLService;
import com.sabre.schemacompiler.validate.FindingType;
import com.sabre.schemacompiler.validate.ValidationFindings;
import com.sabre.schemacompiler.validate.base.TLServiceBaseValidator;
import com.sabre.schemacompiler.validate.impl.TLValidationBuilder;
import com.sabre.schemacompiler.version.MinorVersionHelper;
import com.sabre.schemacompiler.version.VersionScheme;
import com.sabre.schemacompiler.version.VersionSchemeException;

/**
 * Validator for the <code>TLService</code> class.
 * 
 * @author S. Livezey
 */
public class TLServiceCompileValidator extends TLServiceBaseValidator {

	public static final String ERROR_ILLEGAL_SERVICE_VERSION = "ILLEGAL_SERVICE_VERSION";
	public static final String ERROR_ILLEGAL_PATCH           = "ILLEGAL_PATCH";
	
	/**
	 * @see com.sabre.schemacompiler.validate.impl.TLValidatorBase#validateFields(com.sabre.schemacompiler.validate.Validatable)
	 */
	@Override
	protected ValidationFindings validateFields(TLService target) {
		TLValidationBuilder builder = newValidationBuilder(target);
		
		builder.setProperty("name", target.getName()).setFindingType(FindingType.ERROR)
			.assertNotNullOrBlank()
			.assertPatternMatch(NAME_XML_PATTERN);

		builder.setProperty("equivalents", target.getEquivalents()).setFindingType(FindingType.ERROR)
			.assertNotNull()
			.assertContainsNoNullElements();
		
		builder.setProperty("operations", target.getOperations()).setFindingType(FindingType.ERROR)
			.assertMinimumSize(1);
		
		// Validate versioning rules
		try {
			if ((target.getOwningLibrary() instanceof TLLibrary) && (target.getName() != null)) {
				TLLibrary owningLibrary = (TLLibrary) target.getOwningLibrary();
				MinorVersionHelper helper = new MinorVersionHelper();
				VersionScheme vScheme = helper.getVersionScheme( owningLibrary );
				
				if ((vScheme != null) && vScheme.isPatchVersion(owningLibrary.getNamespace())) {
					builder.addFinding(FindingType.ERROR, "name", ERROR_ILLEGAL_PATCH);
					
				} else {
					TLLibrary previousLibraryVersion = helper.getPriorMinorVersion( owningLibrary );
					boolean hasError = false;
					
					while (!hasError && (previousLibraryVersion != null)) {
						TLService previousServiceVersion = previousLibraryVersion.getService();
						
						hasError = (previousServiceVersion != null) && !target.getName().equals( previousServiceVersion.getName() );
						previousLibraryVersion = helper.getPriorMinorVersion( previousLibraryVersion );
					}
					if (hasError) {
						builder.addFinding(FindingType.ERROR, "name", ERROR_ILLEGAL_SERVICE_VERSION, target.getName());
					}
				}
			}
		} catch (VersionSchemeException e) {
			// Ignore - Invalid version scheme error will be reported when the owning library is validated
		}
		
		return builder.getFindings();
	}

}