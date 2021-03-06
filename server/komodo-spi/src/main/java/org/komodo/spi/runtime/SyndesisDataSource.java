/*************************************************************************************
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
package org.komodo.spi.runtime;

/**
 * Syndesis based Data source that are available
 *
 */
public interface SyndesisDataSource {
    /**
     * @return syndesis connection id of data source
     */
    String getId();
    
    /**
     * @return real name of data source, maybe different from display name
     */
    String getName();

    /**
     * Returns the data source type name
     * 
     * @return the type
     */
    String getType();
    
    /**
     * true if the data source is already bound to the current project.
     * @return
     */
    boolean isBound();
    
    /**
     * Returns the matching translator type
     * @return translator name
     */
    String getTranslatorName();
}
