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
package org.komodo.rest.swagger;

import org.komodo.rest.relational.response.RestVdbModelSource;
import org.komodo.spi.repository.KomodoType;
import io.swagger.converter.ModelConverterContext;
import io.swagger.models.ModelImpl;

/**
 * Converter to display properties of {@link RestVdbModelSource} class in swagger
 */
public class RestVdbModelSourceConverter extends RestEntityConverter<RestVdbModelSource> {

    @Override
    protected Class<RestVdbModelSource> getEntityClass() {
        return RestVdbModelSource.class;
    }

    @Override
    protected KomodoType getKomodoType() {
        return KomodoType.VDB_MODEL_SOURCE;
    }

    @Override
    protected void addProperties(ModelImpl model, ModelConverterContext context) throws Exception {
        model.property(RestVdbModelSource.JNDI_NAME_LABEL, requiredProperty(String.class));
        model.property(RestVdbModelSource.TRANSLATOR_LABEL, property(String.class));
    }
}
