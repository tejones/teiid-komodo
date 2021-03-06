/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.komodo.datasources;

import java.util.Map;
import java.util.Properties;

public class TeiidDefinition extends DataSourceDefinition {

    @Override
    public String getType() {
        return "teiid";
    }

    @Override
    public String getPomDendencies() {
        return "";
    }

    @Override
    public String getTranslatorName() {
        return "teiid";
    }

    @Override
    public boolean isTypeOf(Map<String, String> properties) {
        if ((properties != null) && (properties.get("url") != null)
                && (properties.get("url").startsWith("jdbc:teiid:"))) {
            return true;
        }
        return false;
    }

    @Override
    public Properties getInternalTeiidDataSourceProperties(DefaultSyndesisDataSource source) {
        Properties props = new Properties();
        
        props.setProperty("jndi-name", source.getName());
        props.setProperty("driver-name", getType()); // used as translator name
        props.setProperty("display-name", source.getName());
        
        props.setProperty("url", source.getProperty("url"));
        props.setProperty("user", source.getProperty("user"));
        props.setProperty("password", source.getProperty("password"));
        props.setProperty("schema", source.getProperty("schema"));
        return props;
    }

    @Override
    public Properties getPublishedImageDataSourceProperties(DefaultSyndesisDataSource scd) {
        Properties props = new Properties();
        ds(props, scd, "jdbc-url", scd.getProperty("url"));
        ds(props, scd, "user", scd.getProperty("user"));
        ds(props, scd, "password", scd.getProperty("password"));
        
        if (scd.getProperty("schema") != null) {
        	ds(props, scd, "importer.schemaName", scd.getProperty("schema"));
        }

        // pool properties
        ds(props, scd, "maximumPoolSize", "5");
        ds(props, scd, "minimumIdle", "0");
        
        return props;
    }
}
