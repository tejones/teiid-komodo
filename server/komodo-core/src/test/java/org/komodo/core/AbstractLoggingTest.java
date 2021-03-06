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
package org.komodo.core;

import java.nio.file.Path;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.komodo.spi.constants.StringConstants;
import org.komodo.utils.FileUtils;
import org.komodo.utils.TestKLog;

/**
 * Configures the logging for tests and most importantly aims
 * to reduce the modeshape logging from DEBUG to INFO.
 */
//@SuppressWarnings( {"nls", "javadoc"} )
public abstract class AbstractLoggingTest implements StringConstants {

    protected static final String TEST_USER = "user";

    private static Path _dataDirectory;

    protected static Path getLoggingDirectory() {
        return _dataDirectory;
    }

    @BeforeClass
    public static void initLogging() throws Exception {
        // create data directory for engine logging
    	_dataDirectory = TestKLog.createEngineDirectory();
    }

    @AfterClass
    public static void shutdown() throws Exception {
        FileUtils.removeDirectoryAndChildren( _dataDirectory.toFile() );
    }
}

