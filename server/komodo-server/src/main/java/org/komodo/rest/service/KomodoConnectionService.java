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
package org.komodo.rest.service;

import static org.komodo.rest.relational.RelationalMessages.Error.CONNECTION_SERVICE_NAME_EXISTS;
import static org.komodo.rest.relational.RelationalMessages.Error.CONNECTION_SERVICE_NAME_VALIDATION_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.VDB_DATA_SOURCE_NAME_EXISTS;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.komodo.core.repository.ObjectImpl;
import org.komodo.datasources.DefaultSyndesisDataSource;
import org.komodo.openshift.TeiidOpenShiftClient;
import org.komodo.relational.connection.Connection;
import org.komodo.relational.model.Model;
import org.komodo.relational.vdb.Vdb;
import org.komodo.relational.workspace.WorkspaceManager;
import org.komodo.rest.KomodoRestException;
import org.komodo.rest.KomodoRestV1Application.V1Constants;
import org.komodo.rest.KomodoService;
import org.komodo.rest.relational.KomodoProperties;
import org.komodo.rest.relational.RelationalMessages;
import org.komodo.rest.relational.connection.RestConnection;
import org.komodo.rest.relational.json.KomodoJsonMarshaller;
import org.komodo.rest.relational.request.KomodoConnectionAttributes;
import org.komodo.rest.relational.response.KomodoStatusObject;
import org.komodo.rest.relational.response.RestConnectionSummary;
import org.komodo.rest.relational.response.metadata.RestMetadataConnectionStatus;
import org.komodo.rest.relational.response.metadata.RestMetadataConnectionStatus.EntityState;
import org.komodo.spi.KException;
import org.komodo.spi.constants.StringConstants;
import org.komodo.spi.lexicon.datavirt.DataVirtLexicon;
import org.komodo.spi.lexicon.vdb.VdbLexicon;
import org.komodo.spi.metadata.MetadataInstance;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.Repository;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.spi.repository.Repository.UnitOfWork.State;
import org.komodo.spi.runtime.SyndesisDataSource;
import org.komodo.spi.runtime.TeiidDataSource;
import org.komodo.spi.runtime.TeiidVdb;
import org.komodo.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * A Komodo REST service for obtaining Connection information from the workspace.
 */
@Component
@Path(V1Constants.WORKSPACE_SEGMENT + StringConstants.FORWARD_SLASH +
           V1Constants.CONNECTIONS_SEGMENT)
@Api(tags = {V1Constants.CONNECTIONS_SEGMENT})
public final class KomodoConnectionService extends KomodoService {

	private interface OptionalParam {
        /**
         * Indicates if schema statuses should be returned. Defaults to <code>false</code>.
         */
        String INCLUDE_SCHEMA_STATUS = "include-schema-status"; //$NON-NLS-1$

        /**
         * Indicates if workspace connection should be included. Defaults to <code>true</code>.
         */
        String INCLUDE_CONNECTION = "include-connection"; //$NON-NLS-1$
    }
	@Autowired
	private TeiidOpenShiftClient openshiftClient;
    private static final String CONNECTION_VDB_PATTERN = "{0}btlconn"; //$NON-NLS-1$

    private static final String SCHEMA_MODEL_NAME_PATTERN = "{0}schemamodel"; //$NON-NLS-1$
    private static final String SCHEMA_VDB_NAME_PATTERN = "{0}schemavdb"; //$NON-NLS-1$

    /**
     * Get connection summaries from the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @return a JSON document representing the summaries of the requested connections in the Komodo workspace (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem constructing the Connection JSON document
     */
    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Return the summaries of the requested connections",
                  response = RestConnectionSummary[].class)
    @ApiImplicitParams({
    	@ApiImplicitParam(
    			name = OptionalParam.INCLUDE_CONNECTION,
    			value = "Include connections in result.  If not present, connections are returned.",
    			required = false,
    			dataType = "boolean",
    			paramType = "query"),
    	@ApiImplicitParam(
    			name = OptionalParam.INCLUDE_SCHEMA_STATUS,
    			value = "Include statuses in result. If not present, status are not returned.",
    			required = false,
    			dataType = "boolean",
    			paramType = "query"),
    	@ApiImplicitParam(
    			name = QueryParamKeys.PATTERN,
    			value = "A regex expression used when searching. If not present, all objects are returned.",
    			required = false,
    			dataType = "string",
    			paramType = "query"),
    	@ApiImplicitParam(
    			name = QueryParamKeys.SIZE,
    			value = "The number of objects to return. If not present, all objects are returned",
    			required = false,
    			dataType = "integer",
    			paramType = "query"),
    	@ApiImplicitParam(
    			name = QueryParamKeys.START,
    			value = "Index of the first artficact to return",
    			required = false,
    			dataType = "integer",
    			paramType = "query")
      })
    @ApiResponses(value = {
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getConnections( final @Context HttpHeaders headers,
                                    final @Context UriInfo uriInfo ) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;
        boolean includeSchemaStatus = false;
        boolean includeConnection = true;
        final List< RestConnectionSummary > summaries = new ArrayList<>();

        try {
            { // include-schema-status query parameter
                final String param = uriInfo.getQueryParameters().getFirst( OptionalParam.INCLUDE_SCHEMA_STATUS );

                if ( param != null ) {
                    includeSchemaStatus = Boolean.parseBoolean( param );
                }
            }

            { // include-connection query parameter
                final String param = uriInfo.getQueryParameters().getFirst( OptionalParam.INCLUDE_CONNECTION );

                if ( param != null ) {
                	includeConnection = Boolean.parseBoolean( param );
                }
            }

            final String searchPattern = uriInfo.getQueryParameters().getFirst( QueryParamKeys.PATTERN );
            includeConnection = includeConnection || !StringUtils.isBlank( searchPattern ); // assume if search pattern exists that connections should be returned

            // find connections
            final String txId = "getConnections?includeSchemaStatus=" + includeSchemaStatus + "&includeConnection=" + includeConnection; //$NON-NLS-1$ //$NON-NLS-2$
            uow = createTransaction(principal, txId, true );

            final Collection< TeiidVdb > vdbs = includeSchemaStatus ? getMetadataInstance().getVdbs() : null;
            Connection[] connections = null;

            if ( includeConnection ) {
	            if ( StringUtils.isBlank( searchPattern ) ) {
	                connections = getWorkspaceManager(uow).findConnections( uow );
	                LOGGER.debug( "getConnections:found '{0}' Connections", connections.length ); //$NON-NLS-1$
	            } else {
	                final String[] connectionPaths = getWorkspaceManager(uow).findByType( uow, DataVirtLexicon.Connection.NODE_TYPE, null, searchPattern, false );

	                if ( connectionPaths.length == 0 ) {
	                    connections = Connection.NO_CONNECTIONS;
	                } else {
	                    connections = new Connection[ connectionPaths.length ];
	                    int i = 0;

	                    for ( final String path : connectionPaths ) {
	                        connections[ i++ ] = getWorkspaceManager(uow).resolve( uow, new ObjectImpl( getWorkspaceManager(uow).getRepository(), path, 0 ), Connection.class );
	                    }

	                    LOGGER.debug( "getConnections:found '{0}' Connections using pattern '{1}'", connections.length, searchPattern ); //$NON-NLS-1$
	                }
	            }

	            int start = 0;

	            { // start query parameter
	                final String qparam = uriInfo.getQueryParameters().getFirst( QueryParamKeys.START );

	                if ( qparam != null ) {

	                    try {
	                        start = Integer.parseInt( qparam );

	                        if ( start < 0 ) {
	                            start = 0;
	                        }
	                    } catch ( final Exception e ) {
	                        start = 0;
	                    }
	                }
	            }

	            int size = KomodoService.ALL_AVAILABLE;

	            { // size query parameter
	                final String qparam = uriInfo.getQueryParameters().getFirst( QueryParamKeys.SIZE );

	                if ( qparam != null ) {

	                    try {
	                        size = Integer.parseInt( qparam );

	                        if ( size <= 0 ) {
	                            size = KomodoService.ALL_AVAILABLE;
	                        }
	                    } catch ( final Exception e ) {
	                        size = KomodoService.ALL_AVAILABLE;
	                    }
	                }
	            }

	            int i = 0;

	            KomodoProperties properties = new KomodoProperties();
	            for ( final Connection connection : connections ) {
	                if ( ( start == 0 ) || ( i >= start ) ) {
	                	RestConnection restConnection = null;
	                	RestMetadataConnectionStatus restStatus = null;

	                	if ( ( size == KomodoService.ALL_AVAILABLE ) || ( summaries.size() < size ) ) {
	                        restConnection = entityFactory.create(connection, uriInfo.getBaseUri(), uow, properties);
	                        LOGGER.debug("getConnections:Connection '{0}' entity was constructed", connection.getName(uow)); //$NON-NLS-1$

	                        if ( includeSchemaStatus ) {
	                           	restStatus = createStatusRestEntity( uow, vdbs, connection );
	                        }

	                        summaries.add( new RestConnectionSummary( uriInfo.getBaseUri(), restConnection, restStatus ) );
	                	} else {
	                        break;
	                    }
	                }

	                ++i;
	            }
            } else if ( includeSchemaStatus ) { // include schema status and no connections
            	connections = getWorkspaceManager(uow).findConnections( uow );

                for ( final Connection connection: connections ) {
                    final RestMetadataConnectionStatus restStatus = createStatusRestEntity( uow, vdbs, connection );
                    summaries.add( new RestConnectionSummary( uriInfo.getBaseUri(), null, restStatus ) );
                }
            }

            return commit( uow, mediaTypes, summaries );
        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.CONNECTION_SERVICE_GET_CONNECTIONS_ERROR);
        }
    }

    private TeiidVdb findDeployedVdb( final String connectionName ) throws KException {
        final String connectionVdbName = getConnectionWorkspaceVdbName( connectionName );
        return getMetadataInstance().getVdb( connectionVdbName );
    }

    private String getConnectionWorkspaceVdbName( final String connectionName ) {
        return MessageFormat.format( CONNECTION_VDB_PATTERN, connectionName.toLowerCase() );
    }

    private String getSchemaModelName( final String connectionName ) {
        return MessageFormat.format( SCHEMA_MODEL_NAME_PATTERN, connectionName.toLowerCase() );
    }

    private String getSchemaVdbName( final String connectionName ) {
        return MessageFormat.format( SCHEMA_VDB_NAME_PATTERN, connectionName.toLowerCase() );
    }

    private RestMetadataConnectionStatus createStatusRestEntity( final UnitOfWork uow,
                                                                 final Collection< TeiidVdb > vdbs,
                                                                 final Connection connection ) throws Exception {
        final String connectionName = connection.getName( uow );
        final String connVdbName = getConnectionWorkspaceVdbName( connectionName );

        // find status of server connection VDB
        TeiidVdb connVdb = null;

        for ( final TeiidVdb vdb : vdbs ) {
            if ( vdb.getName().equals( connVdbName ) ) {
                connVdb = vdb;
                break;
            }
        }

        if ( connVdb == null ) {
            return new RestMetadataConnectionStatus( connectionName );
        }

        // now find status of workspace schema
        final RestMetadataConnectionStatus restStatus = new RestMetadataConnectionStatus( connectionName, connVdb );
        final WorkspaceManager wkspMgr = getWorkspaceManager( uow );

        // the schema VDB is a child of the connection
        final String schemaVdbName = getSchemaVdbName( connectionName );
        final KomodoObject[] workspaceVdbs = connection.getChildrenOfType( uow,
                                                                           VdbLexicon.Vdb.VIRTUAL_DATABASE,
                                                                           schemaVdbName );

        if ( workspaceVdbs.length != 0 ) {
            final Vdb vdb = wkspMgr.resolve( uow, workspaceVdbs[ 0 ], Vdb.class );
            restStatus.setSchemaVdbName( schemaVdbName );

            // there should be one model
            final String schemaModelName = getSchemaModelName( connectionName );
            final Model[] models = vdb.getModels( uow, schemaModelName );

            if ( models.length > 0 ) {
                final Model schemaModel = models[ 0 ];
                restStatus.setSchemaModelName( schemaModelName );

                // if model has children the DDL has been sequenced
                if ( schemaModel.hasChildren( uow ) ) {
                    // assume sequencer ran successfully
                    restStatus.setSchemaState( EntityState.ACTIVE );
                } else if ( schemaModel.hasProperty( uow, VdbLexicon.Model.MODEL_DEFINITION ) ) {
                    // assume sequencer is running but could have failed
                    restStatus.setSchemaState( EntityState.LOADING );
                }
            } else {
                // Since VDB and model are created in the same transaction this should never happen.
                // Would be nice to be able to get here if we can detect the DDL sequencing failed.
                restStatus.setSchemaState( EntityState.FAILED );
            }
        }

        return restStatus;
    }

    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param connectionName
     *        the id of the connection whose summary is being retrieved (cannot be empty)
     * @return a JSON document representing the summary of the requested connection in the Komodo workspace (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem finding the specified workspace Connection or constructing the JSON representation
     */
    @GET
    @Path( V1Constants.CONNECTION_PLACEHOLDER )
    @Produces( { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML } )
    @ApiOperation(value = "Find connection by name", response = RestConnectionSummary.class)
    @ApiImplicitParams({
    	@ApiImplicitParam(
    			name = OptionalParam.INCLUDE_CONNECTION,
    			value = "Include connections in result.  If not present, connections are returned.",
    			required = false,
    			dataType = "boolean",
    			paramType = "query"),
    	@ApiImplicitParam(
    			name = OptionalParam.INCLUDE_SCHEMA_STATUS,
    			value = "Include statuses in result. If not present, status are not returned.",
    			required = false,
    			dataType = "boolean",
    			paramType = "query"),
      })
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No Connection could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON or XML is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getConnection( final @Context HttpHeaders headers,
                                   final @Context UriInfo uriInfo,
                                   @ApiParam(
                                             value = "Name of the connection",
                                             required = true
                                   )
                                   final @PathParam( "connectionName" ) String connectionName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;
        boolean includeSchemaStatus = false;
        boolean includeConnection = true;

        try {
            { // include-schema-status query parameter
                final String param = uriInfo.getQueryParameters().getFirst( OptionalParam.INCLUDE_SCHEMA_STATUS );

                if ( param != null ) {
                    includeSchemaStatus = Boolean.parseBoolean( param );
                }
            }

            { // include-connection query parameter
                final String param = uriInfo.getQueryParameters().getFirst( OptionalParam.INCLUDE_CONNECTION );

                if ( param != null ) {
                	includeConnection = Boolean.parseBoolean( param );
                }
            }

            final String txId = "getConnection?includeSchemaStatus=" + includeSchemaStatus + "&includeConnection=" + includeConnection; //$NON-NLS-1$ //$NON-NLS-2$
            uow = createTransaction(principal, txId, true );

            Connection connection = findConnection(uow, connectionName);
            if (connection == null)
                return commitNoConnectionFound(uow, mediaTypes, connectionName);

        	RestConnection restConnection = null;
        	RestMetadataConnectionStatus restStatus = null;

        	if ( includeConnection ) {
	        	KomodoProperties properties = new KomodoProperties();
	            restConnection = entityFactory.create(connection, uriInfo.getBaseUri(), uow, properties);
	            LOGGER.debug("getConnection:Connection '{0}' entity was constructed", connection.getName(uow)); //$NON-NLS-1$
        	}

        	if ( includeSchemaStatus ) {
        		restStatus = createStatusRestEntity( uow, getMetadataInstance().getVdbs(), connection );
	            LOGGER.debug("getConnection:Connection '{0}' status entity was constructed", connection.getName(uow)); //$NON-NLS-1$
        	}

        	final RestConnectionSummary summary = new RestConnectionSummary( uriInfo.getBaseUri(), restConnection, restStatus );
        	return commit( uow, mediaTypes, summary );

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.CONNECTION_SERVICE_GET_CONNECTION_ERROR, connectionName);
        }
    }

    /**
     * Create a new Connection in the komodo repository, using a service catalogSource
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param connectionName
     *        the connection name (cannot be empty)
     * @param connectionJson
     *        the connection JSON representation (cannot be <code>null</code>)
     * @return a JSON representation of the new connection (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error creating the Connection
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.CONNECTION_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Create a connection in the workspace, using ServiceCatalogSource")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response createConnection( final @Context HttpHeaders headers,
                                      final @Context UriInfo uriInfo,
                                      @ApiParam(
                                              value = "Name of the connection",
                                              required = true
                                      )
                                      final @PathParam( "connectionName" ) String connectionName,
                                      @ApiParam(
                                              value = "" +
                                                      "Properties for the new connection:<br>" +
                                                      OPEN_PRE_TAG +
                                                      OPEN_BRACE + BR +
                                                      NBSP + "description: \"description for the connection\"" + COMMA + BR +
                                                      NBSP + "serviceCatalogSource: \"serviceCatalog source for the connection\"" + BR +
                                                      CLOSE_BRACE +
                                                      CLOSE_PRE_TAG,
                                              required = true
                                      )
                                      final String connectionJson) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Error if the connection name is missing
        if (StringUtils.isBlank( connectionName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_MISSING_CONNECTION_NAME);
        }

        // Get the attributes - ensure valid attributes provided
        KomodoConnectionAttributes rcAttr;
        try {
        	rcAttr = KomodoJsonMarshaller.unmarshall(connectionJson, KomodoConnectionAttributes.class);

            Response response = checkConnectionAttributes(rcAttr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.CONNECTION_SERVICE_REQUEST_PARSING_ERROR);
        }

        SyndesisDataSource serviceCatalogSource = null;

        RestConnection restConnection = new RestConnection();
        restConnection.setId(connectionName);

        try {
            // Add properties for the description and serviceCatalogSource
            restConnection.addProperty("description", rcAttr.getDescription()); //$NON-NLS-1$
            restConnection.addProperty(DataVirtLexicon.Connection.SERVICE_CATALOG_SOURCE, rcAttr.getDataSource());
            restConnection.setJdbc(true);

            // Get the specified ServiceCatalogDataSource from the metadata instance
            Collection<DefaultSyndesisDataSource> dataSources = openshiftClient.getSyndesisSources(getAuthenticationToken());
			for(SyndesisDataSource ds: dataSources) {
				if(ds.getName().equals(rcAttr.getDataSource())) {
					serviceCatalogSource = ds;
					break;
				}
			}
			// If catalogSource is not found, exit with error
			if (serviceCatalogSource == null) {
				return createErrorResponseWithForbidden(mediaTypes,
						RelationalMessages.Error.CONNECTION_SERVICE_CATALOG_SOURCE_DNE_ERROR);
			}
        } catch (Exception ex) {
            throw new KomodoRestException(ex);
        }

        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "createConnection", false ); //$NON-NLS-1$

            // Error if the repo already contains a connection with the supplied name.
            if ( getWorkspaceManager(uow).hasChild( uow, connectionName ) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_CREATE_ALREADY_EXISTS);
            }

			// Ensures service catalog is bound, and creates the corresponding datasource in wildfly
			openshiftClient.bindToSyndesisSource(getAuthenticationToken(), serviceCatalogSource.getName());

			// Get the connection from the wildfly instance (should be available after binding)
            TeiidDataSource dataSource = getMetadataInstance().getDataSource(serviceCatalogSource.getName());
            if (dataSource == null)
                return commitNoConnectionFound(uow, mediaTypes, connectionName);

            // Add the jndi and driver to the komodo connection to be created
            restConnection.setJndiName(dataSource.getJndiName());
            restConnection.setDriverName(dataSource.getType());

            // create new Connection
            return doAddConnection( uow, uriInfo.getBaseUri(), mediaTypes, restConnection );

        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.CONNECTION_SERVICE_CREATE_CONNECTION_ERROR, connectionName);
        }
    }

    /**
     * Clone a Connection in the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param connectionName
     *        the connection name (cannot be empty)
     * @param newConnectionName
     *        the new connection name (cannot be empty)
     * @return a JSON representation of the new connection (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error creating the Connection
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.CLONE_SEGMENT + StringConstants.FORWARD_SLASH + V1Constants.CONNECTION_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Clone a connection in the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response cloneConnection( final @Context HttpHeaders headers,
                                     final @Context UriInfo uriInfo,
                                     @ApiParam(
                                               value = "Name of the connection",
                                               required = true
                                     )
                                     final @PathParam( "connectionName" ) String connectionName,
                                     @ApiParam(
                                               value = "The new name of the connection",
                                               required = true
                                     )
                                     final String newConnectionName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Error if the connection name is missing
        if (StringUtils.isBlank( connectionName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_MISSING_CONNECTION_NAME);
        }

        // Error if the new connection name is missing
        if ( StringUtils.isBlank( newConnectionName ) ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_CLONE_MISSING_NEW_NAME);
        }

        // Error if the name parameter and new name are the same
        final boolean namesMatch = connectionName.equals( newConnectionName );
        if ( namesMatch ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_CLONE_SAME_NAME_ERROR, newConnectionName);
        }

        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "cloneConnection", false ); //$NON-NLS-1$

            // Error if the repo already contains a connection with the supplied name.
            if ( getWorkspaceManager(uow).hasChild( uow, newConnectionName ) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_CLONE_ALREADY_EXISTS);
            }

            // create new Connection
            // must be an update
            final KomodoObject kobject = getWorkspaceManager(uow).getChild( uow, connectionName, DataVirtLexicon.Connection.NODE_TYPE );
            final Connection oldConnection = getWorkspaceManager(uow).resolve( uow, kobject, Connection.class );
            final RestConnection oldEntity = entityFactory.create(oldConnection, uriInfo.getBaseUri(), uow );

            final Connection connection = getWorkspaceManager(uow).createConnection( uow, null, newConnectionName);

            setProperties( uow, connection, oldEntity );

            final RestConnection entity = entityFactory.create(connection, uriInfo.getBaseUri(), uow );
            final Response response = commit( uow, mediaTypes, entity );
            return response;
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.CONNECTION_SERVICE_CLONE_CONNECTION_ERROR, connectionName);
        }
    }

    /**
     * Update a Connection in the komodo repository, using service catalog source
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param connectionName
     *        the connection name (cannot be empty)
     * @param connectionJson
     *        the connection JSON representation (cannot be <code>null</code>)
     * @return a JSON representation of the updated connection (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error updating the VDB
     */
    @PUT
    @Path( StringConstants.FORWARD_SLASH + V1Constants.CONNECTION_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Update a connection in the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response updateConnection( final @Context HttpHeaders headers,
                                      final @Context UriInfo uriInfo,
                                      @ApiParam(
                                                value = "Name of the connection",
                                                required = true
                                      )
                                      final @PathParam( "connectionName" ) String connectionName,
                                      @ApiParam(
                                                value = "" +
                                                        "Properties for the connection update:<br>" +
                                                        OPEN_PRE_TAG +
                                                        OPEN_BRACE + BR +
                                                        NBSP + "description: \"description for the connection\"" + COMMA + BR +
                                                        NBSP + "serviceCatalogSource: \"serviceCatalog source for the connection\"" + BR +
                                                        CLOSE_BRACE +
                                                        CLOSE_PRE_TAG,
                                                required = true
                                      )
                                      final String connectionJson) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Error if the connection name is missing
        if (StringUtils.isBlank( connectionName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_MISSING_CONNECTION_NAME);
        }

        // Get the attributes - ensure valid attributes provided
        KomodoConnectionAttributes rcAttr;
        try {
        	rcAttr = KomodoJsonMarshaller.unmarshall(connectionJson, KomodoConnectionAttributes.class);

            Response response = checkConnectionAttributes(rcAttr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.CONNECTION_SERVICE_REQUEST_PARSING_ERROR);
        }

        SyndesisDataSource serviceCatalogSource = null;

        RestConnection restConnection = new RestConnection();
        restConnection.setId(connectionName);

        try {
            // Add properties for the description and serviceCatalogSource
            restConnection.addProperty("description", rcAttr.getDescription()); //$NON-NLS-1$
            restConnection.addProperty(DataVirtLexicon.Connection.SERVICE_CATALOG_SOURCE, rcAttr.getDataSource());
            restConnection.setJdbc(true);

            // Get the specified ServiceCatalogDataSource from the metadata instance
            Collection<DefaultSyndesisDataSource> dataSources = openshiftClient.getSyndesisSources(getAuthenticationToken());
			for(SyndesisDataSource ds: dataSources) {
				if(ds.getName().equals(rcAttr.getDataSource())) {
					serviceCatalogSource = ds;
					break;
				}
			}
			// If catalogSource is not found, exit with error
			if(serviceCatalogSource == null) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_CATALOG_SOURCE_DNE_ERROR);
			}
        } catch (Exception ex) {
            throw new KomodoRestException(ex);
        }

        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "updateConnection", false ); //$NON-NLS-1$

            final boolean exists = getWorkspaceManager(uow).hasChild( uow, connectionName );
            // Error if the specified connection does not exist
            if ( !exists ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_UPDATE_CONNECTION_DNE);
            }

            // Update deletes the existing connection and recreates it.
            final KomodoObject kobject = getWorkspaceManager(uow).getChild( uow, connectionName, DataVirtLexicon.Connection.NODE_TYPE );
            getWorkspaceManager(uow).delete(uow, kobject);

			// Ensures service catalog is bound, and creates the corresponding datasource in wildfly
			openshiftClient.bindToSyndesisSource(getAuthenticationToken(), serviceCatalogSource.getName());

			// Get the connection from the wildfly instance (should be available after binding)
            TeiidDataSource dataSource = getMetadataInstance().getDataSource(serviceCatalogSource.getName());
            if (dataSource == null)
                return commitNoConnectionFound(uow, mediaTypes, connectionName);

            // Add the jndi and driver to the komodo connection to be created
            restConnection.setJndiName(dataSource.getJndiName());
            restConnection.setDriverName(dataSource.getType());

            // Create the connection
            Response response = doAddConnection( uow, uriInfo.getBaseUri(), mediaTypes, restConnection );

            LOGGER.debug("updateConnection: connection '{0}' entity was updated", connectionName); //$NON-NLS-1$

            return response;
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.CONNECTION_SERVICE_UPDATE_CONNECTION_ERROR, connectionName);
        }
    }

    private Response doAddConnection( final UnitOfWork uow,
                                      final URI baseUri,
                                      final List<MediaType> mediaTypes,
                                      final RestConnection restConnection ) throws KomodoRestException {
        assert( !uow.isRollbackOnly() );
        assert( uow.getState() == State.NOT_STARTED );
        assert( restConnection != null );

        final String connectionName = restConnection.getId();
        try {
            final Connection connection = getWorkspaceManager(uow).createConnection( uow, null, connectionName);

            // Transfers the properties from the rest object to the created komodo service.
            setProperties(uow, connection, restConnection);

            final RestConnection entity = entityFactory.create(connection, baseUri, uow );
            final Response response = commit( uow, mediaTypes, entity );
            return response;
        } catch ( final Exception e ) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            throw new KomodoRestException( RelationalMessages.getString( RelationalMessages.Error.CONNECTION_SERVICE_CREATE_CONNECTION_ERROR, connectionName ), e );
        }
    }

    /**
     * Delete the specified Connection from the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param connectionName
     *        the name of the connection to remove (cannot be <code>null</code>)
     * @return a JSON document representing the results of the removal
     * @throws KomodoRestException
     *         if there is a problem performing the delete
     */
    @DELETE
    @Path("{connectionName}")
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Delete a connection from the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response deleteConnection( final @Context HttpHeaders headers,
                                      final @Context UriInfo uriInfo,
                                      @ApiParam(
                                                value = "Name of the connection",
                                                required = true
                                      )
                                      final @PathParam( "connectionName" ) String connectionName) throws KomodoRestException {
        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();

        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "removeConnectionFromWorkspace", false); //$NON-NLS-1$
            Repository repo = this.kengine.getDefaultRepository();
            final WorkspaceManager mgr = WorkspaceManager.getInstance( repo, uow );
            Connection connection = findConnection(uow, connectionName);

            if (connection == null)
                return Response.noContent().build();

            // get associated workspace vdb - remove if it exists
            final String connectionVdbName = getConnectionWorkspaceVdbName( connectionName );
            final Vdb connectionWorkspaceVdb = findVdb( uow, connectionVdbName );
            if (connectionWorkspaceVdb != null) {
                mgr.delete(uow, connectionWorkspaceVdb);
            }

            // get associated deployed vdb - undeploy if it exists
            final TeiidVdb deployedVdb = findDeployedVdb( connectionName );
            if (deployedVdb != null) {
                getMetadataInstance().undeployDynamicVdb(connectionVdbName);
            }

            // Delete the workspace connection
            mgr.delete(uow, connection);

            KomodoStatusObject kso = new KomodoStatusObject("Delete Status"); //$NON-NLS-1$
            if (mgr.hasChild(uow, connectionName))
                kso.addAttribute(connectionName, "Deletion failure"); //$NON-NLS-1$
            else
                kso.addAttribute(connectionName, "Successfully deleted"); //$NON-NLS-1$

            return commit(uow, mediaTypes, kso);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.CONNECTION_SERVICE_DELETE_CONNECTION_ERROR, connectionName);
        }
    }

    /**
     * @param headers
     *            the request headers (never <code>null</code>)
     * @param uriInfo
     *            the request URI information (never <code>null</code>)
     * @param connectionName
     *            the Connection name being validated (cannot be empty)
     * @return the response (never <code>null</code>) with an entity that is
     *         either an empty string, when the name is valid, or an error
     *         message
     * @throws KomodoRestException
     *             if there is a problem validating the name or constructing
     *             the response
     */
    @GET
    @Path( V1Constants.NAME_VALIDATION_SEGMENT + StringConstants.FORWARD_SLASH + V1Constants.CONNECTION_PLACEHOLDER )
    @Produces( { MediaType.TEXT_PLAIN } )
    @ApiOperation( value = "Returns an error message if the Connection name is invalid" )
    @ApiResponses( value = {
            @ApiResponse( code = 400, message = "The URI cannot contain encoded slashes or backslashes." ),
            @ApiResponse( code = 403, message = "An unexpected error has occurred." ),
            @ApiResponse( code = 500, message = "The Connection name cannot be empty." )
    } )
    public Response validateConnectionName( final @Context HttpHeaders headers,
                                     final @Context UriInfo uriInfo,
                                     @ApiParam( value = "The Connection name being checked", required = true )
                                     final @PathParam( "connectionName" ) String connectionName ) throws KomodoRestException {

        final SecurityPrincipal principal = checkSecurityContext( headers );

        if ( principal.hasErrorResponse() ) {
            return principal.getErrorResponse();
        }

        final String errorMsg = VALIDATOR.checkValidName( connectionName );

        // a name validation error occurred
        if ( errorMsg != null ) {
            return Response.ok().entity( errorMsg ).build();
        }

        UnitOfWork uow = null;

        try {
            uow = createTransaction( principal, "validateConnectionName", true ); //$NON-NLS-1$

            // make sure an existing Connection does not have that name
            final Connection connection = findConnection( uow, connectionName );

            if ( connection == null ) {
                // make sure an existing vdb does not have the same name
                final Vdb ds = findVdb( uow, connectionName );

                if ( ds == null ) {
                    // name is valid
                    return Response.ok().build();
                }

                // name is the same as an existing connection
                return Response.ok()
                               .entity( RelationalMessages.getString( VDB_DATA_SOURCE_NAME_EXISTS ) )
                               .build();
            }

            // name is the same as an existing connection
            return Response.ok()
                           .entity( RelationalMessages.getString( CONNECTION_SERVICE_NAME_EXISTS ) )
                           .build();
        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden( headers.getAcceptableMediaTypes(),
                                                     e,
                                                     CONNECTION_SERVICE_NAME_VALIDATION_ERROR );
        }
    }

    private synchronized MetadataInstance getMetadataInstance() throws KException {
        return this.kengine.getMetadataInstance();
    }

    /*
     * Checks the supplied attributes for create and update of connections
     *  - serviceCatalogSource is required
     *  - description is optional
     */
    private Response checkConnectionAttributes(KomodoConnectionAttributes attr,
                                               List<MediaType> mediaTypes) throws Exception {

        if ( attr == null || attr.getDataSource() == null ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.CONNECTION_SERVICE_MISSING_PARAMETER_ERROR);
        }

        return Response.ok().build();
    }
}