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
package org.komodo.rest.relational.response;

import java.net.URI;
import java.util.Properties;
import org.komodo.relational.vdb.Translator;
import org.komodo.rest.KomodoService;
import org.komodo.rest.RestBasicEntity;
import org.komodo.rest.RestLink;
import org.komodo.rest.RestLink.LinkType;
import org.komodo.rest.relational.KomodoRestUriBuilder.SettingNames;
import org.komodo.spi.KException;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.spi.lexicon.vdb.VdbLexicon;

/**
 * A translator that can be used by GSON to build a JSON document representation.
 *
 * <pre>
 * <code>
 * {
 *     "id" : "MyTranslator",
 *     "description" : "translator description goes here",
 *     "type" : "customType",
 *     "properties" : [
 *         "green" : "lantern",
 *         "captain" : "america",
 *         "black" : "widow"
 *     ]
 * }
 * </code>
 * </pre>
 */
public class RestVdbTranslator extends RestBasicEntity {

    /**
     * Label used to describe description
     */
    public static final String DESCRIPTION_LABEL = KomodoService.protectPrefix(VdbLexicon.Translator.DESCRIPTION);

    /**
     * Label used to describe type
     */
    public static final String TYPE_LABEL = KomodoService.protectPrefix(VdbLexicon.Translator.TYPE);

    /**
     * An empty array of translators.
     */
    public static final RestVdbTranslator[] NO_TRANSLATORS = new RestVdbTranslator[ 0 ];

    /**
     * Constructor for use <strong>only</strong> when deserializing.
     */
    public RestVdbTranslator() {
        // nothing to do
    }

    /**
     * Constructor for those translators needing more control over what basic properties
     * should be set
     *
     * @param baseUri
     * @throws KException
     */
    public RestVdbTranslator(URI baseUri) throws KException {
        super(baseUri);
    }

    /**
     * Constructor for use when serializing.
     * @param baseUri the base uri of the REST request
     * @param translator the translator
     * @param uow the transaction
     * @throws KException if error occurs
     */
    public RestVdbTranslator(URI baseUri, Translator translator, UnitOfWork uow) throws KException {
        super(baseUri, translator, uow, false);

        setDescription(translator.getDescription(uow));
        setType(translator.getType(uow));

        addExecutionProperties(uow, translator);

        Properties settings = getUriBuilder().createSettings(SettingNames.TRANSLATOR_NAME, getId());
        URI parentUri = getUriBuilder().vdbTranslatorParentUri(translator, uow);
        getUriBuilder().addSetting(settings, SettingNames.PARENT_PATH, parentUri);
        
        // VdbTranslators segment is added for Translators in a VDB
        KomodoObject parentObject = translator.getParent(uow);
        if(parentObject!=null && VdbLexicon.Vdb.VIRTUAL_DATABASE.equals(parentObject.getPrimaryType(uow).getName())) {
            getUriBuilder().addSetting(settings, SettingNames.ADD_TRANSLATORS_SEGMENT, "true"); //$NON-NLS-1$
        }

        addLink(new RestLink(LinkType.SELF, getUriBuilder().vdbTranslatorUri(LinkType.SELF, settings)));
        addLink(new RestLink(LinkType.PARENT, getUriBuilder().vdbTranslatorUri(LinkType.PARENT, settings)));
        createChildLink();
    }

    /**
     * @return the description (can be empty)
     */
    public String getDescription() {
        Object description = tuples.get(DESCRIPTION_LABEL);
        return description != null ? description.toString() : null;
    }

    /**
     * @param newDescription
     *        the new description (can be empty)
     */
    public void setDescription( final String newDescription ) {
        tuples.put(DESCRIPTION_LABEL, newDescription);
    }

    /**
     * @return the translator type (can be empty)
     */
    public String getType() {
        Object type = tuples.get(TYPE_LABEL);
        return type != null ? type.toString() : null;
    }

    /**
     * @param newType
     *        the new translator type (can be empty)
     */
    public void setType( final String newType ) {
        tuples.put(TYPE_LABEL, newType);
    }
}