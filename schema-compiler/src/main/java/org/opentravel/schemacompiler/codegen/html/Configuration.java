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
/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.opentravel.schemacompiler.codegen.html;

import java.util.List;
import java.util.Locale;

import org.opentravel.schemacompiler.codegen.example.ExampleGeneratorOptions;
import org.opentravel.schemacompiler.codegen.html.builders.DocumentationBuilder;
import org.opentravel.schemacompiler.codegen.html.builders.DocumentationBuilderFactory;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.BuiltInLibrary;
import org.opentravel.schemacompiler.model.LibraryMember;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLModel;

/**
 * Configure the output based on the command line options.
 * <p>
 * Also determine the length of the command line option. For EXAMPLE, for a
 * option "-header" there will be a string argument associated, then the the
 * length of option "-header" is two. But for option "-nohelp" no argument is
 * needed so it's length is 1.
 * </p>
 * <p>
 * Also do the error checking on the options used. For EXAMPLE it is illegal to
 * use "-helpfile" option when already "-nohelp" option is used.
 * </p>
 *
 * @author Robert Field.
 * @author Atul Dambalkar.
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class Configuration {
	

	/**
	 * Location of doclet properties file.
	 */
	public static final String DOCLETS_RESOURCE = "org.opentravel.schemacompiler.codegen.html.resources.doclets";
	
	/**
	 * The namespace for XSD schemas.
	 */
	private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

	/**
	 * The build date. Note: For now, we will use a version number instead of a
	 * date.
	 */
	public static final String BUILD_DATE = System.getProperty("java.version");
	
	/**
	 * The build date. Note: For now, we will use a version number instead of a
	 * date.
	 */
	public static final String VERSION = "3.0";

	/**
	 * The name of the constant values file.
	 */
	public static final String CONSTANTS_FILE_NAME = "constant-values.html";

	private static Configuration instance = new Configuration();

	/**
	 * Argument for command line option "-doctitle".
	 */
	private String doctitle = "";

	/**
	 * Argument for command line option "-windowtitle".
	 */
	private String windowtitle = "";

	/**
	 * Argument for command line option "-stylesheetfile".
	 */
	private String stylesheetfile = "";

	/**
	 * This is true if option "-overview" is used or option "-overview" is not
	 * used and number of packages is more than one.
	 */
	private boolean createoverview = true;

	/**
	 * Unique Resource Handler for this package.
	 */
	public final MessageRetriever message;

	/**
	 * First file to appear in the right-hand frame in the generated
	 * documentation.
	 */
	private String topFile = "";

	/**
	 * The classdoc for the class file getting generated.
	 */
	private DocumentationBuilder currentMember = null;

	/**
	 * The Root of the generated Program Structure from the Doclet API.
	 */
	private TLModel model;

	/**
	 * Destination directory name, in which doclet will generate the entire
	 * documentation. Default is current directory.
	 */
	private String destDirName = "";

	/**
	 * Encoding for this document. Default is default encoding for this
	 * platform.
	 */
	private String docencoding = null;

	/**
	 * True if user wants to add member names as meta keywords. Set to false
	 * because meta keywords are ignored in general by most Internet search
	 * engines.
	 */
	private boolean keywords = false;

	private Messager messager = new Messager("OTM Documentation");
	
	/**
     * True if command line option "-nohelp" is used. Default value is false.
     */
    private boolean nohelp = true;
    
    /**
     * False if command line option "-noindex" is used. Default value is true.
     */
    private boolean createindex = false;

    /**
     * True if command line option "-use" is used. Default value is false.
     */
    private boolean classuse = false;

    /**
     * False if command line option "-notree" is used. Default value is true.
     */
    private boolean createtree = false;
    
    /**
     * True if command line option "-splitindex" is used. Default value is
     * false.
     */
    private boolean splitindex = false;
    
    /**
     * Argument for command line option "-helpfile".
     */
    private String helpfile = "";

	/**
	 * Set this to true if you would like to not emit any errors, warnings and
	 * notices.
	 */
	private boolean silent = false;

	private DocumentationBuilderFactory builderFactory;

	private boolean isGenerateBuiltins = false;
	
	private ExampleGeneratorOptions exampleOptions;
	
	/**
	 * Constructor. Initializes resource for the
	 * {@link com.sun.tools.doclets.MessageRetriever}.
	 */
	private Configuration() {
		message = new MessageRetriever(this, DOCLETS_RESOURCE);
		setTopFile("overview-summary.html");
	}

	/**
	 * Reset to a fresh new Configuration, to allow multiple invocations of
	 * javadoc within a single VM. It would be better not to be using static
	 * fields at all, but .... (sigh).
	 */
	public static void reset() {
		instance = new Configuration();
	}

	public static Configuration getInstance() {
		return instance;
	}

	/**
	 * Return the build date for the doclet.
	 */
	public String getDocletSpecificBuildDate() {
		return BUILD_DATE;
	}

	/**
	 * Return the qualified name of the <code>ClassDoc</code> if it's qualifier
	 * is not excluded. Otherwise, return the unqualified <code>ClassDoc</code>
	 * name.
	 * 
	 * @param cd
	 *            the <code>ClassDoc</code> to check.
	 */
	public String getQualifiedName(DocumentationBuilder member) {
		String namespace = member.getNamespace();
		String localName = member.getName();
		return null == namespace ? localName : namespace + ":" + localName;
	}

	/**
	 * Return the qualified name of the <code>ClassDoc</code> if it's qualifier
	 * is not excluded. Otherwise, return the unqualified <code>ClassDoc</code>
	 * name.
	 * 
	 * @param cd
	 *            the <code>ClassDoc</code> to check.
	 */
	public String getQualifiedName(LibraryMember member) {
		String namespace = member.getNamespace();
		String localName = member.getLocalName();
		return null == namespace ? localName : namespace + ":" + localName;
	}


	public String getText(String key) {
		try {
			// Check the doclet specific properties file.
			return getDocletSpecificMsg().getText(key);
		} catch (Exception e) {
			// Check the shared properties file.
			return message.getText(key);
		}
	}

	public String getText(String key, String a1) {
		try {
			// Check the doclet specific properties file.
			return getDocletSpecificMsg().getText(key, a1);
		} catch (Exception e) {
			// Check the shared properties file.
			return message.getText(key, a1);
		}
	}

	public String getText(String key, String a1, String a2) {
		try {
			// Check the doclet specific properties file.
			return getDocletSpecificMsg().getText(key, a1, a2);
		} catch (Exception e) {
			// Check the shared properties file.
			return message.getText(key, a1, a2);
		}
	}

	public Locale getLocale() {
		return Locale.getDefault();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public MessageRetriever getDocletSpecificMsg() {
		return message;
	}

	/**
	 * Return true if the ClassDoc element is getting documented, depending upon
	 * -nodeprecated option and the deprecation information. Return true if
	 * -nodeprecated is not used. Return false if -nodeprecated is used and if
	 * either ClassDoc element is deprecated or the containing package is
	 * deprecated.
	 *
	 * @param builder
	 *            the ClassDoc for which the page generation is checked
	 */
	public boolean isGeneratedDoc(DocumentationBuilder builder) {
		boolean isGenerated = true;
		String namespace = builder.getNamespace();
		if(XSD_NAMESPACE.equals(namespace)){
			isGenerated = false;
		}else if(!isGenerateBuiltins){
			List<AbstractLibrary> libs = getModel().getLibrariesForNamespace(namespace);
			//only need one library to determine if its a builtin
			// is this faster than traversing getBuiltinLibraries?
			if(!libs.isEmpty() && libs.get(0) instanceof BuiltInLibrary){
				isGenerated = false;
			}
		}
		
		return isGenerated;
	}
	
	/**
	 * Return true if the ClassDoc element is getting documented, depending upon
	 * -nodeprecated option and the deprecation information. Return true if
	 * -nodeprecated is not used. Return false if -nodeprecated is used and if
	 * either ClassDoc element is deprecated or the containing package is
	 * deprecated.
	 *
	 * @param builder
	 *            the ClassDoc for which the page generation is checked
	 */
	public boolean isGeneratedDoc(LibraryMember member) {
		return member != null;
	}

	public List<TLLibrary> getLibraries() {
		return getModel().getUserDefinedLibraries();
	}

	/**
	 * Print error message, increment error count.
	 *
	 * @param msg
	 *            message to print.
	 */
	public void printError(String msg) {
		if (silent)
			return;
		getMessager().printError(msg);
	}

	/**
	 * Print error message, increment error count.
	 *
	 * @param msg
	 *            message to print.
	 */
	public void printError(SourcePosition pos, String msg) {
		if (silent)
			return;
		getMessager().printError(pos, msg);
	}

	/**
	 * Print warning message, increment warning count.
	 *
	 * @param msg
	 *            message to print.
	 */
	public void printWarning(String msg) {
		if (silent)
			return;
		getMessager().printWarning(msg);
	}

	/**
	 * Print warning message, increment warning count.
	 *
	 * @param msg
	 *            message to print.
	 */
	public void printWarning(SourcePosition pos, String msg) {
		if (silent)
			return;
		getMessager().printWarning(pos, msg);
	}

	/**
	 * Print a message.
	 *
	 * @param msg
	 *            message to print.
	 */
	public void printNotice(String msg) {
		if (silent)
			return;
		getMessager().printNotice(msg);
	}

	/**
	 * Print a message.
	 *
	 * @param msg
	 *            message to print.
	 */
	public void printNotice(SourcePosition pos, String msg) {
		if (silent)
			return;
		getMessager().printNotice(pos, msg);
	}

	/**
	 * Return the builder factory for this doclet.
	 *
	 * @return the builder factory for this doclet.
	 */
	public DocumentationBuilderFactory getBuilderFactory() {
		if (builderFactory == null) {
			builderFactory = DocumentationBuilderFactory.getInstance();
		}
		return builderFactory;
	}

	public void setModel(TLModel model) {
		this.model = model;
	}

	/**
	 * @param destDirName
	 *            the destDirName to set
	 */
	public void setDestDirName(String destDirName) {
		this.destDirName = destDirName;
	}

	/**
	 * @return the model
	 */
	public TLModel getModel() {
		return model;
	}

	/**
	 * @return the destDirName
	 */
	public String getDestDirName() {
		return destDirName;
	}

	/**
	 * @param windowtitle the windowtitle to set
	 */
	public void setWindowtitle(String windowtitle) {
		this.windowtitle = windowtitle;
	}

	/**
	 * Returns the value of the 'exampleOptions' field.
	 *
	 * @return ExampleGeneratorOptions
	 */
	public ExampleGeneratorOptions getExampleOptions() {
		return exampleOptions;
	}

	/**
	 * Assigns the value of the 'exampleOptions' field.
	 *
	 * @param exampleOptions  the field value to assign
	 */
	public void setExampleOptions(ExampleGeneratorOptions exampleOptions) {
		this.exampleOptions = exampleOptions;
	}

	/**
	 * Returns the value of the 'doctitle' field.
	 *
	 * @return String
	 */
	public String getDoctitle() {
		return doctitle;
	}

	/**
	 * Assigns the value of the 'doctitle' field.
	 *
	 * @param doctitle  the field value to assign
	 */
	public void setDoctitle(String doctitle) {
		this.doctitle = doctitle;
	}

	/**
	 * Returns the value of the 'windowtitle' field.
	 *
	 * @return String
	 */
	public String getWindowtitle() {
		return windowtitle;
	}

	/**
	 * Returns the value of the 'stylesheetfile' field.
	 *
	 * @return String
	 */
	public String getStylesheetfile() {
		return stylesheetfile;
	}

	/**
	 * Assigns the value of the 'stylesheetfile' field.
	 *
	 * @param stylesheetfile  the field value to assign
	 */
	public void setStylesheetfile(String stylesheetfile) {
		this.stylesheetfile = stylesheetfile;
	}

	/**
	 * Returns the value of the 'createoverview' field.
	 *
	 * @return boolean
	 */
	public boolean isCreateoverview() {
		return createoverview;
	}

	/**
	 * Assigns the value of the 'createoverview' field.
	 *
	 * @param createoverview  the field value to assign
	 */
	public void setCreateoverview(boolean createoverview) {
		this.createoverview = createoverview;
	}

	/**
	 * Returns the value of the 'topFile' field.
	 *
	 * @return String
	 */
	public String getTopFile() {
		return topFile;
	}

	/**
	 * Assigns the value of the 'topFile' field.
	 *
	 * @param topFile  the field value to assign
	 */
	public void setTopFile(String topFile) {
		this.topFile = topFile;
	}

	/**
	 * Returns the value of the 'currentMember' field.
	 *
	 * @return DocumentationBuilder
	 */
	public DocumentationBuilder getCurrentMember() {
		return currentMember;
	}

	/**
	 * Assigns the value of the 'currentMember' field.
	 *
	 * @param currentMember  the field value to assign
	 */
	public void setCurrentMember(DocumentationBuilder currentMember) {
		this.currentMember = currentMember;
	}

	/**
	 * Returns the value of the 'docencoding' field.
	 *
	 * @return String
	 */
	public String getDocencoding() {
		return docencoding;
	}

	/**
	 * Assigns the value of the 'docencoding' field.
	 *
	 * @param docencoding  the field value to assign
	 */
	public void setDocencoding(String docencoding) {
		this.docencoding = docencoding;
	}

	/**
	 * Returns the value of the 'keywords' field.
	 *
	 * @return boolean
	 */
	public boolean isKeywords() {
		return keywords;
	}

	/**
	 * Assigns the value of the 'keywords' field.
	 *
	 * @param keywords  the field value to assign
	 */
	public void setKeywords(boolean keywords) {
		this.keywords = keywords;
	}

	/**
	 * Returns the value of the 'messager' field.
	 *
	 * @return Messager
	 */
	public Messager getMessager() {
		return messager;
	}

	/**
	 * Assigns the value of the 'messager' field.
	 *
	 * @param messager  the field value to assign
	 */
	public void setMessager(Messager messager) {
		this.messager = messager;
	}

	/**
	 * Returns the value of the 'nohelp' field.
	 *
	 * @return boolean
	 */
	public boolean isNohelp() {
		return nohelp;
	}

	/**
	 * Assigns the value of the 'nohelp' field.
	 *
	 * @param nohelp  the field value to assign
	 */
	public void setNohelp(boolean nohelp) {
		this.nohelp = nohelp;
	}

	/**
	 * Returns the value of the 'createindex' field.
	 *
	 * @return boolean
	 */
	public boolean isCreateindex() {
		return createindex;
	}

	/**
	 * Assigns the value of the 'createindex' field.
	 *
	 * @param createindex  the field value to assign
	 */
	public void setCreateindex(boolean createindex) {
		this.createindex = createindex;
	}

	/**
	 * Returns the value of the 'classuse' field.
	 *
	 * @return boolean
	 */
	public boolean isClassuse() {
		return classuse;
	}

	/**
	 * Assigns the value of the 'classuse' field.
	 *
	 * @param classuse  the field value to assign
	 */
	public void setClassuse(boolean classuse) {
		this.classuse = classuse;
	}

	/**
	 * Returns the value of the 'createtree' field.
	 *
	 * @return boolean
	 */
	public boolean isCreatetree() {
		return createtree;
	}

	/**
	 * Assigns the value of the 'createtree' field.
	 *
	 * @param createtree  the field value to assign
	 */
	public void setCreatetree(boolean createtree) {
		this.createtree = createtree;
	}

	/**
	 * Returns the value of the 'splitindex' field.
	 *
	 * @return boolean
	 */
	public boolean isSplitindex() {
		return splitindex;
	}

	/**
	 * Assigns the value of the 'splitindex' field.
	 *
	 * @param splitindex  the field value to assign
	 */
	public void setSplitindex(boolean splitindex) {
		this.splitindex = splitindex;
	}

	/**
	 * Returns the value of the 'helpfile' field.
	 *
	 * @return String
	 */
	public String getHelpfile() {
		return helpfile;
	}

	/**
	 * Assigns the value of the 'helpfile' field.
	 *
	 * @param helpfile  the field value to assign
	 */
	public void setHelpfile(String helpfile) {
		this.helpfile = helpfile;
	}

}
