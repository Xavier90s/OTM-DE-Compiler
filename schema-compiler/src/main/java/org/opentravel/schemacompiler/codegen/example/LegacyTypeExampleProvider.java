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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.opentravel.schemacompiler.ioc.CompilerExtensionRegistry;

/**
 * Component that handles the creation of EXAMPLE data values for simple types defined in legacy
 * schemas. This is typically used for built-in libraries that are based on legacy XSD schemas
 * (built-in OTM libraries are capable of defining their own examples).
 * 
 * @author S. Livezey
 */
public class LegacyTypeExampleProvider {

    private static final String NAMESPACE_PROPERTY = "namespace";

    private String namespace;
    private Map<String, List<String>> exampleTypeMappings = new HashMap<>();

    /**
     * Constructor that defines the classpath location of the configuration file used to define the
     * EXAMPLE data for the legacy types within a built-in namespace.
     * 
     * @param exampleFileLocation
     *            the classpath location of the EXAMPLE data configuration file
     */
    public LegacyTypeExampleProvider(String exampleFileLocation) {
        loadExampleFile(exampleFileLocation);
    }

    /**
     * Returns the namespace of the simple legacy types that are serviced by this EXAMPLE provider.
     * 
     * @return String
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns an EXAMPLE value for the specified legacy simple type.
     * 
     * @param xsdSimple
     *            the legacy simple type for which to return an EXAMPLE
     * @return String
     */
    public String getExampleValue(String xsdSimpleName) {
        String example = null;

        if (xsdSimpleName != null) {
            List<String> exampleValues = exampleTypeMappings.get(xsdSimpleName);

            if ((exampleValues != null) && !exampleValues.isEmpty()) {
                // Retrieve the next EXAMPLE and shift the value to the end of the
                // rotating list for this type
                synchronized (exampleValues) {
                    example = exampleValues.remove(0);
                    exampleValues.add(example);
                }
            }
        }
        return example;
    }

    /**
     * Loads the specified configuration file and populates this provider instance.
     * 
     * @param exampleFileLocation
     *            the classpath location of the EXAMPLE data configuration file
     */
    private void loadExampleFile(String exampleFileLocation) {
        try {
            InputStream is = CompilerExtensionRegistry.loadResource(exampleFileLocation);
            Properties exampleDataProps = new Properties();

            if (is == null) {
                throw new FileNotFoundException("File not found at classpath location: "
                        + exampleFileLocation);
            }
            exampleDataProps.load(is);
            is.close();

            Enumeration<?> propertyNames = exampleDataProps.propertyNames();

            while (propertyNames.hasMoreElements()) {
                String typeName = (String) propertyNames.nextElement();
                String propertyValue = exampleDataProps.getProperty(typeName);

                if ((typeName == null) || typeName.equals(NAMESPACE_PROPERTY)) {
                    this.namespace = propertyValue;

                } else if (propertyValue != null) {
                    String[] valueArray = propertyValue.split(",");

                    if ((valueArray != null) && (valueArray.length > 0)) {
                        List<String> exampleValues = exampleTypeMappings.get(typeName);

                        if (exampleValues == null) {
                            exampleValues = new ArrayList<>();
                            exampleTypeMappings.put(typeName, exampleValues);
                        }
                        exampleValues.addAll(Arrays.asList(valueArray));
                    }
                }
            }

            if (this.namespace == null) {
                throw new IllegalArgumentException(
                        "No namespace specified in the EXAMPLE data file: " + exampleFileLocation);
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to load EXAMPLE data for legacy types from file: "
                            + exampleFileLocation);
        }
    }

}
