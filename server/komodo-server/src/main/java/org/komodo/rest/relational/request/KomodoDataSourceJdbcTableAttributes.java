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
package org.komodo.rest.relational.request;

import javax.ws.rs.core.MediaType;

import org.komodo.rest.KRestEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;


/**
 * Object to be serialised by GSON that encapsulates properties for JDBC table request
 */
@JsonSerialize
@JsonInclude(value=Include.NON_NULL)
public class KomodoDataSourceJdbcTableAttributes implements KRestEntity {

    /**
     * Label for the DataSource name
     */
    public static final String DATA_SOURCE_NAME_LABEL = "dataSourceName"; //$NON-NLS-1$

    /**
     * Label for the Catalog filter
     */
    public static final String CATALOG_FILTER_LABEL = "catalogFilter"; //$NON-NLS-1$

    /**
     * Label for the Schema filter
     */
    public static final String SCHEMA_FILTER_LABEL = "schemaFilter"; //$NON-NLS-1$

    /**
     * Label for the table filter
     */
    public static final String TABLE_FILTER_LABEL = "tableFilter"; //$NON-NLS-1$

    @JsonProperty(DATA_SOURCE_NAME_LABEL)
    private String dataSourceName;

    @JsonProperty(CATALOG_FILTER_LABEL)
    private String catalogFilter;

    @JsonProperty(SCHEMA_FILTER_LABEL)
    private String schemaFilter;

    @JsonProperty(TABLE_FILTER_LABEL)
    private String tableFilter;

    /**
     * Default constructor for deserialization
     */
    public KomodoDataSourceJdbcTableAttributes() {
        // do nothing
    }

    @Override
    @JsonIgnore
    public boolean supports(MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
    }

    @Override
    @JsonIgnore
    public Object getXml() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return dataSourceName
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * @param dataSourceName the DataSource name
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * @return catalogFilter
     */
    public String getCatalogFilter() {
        return catalogFilter;
    }

    /**
     * @param catalogFilter the catalog filter
     */
    public void setCatalogFilter(String catalogFilter) {
        this.catalogFilter = catalogFilter;
    }

    /**
     * @return schemaFilter
     */
    public String getSchemaFilter() {
        return schemaFilter;
    }

    /**
     * @param schemaFilter the Schema Filter
     */
    public void setSchemaFilter(String schemaFilter) {
        this.schemaFilter = schemaFilter;
    }

    /**
     * @return tableFilter
     */
    public String getTableFilter() {
        return tableFilter;
    }

    /**
     * @param tableFilter the Table Filter
     */
    public void setTableFilter(String tableFilter) {
        this.tableFilter = tableFilter;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataSourceName == null) ? 0 : dataSourceName.hashCode());
        result = prime * result + ((catalogFilter == null) ? 0 : catalogFilter.hashCode());
        result = prime * result + ((schemaFilter == null) ? 0 : schemaFilter.hashCode());
        result = prime * result + ((tableFilter == null) ? 0 : tableFilter.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        KomodoDataSourceJdbcTableAttributes other = (KomodoDataSourceJdbcTableAttributes)obj;
        if (dataSourceName == null) {
            if (other.dataSourceName != null)
                return false;
        } else if (!dataSourceName.equals(other.dataSourceName))
            return false;
        if (catalogFilter == null) {
            if (other.catalogFilter != null)
                return false;
        } else if (!catalogFilter.equals(other.catalogFilter))
            return false;
        if (schemaFilter == null) {
            if (other.schemaFilter != null)
                return false;
        } else if (!schemaFilter.equals(other.schemaFilter))
            return false;
        if (tableFilter == null) {
            if (other.tableFilter != null)
                return false;
        } else if (!tableFilter.equals(other.tableFilter))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "KomodoVdbUpdateAttributes [dataSourceName=" + dataSourceName + ", catalogFilter=" + catalogFilter + ", schemaFilter=" + schemaFilter + ", tableFilter=" + tableFilter + "]";
    }
}
