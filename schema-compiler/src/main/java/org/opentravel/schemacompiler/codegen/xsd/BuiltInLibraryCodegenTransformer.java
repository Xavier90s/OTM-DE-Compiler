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

import java.io.File;

import org.opentravel.schemacompiler.codegen.CodeGenerationContext;
import org.opentravel.schemacompiler.codegen.CodeGenerationFilenameBuilder;
import org.opentravel.schemacompiler.codegen.CodeGenerationFilter;
import org.opentravel.schemacompiler.codegen.impl.CodeGenerationTransformerContext;
import org.opentravel.schemacompiler.codegen.impl.CodegenArtifacts;
import org.opentravel.schemacompiler.codegen.impl.LibraryFilterBuilder;
import org.opentravel.schemacompiler.codegen.util.XsdCodegenUtils;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.BuiltInLibrary;
import org.opentravel.schemacompiler.model.NamedEntity;
import org.opentravel.schemacompiler.transform.ObjectTransformer;
import org.w3._2001.xmlschema.Annotation;
import org.w3._2001.xmlschema.OpenAttrs;
import org.w3._2001.xmlschema.Schema;

/**
 * Performs the translation from <code>BuiltInLibrary</code> objects to the JAXB nodes used to
 * produce the schema output.
 * 
 * @author S. Livezey
 */
public class BuiltInLibraryCodegenTransformer extends
        AbstractXsdTransformer<BuiltInLibrary, Schema> {

    /**
     * @see org.opentravel.schemacompiler.transform.ObjectTransformer#transform(java.lang.Object)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Schema transform(BuiltInLibrary source) {
        CodeGenerationFilter filter = context.getCodeGenerator().getFilter();
        Schema schema = createSchema(source.getNamespace(), null);

        // Add the application info for this library
        Annotation schemaAnnotation = new Annotation();

        schemaAnnotation.getAppinfoOrDocumentation().add(
                XsdCodegenUtils.getAppInfo(source, context.getCodegenContext()));
        schema.getIncludeOrImportOrRedefine().add(schemaAnnotation);

        // Add entries for each non-service term declaration
        for (NamedEntity member : source.getNamedMembers()) {
            ObjectTransformer<NamedEntity, CodegenArtifacts, CodeGenerationTransformerContext> transformer =
            		getTransformerFactory().getTransformer(member, CodegenArtifacts.class);

            if ((transformer != null) && ((filter == null) || filter.processEntity(member))) {
                CodegenArtifacts artifacts = transformer.transform(member);

                if (artifacts != null) {
                    for (OpenAttrs artifact : artifacts.getArtifactsOfType(OpenAttrs.class)) {
                        schema.getSimpleTypeOrComplexTypeOrGroup().add(artifact);
                    }
                }
            }
        }

        // Add entries for all imports and includes
        CodeGenerationFilenameBuilder<AbstractLibrary> filenameBuilder = (CodeGenerationFilenameBuilder<AbstractLibrary>) context
                .getCodeGenerator().getFilenameBuilder();
        CodeGenerationFilter libraryFilter = new LibraryFilterBuilder(source).setGlobalFilter(
                context.getCodeGenerator().getFilter()).buildFilter();

        addImports(schema, source, filenameBuilder, libraryFilter);
        addIncludes(schema, source, filenameBuilder, libraryFilter);

        return schema;
    }

    /**
     * @see org.opentravel.schemacompiler.codegen.impl.AbstractCodegenTransformer#getBuiltInSchemaOutputLocation()
     */
    @Override
    protected String getBuiltInSchemaOutputLocation() {
        // Since we are generating a built-in schema, all imports should be located in the local
        // directory
        // instead of the '/built-ins' sub-folder
        return "";
    }

    /**
     * @see org.opentravel.schemacompiler.codegen.xsd.AbstractXsdTransformer#getBaseOutputFolder()
     */
    @Override
    protected File getBaseOutputFolder() {
        CodeGenerationContext cgContext = context.getCodegenContext();
        return new File(XsdCodegenUtils.getBaseOutputFolder(cgContext),
                XsdCodegenUtils.getBuiltInSchemaOutputLocation(cgContext));
    }

}
