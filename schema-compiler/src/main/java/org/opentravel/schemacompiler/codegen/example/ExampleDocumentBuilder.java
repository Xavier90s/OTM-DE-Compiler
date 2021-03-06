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
package org.opentravel.schemacompiler.codegen.example;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opentravel.schemacompiler.codegen.CodeGenerationException;
import org.opentravel.schemacompiler.codegen.util.PropertyCodegenUtils;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.TLExtensionPointFacet;
import org.opentravel.schemacompiler.validate.ValidationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Builder component that is capable of producing XML EXAMPLE output for model entities in a variety
 * of formats (e.g. DOM, text, and streaming output).
 * 
 * @author S. Livezey
 */
public class ExampleDocumentBuilder extends ExampleBuilder<Document>{

    private static final String XML_HEADER_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n\n";
    
    private Map<String,String> schemaLocations = new HashMap<>();

    
    /**
     * Constructor that assigns the EXAMPLE generation options to use when constructing the EXAMPLE
     * content and formatting the text/stream output.
     * 
     * @param options
     *            the EXAMPLE generation options
     */
    public ExampleDocumentBuilder(ExampleGeneratorOptions options) {
        super(options);
    }
  
    /**
     * Assigns the location of the XML schema (XSD) file that should be used to validate the content
     * from the specified namespace. Schema locations will only be included in the EXAMPLE XML
     * output if they are bound to one or more elements in the resulting XML document.
     * 
     * @param library  the library for which to assign a schema location
     * @return ExampleDocumentBuilder
     */
    public ExampleDocumentBuilder addSchemaLocation(AbstractLibrary library, String schemaLocation) {
    	String slocKey = library.getNamespace() + "#" + library.getName();
    	
        if (!schemaLocations.containsKey(slocKey)) {
            schemaLocations.put(slocKey, schemaLocation);
        }
        if (!schemaLocations.containsKey(library.getNamespace())) {
            schemaLocations.put(library.getNamespace(), schemaLocation);
        }
        return this;
    }
    
    /**
     * Returns the schema location of the given library.  If a schema location has not been
     * added for the requested library, this method will return null.
     * 
     * @param library  the library for which to return a schema location
     * @return String
     */
    private String getSchemaLocation(AbstractLibrary library) {
    	String slocKey = library.getNamespace() + "#" + library.getName();
    	
    	return schemaLocations.get( slocKey );
    }

    /**
     * Returns the schema location of the specified namespace.  If a schema location has
     * not been added for the namespace, this method will return null.
     * 
     * @param namespace  the namespace for which to return a schema location
     * @return String
     */
    private String getSchemaLocation(String namespace) {
    	return schemaLocations.get( namespace );
    }

   
    /**
     * Generates the EXAMPLE output and directs the resuting content to the specified writer.
     * 
     * @param buffer
     *            the output writer to which the EXAMPLE content should be directed
     * @return String
     * @throws ValidationException
     *             thrown if one or more of the entities for which content is to be generated
     *             contains errors (warnings are acceptable and will not produce an exception)
     * @throws CodeGenerationException
     *             thrown if an error occurs during EXAMPLE content generation
     */
    @Override
    public void buildToStream(Writer buffer) throws ValidationException, CodeGenerationException {
        try {
            TransformerFactory transFactory = TransformerFactory.newInstance();
            
            transFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            
            Transformer transformer = transFactory.newTransformer();
            Document domDocument = buildTree();

            buffer.write(XML_HEADER_CONTENT);
            buffer.flush();

            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(domDocument), new StreamResult(buffer));

        } catch (TransformerException | IOException e) {
            throw new CodeGenerationException(e);
        }
    }

    /**
     * Generates the EXAMPLE output as a DOM structure and returns the raw tree content.
     * 
     * @return Document
     * @throws ValidationException
     *             thrown if one or more of the entities for which content is to be generated
     *             contains errors (warnings are acceptable and will not produce an exception)
     * @throws CodeGenerationException
     *             thrown if an error occurs during EXAMPLE content generation
     */
    public Document buildTree() throws ValidationException, CodeGenerationException {
        DOMExampleVisitor visitor = new DOMExampleVisitor(options.getExampleContext());
        Document domDocument;

        validateModelElement();
        ExampleNavigator.navigate(modelElement, visitor, options);
        domDocument = visitor.getDocument();

        if (schemaLocationRequired()) {
            Element rootElement = domDocument.getDocumentElement();
            StringBuilder schemaLocation = new StringBuilder();

            // Construct the xsi:schemaLocation string for the XML document
            for (String boundNS : visitor.getBoundNamespaces()) {
            	String sLoc;
            	
            	if (boundNS.equals( modelElement.getNamespace() )) {
            		sLoc = getSchemaLocation( modelElement.getOwningLibrary() );
            		
            	} else {
            		sLoc = getSchemaLocation( boundNS );
            	}
                if (sLoc != null) {
                    if (schemaLocation.length() > 0)
                        schemaLocation.append(" ");
                    schemaLocation.append(boundNS).append(' ').append( sLoc );
                }
            }

            // If any bound namespace were resolved to a schema location, assign the schema location
            // value to the XML document
            if (schemaLocation.length() > 0) {
                rootElement.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xsi",
                        XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
                rootElement.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,
                        "xsi:schemaLocation", schemaLocation.toString());
            }
        }
        return domDocument;
    }
    
    /**
     * Returns true if an 'xsi:schemaLocation' attribute should be added to the
     * root element of the EXAMPLE for the given entity.
     * 
     * @return boolean
     */
    private boolean schemaLocationRequired() {
    	return PropertyCodegenUtils.hasGlobalElement(modelElement)
    			|| (modelElement instanceof TLExtensionPointFacet);
    }
    
}
