/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.api.service;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MeasureReport;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cohort.engine.api.service.model.MeasureEvaluation;
import com.ibm.cohort.engine.api.service.model.MeasureParameterInfo;
import com.ibm.cohort.engine.api.service.model.MeasureParameterInfoList;
import com.ibm.cohort.engine.api.service.model.ServiceErrorList;
import com.ibm.cohort.engine.measure.MeasureEvaluator;
import com.ibm.cohort.engine.measure.R4DataProviderFactory;
import com.ibm.cohort.engine.measure.ZipResourceResolutionProvider;
import com.ibm.cohort.engine.measure.cache.DefaultRetrieveCacheContext;
import com.ibm.cohort.engine.measure.cache.RetrieveCacheContext;
import com.ibm.cohort.engine.terminology.R4RestFhirTerminologyProvider;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilder;
import com.ibm.cohort.fhir.client.config.FhirClientBuilder;
import com.ibm.cohort.fhir.client.config.FhirClientBuilderFactory;
import com.ibm.cohort.fhir.client.config.FhirServerConfig;
import com.ibm.cohort.valueset.ValueSetArtifact;
import com.ibm.cohort.valueset.ValueSetUtil;
import com.ibm.watson.common.service.base.DarkFeatureSwaggerFilter;
import com.ibm.watson.common.service.base.ServiceBaseConstants;
import com.ibm.watson.common.service.base.ServiceBaseUtility;
import com.ibm.websphere.jaxrs20.multipart.IAttachment;
import com.ibm.websphere.jaxrs20.multipart.IMultipartBody;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.Extension;
import io.swagger.annotations.ExtensionProperty;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

/**
 * Cohort REST service
 *
 *
 */
@Path(CohortEngineRestConstants.SERVICE_MAJOR_VERSION)
@Api()
@SwaggerDefinition(
tags={	@Tag(name = "FHIR Measures", description = "IBM Cohort Engine FHIR Measure Information"),
		@Tag(name = "Measure Evaluation", description = "IBM Cohort Engine Measure Evaluation"),
		@Tag(name = "ValueSet", description = "IBM Cohort Engine ValueSet Operations")})
public class CohortEngineRestHandler {
	private static final String MEASURE_PART_DESC = "ZIP archive containing the FHIR Measure and Library resources used in the evaluation.";
	private static final String REQUEST_DATA_PART_DESC = "Metadata describing the evaluation to perform.";
	private static final String EVALUATION_API_NOTES = "The body of the request is a multipart/form-data request with an application/json attachment named 'request_data' that describes the measure evaluation that will be performed and an application/zip attachment named 'measure' that contains the measure and library artifacts to be evaluated. Valueset resources required for Measure evaluation must be loaded to the FHIR server in advance of an evaluation request. ";
	private static final Logger logger = LoggerFactory.getLogger(CohortEngineRestHandler.class.getName());
	private static final String MEASURE_IDENTIFIER_VALUE_DESC = "Used to identify the FHIR measure resource you would like the parameter information "
			+ "for using the Measure.Identifier.Value field.";
	private static final String MEASURE_ID_DESC = "FHIR measure resource id for the measure you would like the parameter information "
			+ "for using the Measure.id field.";
	private static final String MEASURE_IDENTIFIER_SYSTEM_DESC = "The system name used to provide a namespace for the measure identifier values. For "
			+ "example, if using social security numbers for the identifier values, one would use http://hl7.org/fhir/sid/us-ssn as the system value.";
	private static final String MEASURE_VERSION_DESC = " The version of the measure to retrieve as represented by the FHIR resource Measure.version "
			+ "field. If a value is not provided, the underlying code will atempt to resolve the most recent version assuming a "
			+ "<Major>.<Minor>.<Patch> format (ie if versions 1.0.0 and 2.0.0 both exist, the code will return the 2.0.0 version)";
	private static final String MEASURE_API_NOTES = "Retrieves the parameter information for libraries linked to by a measure";

	private static final String VALUE_SET_API_NOTES = "Uploads a value set described by the given xslx file";
	private static final String VALUE_SET_UPDATE_IF_EXISTS_DESC = "The parameter that, if true, will force updates of value sets if the value set already exists";
	private static final String VALUE_SET_DESC = "Spreadsheet containing the Value Set definition.";
	private static final String CUSTOM_CODE_SYSTEM_DESC = "A custom mapping of code systems to urls";

	public static final String DELAY_DEFAULT = "3";
	
	public static final String REQUEST_DATA_PART = "request_data";
	public static final String MEASURE_PART = "measure";
	public static final String FHIR_DATA_SERVER_CONFIG_PART = "fhir_data_server_config";
	public static final String UPDATE_IF_EXISTS_PARM = "update_if_exists";
	public static final String VERSION = "version";
	public static final String MEASURE_IDENTIFIER_VALUE = "measure_identifier_value";
	public static final String MEASURE_IDENTIFIER_SYSTEM = "measure_identifier_system";
	public static final String MEASURE_VERSION = "measure_version";
	public static final String MEASURE_ID = "measure_id";
	
	
	public final static String VALUE_SET_PART = "value_set";
	public final static String CUSTOM_CODE_SYSTEM = "custom_code_system";
	
	public static final String EXAMPLE_DATA_SERVER_CONFIG_JSON = "A configuration file containing information needed to connect to the FHIR server. "
			+ "See https://github.com/Alvearie/quality-measure-and-cohort-service/blob/main/docs/user-guide/getting-started.md#fhir-server-configuration for more details. \n"
			+ "Example Contents: \n <pre>{\n" + 
			"    \"@class\": \"com.ibm.cohort.fhir.client.config.IBMFhirServerConfig\",\n" + 
			"    \"endpoint\": \"https://fhir-internal.dev:9443/fhir-server/api/v4\",\n" + 
			"    \"user\": \"fhiruser\",\n" + 
			"    \"password\": \"replaceWithfhiruserPassword\",\n" + 
			"    \"logInfo\": [\n" + 
			"        \"ALL\"\n" + 
			"    ],\n" + 
			"    \"tenantId\": \"default\"\n" + 
			"}</pre>";
	
	public enum MethodNames {
		EVALUATE_MEASURE("evaluateMeasure"),
		GET_MEASURE_PARAMETERS("getMeasureParameters"),
		GET_MEASURE_PARAMETERS_BY_ID("getMeasureParametersById"),
		CREATE_VALUE_SET("createValueSet")
		;
		
		private String name;
		
		private MethodNames(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
	}

	static {
		// Long and expensive initialization should occur here or
		// delayed and stored in a static member.

		//logger.setLevel(Level.DEBUG); // TODO: Development time only
	}

	public CohortEngineRestHandler() {
		super();
		// Instance initialization here.
		// NOTE: This constructor is called on every REST call!
	}

	@POST
	@Path("/evaluation")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ MediaType.APPLICATION_JSON })
	@ApiOperation(value = "Evaluates a measure bundle for a single patient"
		, notes = EVALUATION_API_NOTES, response = String.class
		, tags = {"Measure Evaluation"}
		, nickname = "evaluate_measure"
		, extensions = {
				@Extension(properties = {
						@ExtensionProperty(
								name = DarkFeatureSwaggerFilter.DARK_FEATURE_NAME
								, value = CohortEngineRestConstants.DARK_LAUNCHED_MEASURE_EVALUATION)
				})
		}
	)
	@ApiImplicitParams({
		// This is necessary for the dark launch feature
		@ApiImplicitParam(access = DarkFeatureSwaggerFilter.DARK_FEATURE_CONTROLLED, paramType = "header", dataType = "string"),
		// These are necessary to create a proper view of the request body that is all wrapped up in the Liberty IMultipartBody parameter
		@ApiImplicitParam(name=REQUEST_DATA_PART, value=REQUEST_DATA_PART_DESC, dataTypeClass = MeasureEvaluation.class, required=true, paramType="form", type="file"),
		@ApiImplicitParam(name=MEASURE_PART, value=MEASURE_PART_DESC, dataTypeClass = File.class, required=true, paramType="form", type="file" )
	})
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successful Operation"),
			@ApiResponse(code = 400, message = "Bad Request", response = ServiceErrorList.class),
			@ApiResponse(code = 500, message = "Server Error", response = ServiceErrorList.class)
	})
	public Response evaluateMeasure(@Context HttpServletRequest request,

			@ApiParam(value = ServiceBaseConstants.MINOR_VERSION_DESCRIPTION, required = true, defaultValue = ServiceBuildConstants.DATE) @QueryParam(CohortEngineRestHandler.VERSION) String version,
			@ApiParam(hidden = true, type="file", required=true) IMultipartBody multipartBody) {
		final String methodName = MethodNames.EVALUATE_MEASURE.getName();

		Response response = null;
		
		// Error out if feature is not enabled
		ServiceBaseUtility.isDarkFeatureEnabled(CohortEngineRestConstants.DARK_LAUNCHED_MEASURE_EVALUATION);
		
		try {
			// Perform api setup
			Response errorResponse = ServiceBaseUtility.apiSetup(version, logger, methodName);
			if(errorResponse != null) {
				return errorResponse;
			}
			
			if( multipartBody == null ) {
				throw new IllegalArgumentException("A multipart/form-data body is required");
			}
			
			IAttachment metadataAttachment = multipartBody.getAttachment(REQUEST_DATA_PART);
			if( metadataAttachment == null ) {
				throw new IllegalArgumentException(String.format("Missing '%s' MIME attachment", REQUEST_DATA_PART));
			}

			IAttachment measureAttachment = multipartBody.getAttachment(MEASURE_PART);
			if( measureAttachment == null ) {
				throw new IllegalArgumentException(String.format("Missing '%s' MIME attachment", MEASURE_PART));
			}
			
			// deserialize the MeasuresEvaluation request
			ObjectMapper om = new ObjectMapper();
			MeasureEvaluation evaluationRequest = om.readValue( metadataAttachment.getDataHandler().getInputStream(), MeasureEvaluation.class );
			
			//validate the contents of the evaluationRequest
			validateBean(evaluationRequest);
			
			FhirClientBuilder clientBuilder = FhirClientBuilderFactory.newInstance().newFhirClientBuilder();
			IGenericClient dataClient = clientBuilder.createFhirClient(evaluationRequest.getDataServerConfig());
			IGenericClient terminologyClient = dataClient;
			if( evaluationRequest.getTerminologyServerConfig() != null ) {
				terminologyClient = clientBuilder.createFhirClient(evaluationRequest.getTerminologyServerConfig());
			}
	
			IParser parser = dataClient.getFhirContext().newJsonParser();
			
			String [] searchPaths = new String[] { "fhirResources", "fhirResources/libraries" };
			ZipResourceResolutionProvider provider = new ZipResourceResolutionProvider(new ZipInputStream( measureAttachment.getDataHandler().getInputStream()), parser, searchPaths);;
			
			TerminologyProvider terminologyProvider = new R4RestFhirTerminologyProvider(terminologyClient);
			try (RetrieveCacheContext retrieveCacheContext = new DefaultRetrieveCacheContext()) {
				Map<String, DataProvider> dataProviders = R4DataProviderFactory.createDataProviderMap(dataClient, terminologyProvider, retrieveCacheContext);
				
				MeasureEvaluator evaluator = new MeasureEvaluator(provider, provider, terminologyProvider, dataProviders);
				
				MeasureReport report = evaluator.evaluatePatientMeasure(evaluationRequest.getPatientId(), evaluationRequest.getMeasureContext(), evaluationRequest.getEvidenceOptions());
				
				// The default serializer gets into an infinite loop when trying to serialize MeasureReport, so we use the
				// HAPI encoder instead.
				ResponseBuilder responseBuilder = Response.status(Response.Status.OK).header("Content-Type", "application/json").entity(parser.encodeResourceToString(report));
				response = responseBuilder.build();

			}
		} catch (Throwable e) {
			//map any exceptions caught into the proper REST error response objects
			response = new CohortServiceExceptionMapper().toResponse(e);
		} finally {
			// Perform api cleanup
			Response errorResponse = ServiceBaseUtility.apiCleanup(logger, methodName);
			if( errorResponse != null ) {
				response = errorResponse;
			}
		}

		return response;
	}
	
	@POST
	@Path("/fhir/measure/identifier/{measure_identifier_value}/parameters")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@ApiOperation(value = "Get measure parameters", notes = CohortEngineRestHandler.MEASURE_API_NOTES, response = MeasureParameterInfoList.class, authorizations = {@Authorization(value = "BasicAuth")   }, responseContainer = "List", tags = {
			"FHIR Measures" })
	@ApiImplicitParams({
		@ApiImplicitParam(name=FHIR_DATA_SERVER_CONFIG_PART, value=CohortEngineRestHandler.EXAMPLE_DATA_SERVER_CONFIG_JSON, dataTypeClass = FhirServerConfig.class, required=true, paramType="form", type="file")
	})
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successful Operation", response = MeasureParameterInfoList.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ServiceErrorList.class),
			@ApiResponse(code = 500, message = "Server Error", response = ServiceErrorList.class) })
	public Response getMeasureParameters(
			@ApiParam(value = ServiceBaseConstants.MINOR_VERSION_DESCRIPTION, required = true, defaultValue = ServiceBuildConstants.DATE) @QueryParam(CohortEngineRestHandler.VERSION) String version,
			@ApiParam(hidden = true, type="file", required=true) IMultipartBody multipartBody,
			@ApiParam(value = CohortEngineRestHandler.MEASURE_IDENTIFIER_VALUE_DESC, required = true) @PathParam(CohortEngineRestHandler.MEASURE_IDENTIFIER_VALUE) String measureIdentifierValue,
			@ApiParam(value = CohortEngineRestHandler.MEASURE_IDENTIFIER_SYSTEM_DESC, required = false) @QueryParam(CohortEngineRestHandler.MEASURE_IDENTIFIER_SYSTEM) String measureIdentifierSystem,
			@ApiParam(value = CohortEngineRestHandler.MEASURE_VERSION_DESC, required = false) @QueryParam(CohortEngineRestHandler.MEASURE_VERSION) String measureVersion)
			{
		final String methodName = MethodNames.GET_MEASURE_PARAMETERS.getName();

		Response response = null;
		
		try {
			// Perform api setup
			Response errorResponse = ServiceBaseUtility.apiSetup(version, logger, methodName);
			if(errorResponse != null) {
				return errorResponse;
			}
			
			IAttachment dataSourceAttachment = multipartBody.getAttachment(FHIR_DATA_SERVER_CONFIG_PART);
			if( dataSourceAttachment == null ) {
				throw new IllegalArgumentException(String.format("Missing '%s' MIME attachment", FHIR_DATA_SERVER_CONFIG_PART));
			}
			
			// deserialize the MeasuresEvaluation request
			ObjectMapper om = new ObjectMapper();
			FhirServerConfig fhirServerConfig = om.readValue( dataSourceAttachment.getDataHandler().getInputStream(), FhirServerConfig.class );		
			
			//validate the contents of the fhirServerConfig
			validateBean(fhirServerConfig);
			
			//get the fhir client object used to call to FHIR
			FhirContext ctx = FhirContext.forR4();
			DefaultFhirClientBuilder builder = new DefaultFhirClientBuilder(ctx);
			IGenericClient measureClient = builder.createFhirClient(fhirServerConfig);
			
			//build the identifier object which is used by the fhir client
			//to find the measure
			Identifier identifier = new Identifier()
					.setValue(measureIdentifierValue)
					.setSystem(measureIdentifierSystem);

			//resolve the measure, and return the parameter info for all the libraries linked to by the measure
			List<MeasureParameterInfo> parameterInfoList = FHIRRestUtils.getParametersForMeasureIdentifier(measureClient, identifier, measureVersion);

			//return the results
			MeasureParameterInfoList status = new MeasureParameterInfoList().measureParameterInfoList(parameterInfoList);
			response = Response.ok(status).build();
		} catch (Throwable e) {
			//map any exceptions caught into the proper REST error response objects
			return new CohortServiceExceptionMapper().toResponse(e);
		}finally {
			// Perform api cleanup
			Response errorResponse = ServiceBaseUtility.apiCleanup(logger, methodName);
			if( errorResponse != null ) {
				response = errorResponse;
			}
		}
		
		return response;
	}
	
	@POST
	@Path("/fhir/measure/{measure_id}/parameters")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@ApiOperation(value = "Get measure parameters by id", notes = CohortEngineRestHandler.MEASURE_API_NOTES, response = MeasureParameterInfoList.class, nickname = "get_measure_parameters_by_id", tags = {
			"FHIR Measures" })
	@ApiImplicitParams({
		@ApiImplicitParam(name=FHIR_DATA_SERVER_CONFIG_PART, value=CohortEngineRestHandler.EXAMPLE_DATA_SERVER_CONFIG_JSON, dataTypeClass = FhirServerConfig.class, required=true, paramType="form", type="file"),
	})
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successful Operation", response = MeasureParameterInfoList.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ServiceErrorList.class),
			@ApiResponse(code = 500, message = "Server Error", response = ServiceErrorList.class) })
	public Response getMeasureParametersById(
			@ApiParam(value = ServiceBaseConstants.MINOR_VERSION_DESCRIPTION, required = true, defaultValue = ServiceBuildConstants.DATE) @QueryParam(CohortEngineRestHandler.VERSION) String version,
			@ApiParam(hidden = true, type="file", required=true) IMultipartBody multipartBody,
			@ApiParam(value = CohortEngineRestHandler.MEASURE_ID_DESC, required = true) @PathParam(CohortEngineRestHandler.MEASURE_ID) String measureId)
			{

		final String methodName = MethodNames.GET_MEASURE_PARAMETERS_BY_ID.getName();
		
		Response response = null;
		
		try {
			// Perform api setup
			Response errorResponse = ServiceBaseUtility.apiSetup(version, logger, methodName);
			if(errorResponse != null) {
				return errorResponse;
			}

			IAttachment dataSourceAttachment = multipartBody.getAttachment(FHIR_DATA_SERVER_CONFIG_PART);
			if( dataSourceAttachment == null ) {
				throw new IllegalArgumentException(String.format("Missing '%s' MIME attachment", FHIR_DATA_SERVER_CONFIG_PART));
			}
			
			// deserialize the MeasuresEvaluation request
			ObjectMapper om = new ObjectMapper();
			FhirServerConfig fhirServerConfig = om.readValue( dataSourceAttachment.getDataHandler().getInputStream(), FhirServerConfig.class );		

			//validate the contents of the fhirServerConfig
			validateBean(fhirServerConfig);
			
			//get the fhir client object used to call to FHIR
			FhirContext ctx = FhirContext.forR4();
			DefaultFhirClientBuilder builder = new DefaultFhirClientBuilder(ctx);
			IGenericClient measureClient = builder.createFhirClient(fhirServerConfig);

			//resolve the measure, and return the parameter info for all the libraries linked to by the measure
			List<MeasureParameterInfo> parameterInfoList = FHIRRestUtils.getParametersForMeasureId(measureClient, measureId);

			//return the results
			MeasureParameterInfoList status = new MeasureParameterInfoList().measureParameterInfoList(parameterInfoList);
			response = Response.ok(status).build();
		} catch (Throwable e) {
			//map any exceptions caught into the proper REST error response objects
			return new CohortServiceExceptionMapper().toResponse(e);
		}finally {
			// Perform api cleanup
			Response errorResponse = ServiceBaseUtility.apiCleanup(logger, methodName);
			if(errorResponse != null) {
				response = errorResponse;
			}
		}
		
		return response;
	}


	@POST
	@Path("/valueset/")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ MediaType.APPLICATION_JSON })
	@ApiImplicitParams({
			// This is necessary for the dark launch feature
			@ApiImplicitParam(access = DarkFeatureSwaggerFilter.DARK_FEATURE_CONTROLLED, paramType = "header", dataType = "string"),
			@ApiImplicitParam(name=FHIR_DATA_SERVER_CONFIG_PART, value=CohortEngineRestHandler.EXAMPLE_DATA_SERVER_CONFIG_JSON, dataTypeClass = FhirServerConfig.class, required=true, paramType="form", type="file"),
			@ApiImplicitParam(name=VALUE_SET_PART, value= VALUE_SET_DESC, dataTypeClass = File.class, required=true, paramType="form", type="file" ),
			@ApiImplicitParam(name=CUSTOM_CODE_SYSTEM, value= CUSTOM_CODE_SYSTEM_DESC, dataTypeClass = File.class, required=true, paramType="form", type="file" )
	})
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "Successful Operation"),
			@ApiResponse(code = 400, message = "Bad Request", response = ServiceErrorList.class),
			@ApiResponse(code = 409, message = "Conflict", response = ServiceErrorList.class),
			@ApiResponse(code = 500, message = "Server Error", response = ServiceErrorList.class)
	})
	@ApiOperation(value = "Insert a new value set to the fhir server or, if it already exists, update it in place"
			, notes = CohortEngineRestHandler.VALUE_SET_API_NOTES
			, tags = {"ValueSet" }
			, nickname = "create_value_set"
			, extensions = {
			@Extension(properties = {
					@ExtensionProperty(
							name = DarkFeatureSwaggerFilter.DARK_FEATURE_NAME
							, value = CohortEngineRestConstants.DARK_LAUNCHED_VALUE_SET_UPLOAD)
			})}
	)
	public Response createValueSet(@DefaultValue(ServiceBuildConstants.DATE) @ApiParam(value = ServiceBaseConstants.MINOR_VERSION_DESCRIPTION, required = true, defaultValue = ServiceBuildConstants.DATE) @QueryParam(CohortEngineRestHandler.VERSION) String version,
								   @ApiParam(hidden = true, type="file", required=true) IMultipartBody multipartBody,
								   @ApiParam(value = CohortEngineRestHandler.VALUE_SET_UPDATE_IF_EXISTS_DESC, defaultValue = "false") @DefaultValue ("false") @QueryParam(CohortEngineRestHandler.UPDATE_IF_EXISTS_PARM) boolean updateIfExists
								) {
		String methodName = MethodNames.CREATE_VALUE_SET.getName();
		Response response;
		ServiceBaseUtility.isDarkFeatureEnabled(CohortEngineRestConstants.DARK_LAUNCHED_VALUE_SET_UPLOAD);
		try {
			// Perform api setup
			Response errorResponse = ServiceBaseUtility.apiSetup(version, logger, methodName);
			if(errorResponse != null) {
				return errorResponse;
			}

			IAttachment dataSourceAttachment = multipartBody.getAttachment(FHIR_DATA_SERVER_CONFIG_PART);
			if( dataSourceAttachment == null ) {
				throw new IllegalArgumentException(String.format("Missing '%s' MIME attachment", FHIR_DATA_SERVER_CONFIG_PART));
			}
			
			// deserialize the MeasuresEvaluation request
			ObjectMapper om = new ObjectMapper();
			FhirServerConfig fhirServerConfig = om.readValue( dataSourceAttachment.getDataHandler().getInputStream(), FhirServerConfig.class );		
			
			//validate the contents of the fhirServerConfig
			validateBean(fhirServerConfig);

			//get the fhir client object used to call to FHIR
			FhirContext ctx = FhirContext.forR4();
			DefaultFhirClientBuilder builder = new DefaultFhirClientBuilder(ctx);
			IGenericClient terminologyClient = builder.createFhirClient(fhirServerConfig);
			
			IAttachment valueSetAttachment = multipartBody.getAttachment(VALUE_SET_PART);
			if (valueSetAttachment == null) {
				throw new IllegalArgumentException(String.format("Missing '%s' MIME attachment", VALUE_SET_PART));
			}

			IAttachment customCodes = multipartBody.getAttachment(CUSTOM_CODE_SYSTEM);
			Map<String, String> customCodeMap = null;
			if(customCodes != null){
				customCodeMap = ValueSetUtil.getMapFromInputStream(customCodes.getDataHandler().getInputStream());
			}

			ValueSetArtifact artifact;
			try (InputStream is = valueSetAttachment.getDataHandler().getInputStream()) {
				artifact = ValueSetUtil.createArtifact(is, customCodeMap);
			}
			ValueSetUtil.validateArtifact(artifact);

			String valueSetId = ValueSetUtil.importArtifact(terminologyClient, artifact, updateIfExists);
			if(valueSetId == null){
				return Response.status(Response.Status.CONFLICT).header("Content-Type", "text/plain")
						.entity("Value Set already exists! Rerun with updateIfExists set to true!")
						.build();
			}

			response = Response.status(Response.Status.CREATED).header("Content-Type", "text/plain").entity(valueSetId).build();
		}
		catch (Throwable e){
			return new CohortServiceExceptionMapper().toResponse(e);
		}
		finally {
			// Perform api cleanup
			Response errorResponse = ServiceBaseUtility.apiCleanup(logger, methodName);
			if( errorResponse != null ) {
				response = errorResponse;
			}
		}
		return response;
	}
	
	private <T> void validateBean(T beanInput) {
		// See https://openliberty.io/guides/bean-validation.html
		// TODO: The validator below is recommended to be injected using CDI in the
		// guide
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		try {
			Validator validator = factory.getValidator();

			Set<ConstraintViolation<T>> violations = validator.validate(beanInput);
			if (!violations.isEmpty()) {
				StringBuilder sb = new StringBuilder("Invalid request metadata: ");
				for (ConstraintViolation<T> violation : violations) {
					sb.append(System.lineSeparator()).append(violation.getPropertyPath().toString()).append(": ")
							.append(violation.getMessage());
				}
				throw new IllegalArgumentException(sb.toString());
			}
		} finally {
			factory.close();
		}
	}
}

