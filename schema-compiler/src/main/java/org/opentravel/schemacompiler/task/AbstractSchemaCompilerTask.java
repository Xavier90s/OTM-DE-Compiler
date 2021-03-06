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
package org.opentravel.schemacompiler.task;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;

import org.opentravel.schemacompiler.codegen.CodeGenerationContext;
import org.opentravel.schemacompiler.codegen.CodeGenerationFilenameBuilder;
import org.opentravel.schemacompiler.codegen.CodeGenerationFilter;
import org.opentravel.schemacompiler.codegen.CodeGenerator;
import org.opentravel.schemacompiler.codegen.CodeGeneratorFactory;
import org.opentravel.schemacompiler.codegen.impl.AbstractJaxbCodeGenerator;
import org.opentravel.schemacompiler.codegen.impl.DependencyFilterBuilder;
import org.opentravel.schemacompiler.codegen.impl.LegacySchemaExtensionFilenameBuilder;
import org.opentravel.schemacompiler.codegen.impl.LibraryFilenameBuilder;
import org.opentravel.schemacompiler.codegen.json.AbstractJsonSchemaCodeGenerator;
import org.opentravel.schemacompiler.codegen.util.XsdCodegenUtils;
import org.opentravel.schemacompiler.codegen.xsd.AbstractXsdCodeGenerator;
import org.opentravel.schemacompiler.codegen.xsd.ImportSchemaLocations;
import org.opentravel.schemacompiler.codegen.xsd.XsdBuiltInCodeGenerator;
import org.opentravel.schemacompiler.ioc.SchemaCompilerApplicationContext;
import org.opentravel.schemacompiler.ioc.SchemaDeclaration;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.BuiltInLibrary;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLModel;
import org.opentravel.schemacompiler.model.XSDLibrary;
import org.opentravel.schemacompiler.repository.RepositoryManager;
import org.opentravel.schemacompiler.util.SchemaCompilerException;
import org.springframework.context.ApplicationContext;

/**
 * Extends the base class by adding support for XML and JSON schemas, as well as EXAMPLE
 * output generation.
 * 
 * @author S. Livezey
 */
public abstract class AbstractSchemaCompilerTask extends AbstractCompilerTask implements
        SchemaCompilerTaskOptions {

    private boolean suppressOtmExtensions = false;
    private boolean generateExamples = true;
    private boolean generateMaxDetailsForExamples = true;
    private String exampleContext;
    private Integer exampleMaxRepeat;
    private Integer exampleMaxDepth;
    private boolean suppressOptionalFields = false;

    /**
     * Default constructor.
     */
    public AbstractSchemaCompilerTask() {}
    
    /**
     * Constructor that assigns the repository manager for this task instance.
     * 
     * @param repositoryManager  the repository manager to use when retrieving managed content
     */
    public AbstractSchemaCompilerTask(RepositoryManager repositoryManager) {
    	super( repositoryManager );
    }

    /**
     * Compiles the XML schema files for the given model using the context, filename builder, and
     * code generation filter provided.
     * 
     * @param userDefinedLibraries
     *            the list of user-defined libraries for which to compile XML schema artifacts
     * @param legacySchemas
     *            the list of legacy schemas (xsd files) for which to compile XML schema artifacts
     * @param context
     *            the code generation context to use for code generation
     * @param filenameBuilder
     *            the filename builder to assign to the code generator(s) used by this method
     * @param filter
     *            the filter used to identify specific artifacts for which schema generation is
     *            required
     * @throws SchemaCompilerException
     */
    @SuppressWarnings("unchecked")
    protected void compileXmlSchemas(Collection<TLLibrary> userDefinedLibraries,
            Collection<XSDLibrary> legacySchemas, CodeGenerationContext context,
            CodeGenerationFilenameBuilder<?> filenameBuilder, CodeGenerationFilter filter)
            throws SchemaCompilerException {

        // Generate output for all user-defined libraries
        TLModel model = getModel(userDefinedLibraries, legacySchemas);

        if (model == null) {
            throw new SchemaCompilerException(
                    "No libraries or legacy schemas found for code generation task.");
        }
        ImportSchemaLocations importLocations = analyzeImportDependencies(model, context,
                filenameBuilder, filter);

        for (TLLibrary library : userDefinedLibraries) {
            CodeGenerator<TLLibrary> xsdGenerator = newCodeGenerator(
                    CodeGeneratorFactory.XSD_TARGET_FORMAT, TLLibrary.class,
                    (CodeGenerationFilenameBuilder<TLLibrary>) filenameBuilder, filter);

            if (xsdGenerator instanceof AbstractXsdCodeGenerator) {
                ((AbstractXsdCodeGenerator<?>) xsdGenerator)
                        .setImportSchemaLocations(importLocations);
            }
            addGeneratedFiles(xsdGenerator.generateOutput(library, context));

            // If any non-xsd built-in dependencies were identified, add them to the current filter
            if ((filter != null) && (xsdGenerator instanceof AbstractJaxbCodeGenerator)) {
                AbstractJaxbCodeGenerator<?> generator = (AbstractJaxbCodeGenerator<?>) xsdGenerator;

                for (SchemaDeclaration schemaDeclaration : generator.getCompileTimeDependencies()) {
                    if (schemaDeclaration.getFilename(CodeGeneratorFactory.XSD_TARGET_FORMAT)
                    		.endsWith(".xsd")) {
                        continue;
                    }
                    AbstractLibrary dependentLib = model.getLibrary(
                            schemaDeclaration.getNamespace(), schemaDeclaration.getName());

                    if (dependentLib instanceof BuiltInLibrary) {
                        filter.addBuiltInLibrary((BuiltInLibrary) dependentLib);
                    }
                }
            }
        }

        // Generate output for all legacy XML schema libraries

        // If a filter was not passed to this method create one that will identify the legacy
        // schemas (and schema extensions) are really needed in the output folder.
        CodeGenerationFilter legacySchemaFilter = filter;

        if (legacySchemaFilter == null) {
            DependencyFilterBuilder filterBuilder = new DependencyFilterBuilder()
                    .setIncludeExtendedLegacySchemas(true);

            for (TLLibrary library : userDefinedLibraries) {
                filterBuilder.addLibrary(library);
            }
            for (XSDLibrary library : legacySchemas) {
                filterBuilder.addLibrary(library);
            }
            legacySchemaFilter = filterBuilder.buildFilter();
        }

        // Copy the required legacy schemas to the output folder
        CodeGenerator<XSDLibrary> legacyGenerator = newCodeGenerator(
                CodeGeneratorFactory.XSD_TARGET_FORMAT, XSDLibrary.class,
                (CodeGenerationFilenameBuilder<XSDLibrary>) filenameBuilder, legacySchemaFilter);
        CodeGenerator<XSDLibrary> legacyExtensionGenerator = newCodeGenerator(
                CodeGeneratorFactory.EXT_XSD_TARGET_FORMAT, XSDLibrary.class,
                (CodeGenerationFilenameBuilder<XSDLibrary>) filenameBuilder, legacySchemaFilter);

        for (XSDLibrary library : legacySchemas) {
            addGeneratedFiles(legacyGenerator.generateOutput(library, context));
            addGeneratedFiles(legacyExtensionGenerator.generateOutput(library, context));
        }

        // Generate output for all built-in libraries
        if (model != null) {
            CodeGenerator<BuiltInLibrary> xsdGenerator = newCodeGenerator(
                    CodeGeneratorFactory.XSD_TARGET_FORMAT, BuiltInLibrary.class,
                    (CodeGenerationFilenameBuilder<BuiltInLibrary>) filenameBuilder, filter);
            CodeGenerationContext builtInContext = context.getCopy();

            if (xsdGenerator instanceof XsdBuiltInCodeGenerator) {
                ((XsdBuiltInCodeGenerator) xsdGenerator).setImportSchemaLocations(importLocations);
            }

            for (BuiltInLibrary library : model.getBuiltInLibraries()) {
                addGeneratedFiles(xsdGenerator.generateOutput(library, builtInContext));
            }
        }

        // Generate any consolidated import files (if needed)
        addGeneratedFiles(importLocations.generateConsolidatedImportFiles());
    }

    /**
     * Performs a dependency analysis of all the libraries that are to be included in the generated
     * schema output. For each included library, the name and location of the generated schemas are
     * pre-calculated for the purpose of creating consistent import declarations across all of the
     * generated schemas.
     * 
     * @param model
     *            the model from which schemas are to be generated
     * @param context
     *            the code generation context to use for code generation
     * @param filenameBuilder
     *            the filename builder to assign to the code generator(s) used by this method
     * @param filter
     *            the filter used to identify specific artifacts for which schema generation is
     *            required
     * @return ImportSchemaLocations
     */
    @SuppressWarnings("unchecked")
    private ImportSchemaLocations analyzeImportDependencies(TLModel model,
            CodeGenerationContext context, CodeGenerationFilenameBuilder<?> filenameBuilder,
            CodeGenerationFilter filter) {
        if (filenameBuilder == null) {
            filenameBuilder = new LibraryFilenameBuilder<>();
        }
        CodeGenerationFilenameBuilder<TLLibrary> tlFilenameBuilder = (CodeGenerationFilenameBuilder<TLLibrary>) filenameBuilder;
        CodeGenerationFilenameBuilder<XSDLibrary> xsdFilenameBuilder = (CodeGenerationFilenameBuilder<XSDLibrary>) filenameBuilder;
        CodeGenerationFilenameBuilder<XSDLibrary> extFilenameBuilder = new LegacySchemaExtensionFilenameBuilder<>(xsdFilenameBuilder);
        CodeGenerationFilenameBuilder<BuiltInLibrary> builtInFilenameBuilder = new LibraryFilenameBuilder<>();

        File outputFolder = XsdCodegenUtils.getBaseOutputFolder(context);
        File legacyOutputFolder = new File(outputFolder,
                XsdCodegenUtils.getLegacySchemaOutputLocation(context));
        File builtInOutputFolder = new File(outputFolder,
                XsdCodegenUtils.getBuiltInSchemaOutputLocation(context));

        ImportSchemaLocations importLocations = new ImportSchemaLocations(outputFolder);

        for (TLLibrary library : model.getUserDefinedLibraries()) {
            if ((filter == null) || filter.processLibrary(library)) {
                importLocations.setSchemaLocation(library.getNamespace(), library.getPrefix(),
                        new File(outputFolder, tlFilenameBuilder.buildFilename(library, "xsd")));
            }
        }
        for (XSDLibrary library : model.getLegacySchemaLibraries()) {
            if ((filter == null) || filter.processLibrary(library)) {
                importLocations.setSchemaLocation(
                        library.getNamespace(),
                        library.getPrefix(),
                        new File(legacyOutputFolder, xsdFilenameBuilder.buildFilename(library,
                                "xsd")));
            }
            if ((filter != null) && filter.processExtendedLibrary(library)) {
                importLocations.setSchemaLocation(
                        library.getNamespace(),
                        library.getPrefix(),
                        new File(legacyOutputFolder, extFilenameBuilder.buildFilename(library,
                                "xsd")));
            }
        }

        // For built-ins, we have to dig into the schema declaration dependencies that are defined
        // in
        // the Spring application context
        Set<SchemaDeclaration> builtInDependencies = new HashSet<>();

        for (BuiltInLibrary library : model.getBuiltInLibraries()) {
            if (library.getNamespace().equals(XMLConstants.W3C_XML_SCHEMA_NS_URI)) {
                continue; // skip the schema-for-schemas built-in
            }
            if ((filter == null) || filter.processLibrary(library)) {
                addBuiltInSchemaDependencies(library.getSchemaDeclaration(), builtInDependencies);
            }
        }
        for (SchemaDeclaration builtIn : builtInDependencies) {
            String filename = builtIn.getFilename(CodeGeneratorFactory.XSD_TARGET_FORMAT);

            if (!filename.toLowerCase().endsWith(".xsd")) {
                BuiltInLibrary builtInLib = getBuiltInLibrary(builtIn, model);

                if (builtInLib != null) {
                    filename = builtInFilenameBuilder.buildFilename(builtInLib, "xsd");
                }
            }
            importLocations.setSchemaLocation(builtIn.getNamespace(), builtIn.getDefaultPrefix(),
                    new File(builtInOutputFolder, filename));
        }
        return importLocations;
    }

    /**
     * Searches the implied schema dependencies of the given built-in and adds any required
     * libraries to the list.
     * 
     * @param builtIn
     *            the built-in library to analyze
     * @param builtInDependencies
     */
    private void addBuiltInSchemaDependencies(SchemaDeclaration builtIn,
            Set<SchemaDeclaration> schemaDependencies) {
        ApplicationContext appContext = SchemaCompilerApplicationContext.getContext();

        if (!schemaDependencies.contains(builtIn)) {
            for (String dependencyId : builtIn.getDependencies()) {
                addBuiltInSchemaDependencies((SchemaDeclaration) appContext.getBean(dependencyId),
                        schemaDependencies);
            }
            schemaDependencies.add(builtIn);
        }
    }

    /**
     * Returns the built-in library that is associated with the schema declaration provided.
     * 
     * @param builtInDeclaration
     *            the schema declaration for the built-in library
     * @param model
     *            the model from which the built-in library should be retrieved
     * @return BuiltInLibrary
     */
    private BuiltInLibrary getBuiltInLibrary(SchemaDeclaration builtInDeclaration, TLModel model) {
        BuiltInLibrary library = null;

        for (BuiltInLibrary lib : model.getBuiltInLibraries()) {
            if (lib.getSchemaDeclaration() == builtInDeclaration) {
                library = lib;
                break;
            }
        }
        return library;
    }

    /**
     * Compiles the XML schema files for the given model using the context, filename builder, and
     * code generation filter provided.
     * 
     * @param userDefinedLibraries  the list of user-defined libraries for which to compile XML schema artifacts
     * @param legacySchemas  the list of legacy schemas (xsd files) for which to compile XML schema artifacts
     * @param context  the code generation context to use for code generation
     * @param filenameBuilder  the filename builder to assign to the code generator(s) used by this method
     * @param filter  the filter used to identify specific artifacts for which schema generation is required
     * @throws SchemaCompilerException
     */
    @SuppressWarnings("unchecked")
    protected void compileJsonSchemas(Collection<TLLibrary> userDefinedLibraries,
            Collection<XSDLibrary> legacySchemas, CodeGenerationContext context,
            CodeGenerationFilenameBuilder<?> filenameBuilder, CodeGenerationFilter filter)
            throws SchemaCompilerException {

        // Generate output for all user-defined libraries
        TLModel model = getModel(userDefinedLibraries, legacySchemas);

        if (model == null) {
            throw new SchemaCompilerException(
                    "No libraries or legacy schemas found for code generation task.");
        }

        for (TLLibrary library : userDefinedLibraries) {
            CodeGenerator<TLLibrary> jsonSchemaGenerator = newCodeGenerator(
                    CodeGeneratorFactory.JSON_SCHEMA_TARGET_FORMAT, TLLibrary.class,
                    (CodeGenerationFilenameBuilder<TLLibrary>) filenameBuilder, filter);

            addGeneratedFiles(jsonSchemaGenerator.generateOutput(library, context));

            // If any OTM built-in dependencies were identified, add them to the current filter
            if ((filter != null) && (jsonSchemaGenerator instanceof AbstractJsonSchemaCodeGenerator)) {
            	AbstractJsonSchemaCodeGenerator<?> generator = (AbstractJsonSchemaCodeGenerator<?>) jsonSchemaGenerator;

                for (SchemaDeclaration schemaDeclaration : generator.getCompileTimeDependencies()) {
                	String schemaFilename = schemaDeclaration.getFilename(CodeGeneratorFactory.JSON_SCHEMA_TARGET_FORMAT);
                	
                    if ((schemaFilename != null) && schemaFilename.endsWith(".json")) {
                        AbstractLibrary dependentLib = model.getLibrary(
                                schemaDeclaration.getNamespace(), schemaDeclaration.getName());

                        if (dependentLib instanceof BuiltInLibrary) {
                            filter.addBuiltInLibrary((BuiltInLibrary) dependentLib);
                        }
                    }
                }
            }
        }

        // Generate output for all built-in libraries
        if (model != null) {
            CodeGenerator<BuiltInLibrary> xsdGenerator = newCodeGenerator(
                    CodeGeneratorFactory.JSON_SCHEMA_TARGET_FORMAT, BuiltInLibrary.class,
                    (CodeGenerationFilenameBuilder<BuiltInLibrary>) filenameBuilder, filter);
            CodeGenerationContext builtInContext = context.getCopy();

            for (BuiltInLibrary library : model.getBuiltInLibraries()) {
                addGeneratedFiles(xsdGenerator.generateOutput(library, builtInContext));
            }
        }
    }

    /**
     * @see org.opentravel.schemacompiler.task.AbstractCompilerTask#createContext()
     */
    @Override
    protected CodeGenerationContext createContext() {
        CodeGenerationContext context = super.createContext();
        
        if (!generateMaxDetailsForExamples) {
            context.setValue(CodeGenerationContext.CK_EXAMPLE_DETAIL_LEVEL, "MINIMUM");
        }
        if (exampleContext != null) {
            context.setValue(CodeGenerationContext.CK_EXAMPLE_CONTEXT, exampleContext);
        }
        if (exampleMaxRepeat != null) {
            context.setValue(CodeGenerationContext.CK_EXAMPLE_MAX_REPEAT, exampleMaxRepeat.toString());
        }
        if (exampleMaxDepth != null) {
            context.setValue(CodeGenerationContext.CK_EXAMPLE_MAX_DEPTH, exampleMaxDepth.toString());
        }
        context.setValue(CodeGenerationContext.CK_SUPRESS_OTM_EXTENSIONS, suppressOtmExtensions + "");
        context.setValue(CodeGenerationContext.CK_SUPPRESS_OPTIONAL_FIELDS, suppressOptionalFields + "");
        return context;
    }

    /**
     * @see org.opentravel.schemacompiler.task.AbstractCompilerTask#applyTaskOptions(org.opentravel.schemacompiler.task.CommonCompilerTaskOptions)
     */
    @Override
    public void applyTaskOptions(CommonCompilerTaskOptions taskOptions) {
        if (taskOptions instanceof SchemaCompilerTaskOptions) {
        	setSuppressOtmExtensions( ((SchemaCompilerTaskOptions) taskOptions).isSuppressOtmExtensions() );
        }
        if (taskOptions instanceof ExampleCompilerTaskOptions) {
            ExampleCompilerTaskOptions exampleOptions = (ExampleCompilerTaskOptions) taskOptions;

            setGenerateExamples(exampleOptions.isGenerateExamples());
            setGenerateMaxDetailsForExamples(exampleOptions.isGenerateMaxDetailsForExamples());
            setExampleContext(exampleOptions.getExampleContext());
            setExampleMaxRepeat(exampleOptions.getExampleMaxRepeat());
            setExampleMaxDepth(exampleOptions.getExampleMaxDepth());
            setSuppressOptionalFields(exampleOptions.isSuppressOptionalFields());
        }
        super.applyTaskOptions(taskOptions);
    }

	/**
	 * @see org.opentravel.schemacompiler.task.ResourceCompilerTaskOptions#isSuppressOtmExtensions()
	 */
	@Override
	public boolean isSuppressOtmExtensions() {
		return suppressOtmExtensions;
	}

    /**
     * Assigns the option flag indicating that all 'x-otm-' extensions should be
     * suppressed in the generated swagger document(s)
     * 
     * @param suppressOtmExtensions
     *            the task option value to assign
     */
    public void setSuppressOtmExtensions(boolean suppressOtmExtensions) {
        this.suppressOtmExtensions = suppressOtmExtensions;
    }

    /**
     * @see org.opentravel.schemacompiler.task.CommonCompilerTaskOptions#isGenerateExamples()
     */
    @Override
    public boolean isGenerateExamples() {
        return generateExamples;
    }

    /**
     * Assigns the option flag indicating that EXAMPLE XML documents should be generated.
     * 
     * @param compileRAS
     *            the task option value to assign
     */
    public void setGenerateExamples(boolean generateExamples) {
        this.generateExamples = generateExamples;
    }

    /**
     * @see org.opentravel.schemacompiler.task.CommonCompilerTaskOptions#isGenerateMaxDetailsForExamples()
     */
    @Override
    public boolean isGenerateMaxDetailsForExamples() {
        return generateMaxDetailsForExamples;
    }

    /**
     * Assigns the flag indicating whether the maximum amount of detail is to be included in
     * generated EXAMPLE data. If false, minimum detail will be generated.
     * 
     * @param generateMaxDetailsForExamples
     *            the boolean flag value to assign
     */
    public void setGenerateMaxDetailsForExamples(boolean generateMaxDetailsForExamples) {
        this.generateMaxDetailsForExamples = generateMaxDetailsForExamples;
    }

    /**
     * @see org.opentravel.schemacompiler.task.CommonCompilerTaskOptions#getExampleContext()
     */
    @Override
    public String getExampleContext() {
        return exampleContext;
    }

    /**
     * Assigns the preferred context to use when producing EXAMPLE values for simple data types.
     * 
     * @param exampleContext
     *            the context ID to assign
     */
    public void setExampleContext(String exampleContext) {
        this.exampleContext = exampleContext;
    }

    /**
     * @see org.opentravel.schemacompiler.task.CommonCompilerTaskOptions#getExampleMaxRepeat()
     */
    @Override
    public Integer getExampleMaxRepeat() {
        return exampleMaxRepeat;
    }

    /**
     * Assigns the maximum number of times that repeating elements should be displayed in generated
     * EXAMPLE output.
     * 
     * @param exampleMaxRepeat
     *            the max repeat value to assign
     */
    public void setExampleMaxRepeat(Integer exampleMaxRepeat) {
        this.exampleMaxRepeat = exampleMaxRepeat;
    }

    /**
     * @see org.opentravel.schemacompiler.task.CommonCompilerTaskOptions#getExampleMaxDepth()
     */
    @Override
    public Integer getExampleMaxDepth() {
        return exampleMaxDepth;
    }

    /**
     * Assigns the maximum depth that should be included for nested elements in generated EXAMPLE
     * output.
     * 
     * @param exampleMaxDepth
     *            the max depth value to assign
     */
    public void setExampleMaxDepth(Integer exampleMaxDepth) {
        this.exampleMaxDepth = exampleMaxDepth;
    }

    /**
	 * @see org.opentravel.schemacompiler.task.ExampleCompilerTaskOptions#isSuppressOptionalFields()
	 */
	@Override
	public boolean isSuppressOptionalFields() {
		return suppressOptionalFields;
	}

    /**
     * Assigns the flag indicating whether optional fields should be suppressed
	 * during EXAMPLE generation.
     * 
     * @param generateExamples  the flag value to assign
     */
    public void setSuppressOptionalFields(boolean suppressOptionalFields) {
        this.suppressOptionalFields = suppressOptionalFields;
    }

}
