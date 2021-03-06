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
package org.opentravel.schemacompiler.transform.library_01_05;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.InputStream;

import org.junit.Test;
import org.opentravel.schemacompiler.loader.LibraryModelLoader;
import org.opentravel.schemacompiler.loader.impl.LibraryStreamInputSource;
import org.opentravel.schemacompiler.model.LibraryMember;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLModel;
import org.opentravel.schemacompiler.model.TLSimple;
import org.opentravel.schemacompiler.validate.FindingMessageFormat;
import org.opentravel.schemacompiler.validate.ValidationFindings;

/**
 * Verifies that circular dependencies will not "crash" the loader or transformer frameworks.
 * 
 * @author S. Livezey
 */
public class TestCircularDependencies extends Abstract_1_5_TestTransformers {

    private static final boolean DEBUG = false;

    @Test
    public void testDirectCircularDependency() throws Exception {
        File libraryFile = new File(getBaseLocation()
                + "/test-package_v1/circular-reference-library.xml");
        LibraryModelLoader<InputStream> modelLoader = new LibraryModelLoader<InputStream>();
        ValidationFindings findings = modelLoader.loadLibraryModel(new LibraryStreamInputSource(
                libraryFile));

        if (DEBUG)
            displayFindings(findings);

        TLLibrary library = getTestLibrary(modelLoader.getLibraryModel());
        TLSimple indirectCircularReferenceType1 = (TLSimple) getMember(library,
                "IndirectCircularReferenceType_1");
        TLSimple indirectCircularReferenceType2 = (TLSimple) getMember(library,
                "IndirectCircularReferenceType_2");
        TLSimple directCircularReferenceType = (TLSimple) getMember(library,
                "DirectCircularReferenceType");

        assertNotNull(directCircularReferenceType);
        assertNotNull(directCircularReferenceType.getParentType());
        assertEquals(directCircularReferenceType, directCircularReferenceType.getParentType());
        assertEquals("DirectCircularReferenceType", directCircularReferenceType.getParentTypeName());

        assertNotNull(indirectCircularReferenceType1);
        assertNotNull(indirectCircularReferenceType2);
        assertEquals(indirectCircularReferenceType2, indirectCircularReferenceType1.getParentType());
        assertEquals("IndirectCircularReferenceType_2",
                indirectCircularReferenceType1.getParentTypeName());
        assertNotNull(indirectCircularReferenceType2.getParentType());
        assertEquals(indirectCircularReferenceType1, indirectCircularReferenceType2.getParentType());
        assertEquals("IndirectCircularReferenceType_1",
                indirectCircularReferenceType2.getParentTypeName());
    }

    private void displayFindings(ValidationFindings findings) {
        if (findings.hasFinding()) {
            System.out.println("Errors/Warnings:");

            for (String message : findings.getAllValidationMessages(FindingMessageFormat.DEFAULT)) {
                System.out.println("  " + message);
            }
        }
    }

    private TLLibrary getTestLibrary(TLModel model) {
        for (TLLibrary lib : model.getUserDefinedLibraries()) {
        	if (lib.getName().equals("library_1")) {
        		return lib;
        	}
        }
        return null;
    }

    private LibraryMember getMember(TLLibrary library, String name) {
        for (LibraryMember entity : library.getNamedMembers()) {
            if (entity.getLocalName().equals(name)) {
                return entity;
            }
        }
        return null;
    }

}
