/*
 * (C) Copyright IBM Corp. 2021, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.api.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cohort.engine.BaseFhirTest;
import com.ibm.cohort.engine.api.service.CohortEngineRestHandler.MethodNames;
import com.ibm.cohort.engine.api.service.model.MeasureEvaluation;
import com.ibm.cohort.engine.api.service.model.MeasureParameterInfo;
import com.ibm.cohort.engine.api.service.model.PatientListMeasureEvaluation;
import com.ibm.cohort.engine.api.service.model.ServiceErrorList;
import com.ibm.cohort.engine.measure.Identifier;
import com.ibm.cohort.engine.measure.MeasureContext;
import com.ibm.cohort.engine.parameter.DateParameter;
import com.ibm.cohort.engine.parameter.IntervalParameter;
import com.ibm.cohort.engine.parameter.Parameter;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilder;
import com.ibm.cohort.fhir.client.config.FhirServerConfig;
import com.ibm.cohort.valueset.ValueSetUtil;
import com.ibm.watson.common.service.base.ServiceBaseUtility;
import com.ibm.watson.common.service.base.security.Tenant;
import com.ibm.watson.common.service.base.security.TenantManager;
import com.ibm.websphere.jaxrs20.multipart.IAttachment;
import com.ibm.websphere.jaxrs20.multipart.IMultipartBody;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;


/**
 * Junit class to test the CohortEngineRestHandler.
 */

public class CohortEngineRestHandlerTest extends BaseFhirTest {
	// Need to add below to get jacoco to work with powermockito
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	static {
		PowerMockAgent.initializeIfNeeded();
	}

	// We're using this to mock calls to CohortEngineRestHandler, thus to make the mocking work, we need to use the same logger as that class
	private static final Logger logger = LoggerFactory.getLogger(CohortEngineRestHandler.class.getName());
	private static final String VERSION = "version";
	HttpServletRequest requestContext;
	IGenericClient measureClient;
	List<String> httpHeadersList = Arrays.asList("Basic dXNlcm5hbWU6cGFzc3dvcmQ=");
	String[] authParts = new String[] { "username", "password" };
	List<MeasureParameterInfo> parameterInfoList = new ArrayList<>(
			Arrays.asList(new MeasureParameterInfo().documentation("documentation").name("name").min(0).max("max")
					.use("IN").type("type")));
	private static final String VALUE_SET_INPUT = "src/test/resources/2.16.840.1.113762.1.4.1114.7.xlsx";

	@Mock
	private static DefaultFhirClientBuilder mockDefaultFhirClientBuilder;
	@Mock
	private static HttpHeaders mockHttpHeaders;
	@Mock
	private static HttpServletRequest mockRequestContext;
	@Mock
	private static Response mockResponse;
	@Mock
	private static ResponseBuilder mockResponseBuilder;
	@InjectMocks
	private static CohortEngineRestHandler restHandler;

	private void prepMocks() {
		prepMocks(true);
	}

	private void prepMocks(boolean prepResponse) {
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		if (prepResponse) {
			PowerMockito.mockStatic(Response.class);
		}
		PowerMockito.mockStatic(TenantManager.class);

		PowerMockito.when(TenantManager.getTenant()).thenReturn(new Tenant() {
			@Override
			public String getTenantId() {
				return "JunitTenantId";
			}

			@Override
			public String getUserId() {
				return "JunitUserId";
			}

		});
		when(mockRequestContext.getRemoteAddr()).thenReturn("1.1.1.1");
		when(mockRequestContext.getLocalAddr()).thenReturn("1.1.1.1");
		when(mockRequestContext.getRequestURL())
				.thenReturn(new StringBuffer("http://localhost:9080/services/cohort/api/v1/evaluation"));
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(httpHeadersList);
		when(mockDefaultFhirClientBuilder.createFhirClient(ArgumentMatchers.any())).thenReturn(null);
	}

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@BeforeClass
	public static void setUpHandlerBeforeClass() throws Exception {
		restHandler = new CohortEngineRestHandler();
	}

	/**
	 * Test the successful building of a response.
	 */
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureSuccess() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_MEASURE.getName())).thenReturn(null);
		
		mockResponseClasses();
		
		Library library = TestHelper.getTemplateLibrary();
		
		Measure measure = TestHelper.getTemplateMeasure(library);
		
		Patient patient = getPatient("patientId", AdministrativeGender.MALE, 40);
		
		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());
		mockFhirResourceRetrieval(patient);
		
		FhirServerConfig clientConfig = getFhirServerConfig();
		
		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(
				new DateParameter("2019-07-04")
				, true
				, new DateParameter("2020-07-04")
				, true));
		
		MeasureContext measureContext = new MeasureContext(measure.getId(), parameterOverrides);
		
		MeasureEvaluation evaluationRequest = new MeasureEvaluation();
		evaluationRequest.setDataServerConfig(clientConfig);
		evaluationRequest.setPatientId(patient.getId());
		evaluationRequest.setMeasureContext(measureContext);
		//evaluationRequest.setEvidenceOptions(evidenceOptions);
		evaluationRequest.setExpandValueSets(true);
		evaluationRequest.setSearchPageSize(500);
		
		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
				
		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(evaluationRequest);
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Create the ZIP part of the request
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		ByteArrayInputStream zipIs = new ByteArrayInputStream( baos.toByteArray() );
		IAttachment measurePart = mockAttachment(zipIs);
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, VERSION, body);
		assertEquals(mockResponse, loadResponse);
		
		PowerMockito.verifyStatic(Response.class);
		Response.status(Response.Status.OK);
	}



	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureMissingMeasureId() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_MEASURE.getName())).thenReturn(null);
		
		mockResponseClasses();
		
		Patient patient = getPatient("patientId", AdministrativeGender.MALE, 40);
		
		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());
		mockFhirResourceRetrieval(patient);
		
		FhirServerConfig clientConfig = getFhirServerConfig();
		
		MeasureContext measureContext = new MeasureContext("unknown");
		
		MeasureEvaluation evaluationRequest = new MeasureEvaluation();
		evaluationRequest.setDataServerConfig(clientConfig);
		evaluationRequest.setPatientId(patient.getId());
		evaluationRequest.setMeasureContext(measureContext);
		//evaluationRequest.setEvidenceOptions(evidenceOptions);
		
		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(evaluationRequest);
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Create the ZIP part of the request
		ByteArrayInputStream zipIs = TestHelper.emptyZip();
		IAttachment measurePart = mockAttachment(zipIs);
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, VERSION, body);
		assertNotNull(loadResponse);
		
		PowerMockito.verifyStatic(Response.class);
		Response.status(400);
	}
	
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureMissingPatient() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_MEASURE.getName())).thenReturn(null);
		
		mockResponseClasses();
		
		Library library = TestHelper.getTemplateLibrary();
		
		Measure measure = TestHelper.getTemplateMeasure(library);
		
		Patient patient = getPatient("patientId", AdministrativeGender.MALE, 40);
		
		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());
		mockNotFound("/Patient/" + patient.getId());
		
		FhirServerConfig clientConfig = getFhirServerConfig();
		
		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(
				new DateParameter("2019-07-04")
				, true
				, new DateParameter("2020-07-04")
				, true));
		
		MeasureContext measureContext = new MeasureContext(measure.getId(), parameterOverrides);
		
		MeasureEvaluation evaluationRequest = new MeasureEvaluation();
		evaluationRequest.setDataServerConfig(clientConfig);
		evaluationRequest.setPatientId(patient.getId());
		evaluationRequest.setMeasureContext(measureContext);
		//evaluationRequest.setEvidenceOptions(evidenceOptions);
		
		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
				
		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(evaluationRequest);
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Create the ZIP part of the request
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		ByteArrayInputStream zipIs = new ByteArrayInputStream( baos.toByteArray() );
		IAttachment measurePart = mockAttachment(zipIs);
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, VERSION, body);
		assertNotNull(loadResponse);
		
		PowerMockito.verifyStatic(Response.class);
		Response.status(400);
	}
	
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureMoreThanOneFormOfId() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_MEASURE.getName())).thenReturn(null);
		
		mockResponseClasses();
		
		Library library = TestHelper.getTemplateLibrary();
		
		Measure measure = TestHelper.getTemplateMeasure(library);
		
		Patient patient = getPatient("patientId", AdministrativeGender.MALE, 40);
		
		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());
		mockFhirResourceRetrieval(patient);
		
		FhirServerConfig clientConfig = getFhirServerConfig();
		
		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(
				new DateParameter("2019-07-04")
				, true
				, new DateParameter("2020-07-04")
				, true));
		
		MeasureContext measureContext = new MeasureContext(measure.getId(), parameterOverrides, new Identifier().setValue("identifier"));
		
		MeasureEvaluation evaluationRequest = new MeasureEvaluation();
		evaluationRequest.setDataServerConfig(clientConfig);
		evaluationRequest.setPatientId(patient.getId());
		evaluationRequest.setMeasureContext(measureContext);
		//evaluationRequest.setEvidenceOptions(evidenceOptions);
		
		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
				
		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(evaluationRequest);
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Create the ZIP part of the request
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		ByteArrayInputStream zipIs = new ByteArrayInputStream( baos.toByteArray() );
		IAttachment measurePart = mockAttachment(zipIs);
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, VERSION, body);
		assertNotNull(loadResponse);
		
		PowerMockito.verifyStatic(Response.class);
		Response.status(400);
	}
	
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureMissingVersion() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		
		Response badRequest = Mockito.mock(Response.class);
		PowerMockito.when(ServiceBaseUtility.class, "apiSetup", Mockito.isNull(), Mockito.any(), Mockito.eq(MethodNames.EVALUATE_MEASURE.getName())).thenReturn(badRequest);
		
		mockResponseClasses();
		
		// Create the metadata part of the request
		String json = "{}";
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Create the ZIP part of the request
		ByteArrayInputStream zipIs = TestHelper.emptyZip();
		IAttachment measurePart = mockAttachment(zipIs);
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, null, body);
		assertNotNull(loadResponse);
		assertSame(badRequest, loadResponse);
		
		PowerMockito.verifyStatic(ServiceBaseUtility.class);
		ServiceBaseUtility.apiSetup(Mockito.isNull(), Mockito.any(), Mockito.anyString());
		
		// verifyStatic must be called before each verification
		PowerMockito.verifyStatic(ServiceBaseUtility.class);
		ServiceBaseUtility.apiCleanup(Mockito.any(), Mockito.eq(MethodNames.EVALUATE_MEASURE.getName()));
	}
	
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureInvalidMeasureJSON() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_MEASURE.getName())).thenReturn(null);
		
		mockResponseClasses();
		
		// Create the metadata part of the request
		String json = "{ \"something\": \"unexpected\" }";
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Create the ZIP part of the request
		IAttachment measurePart = mockAttachment( TestHelper.emptyZip() );
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, VERSION, body);
		assertNotNull(loadResponse);
		
		PowerMockito.verifyStatic(Response.class);
		Response.status(400);
		
		ArgumentCaptor<ServiceErrorList> errorBody = ArgumentCaptor.forClass(ServiceErrorList.class);
		Mockito.verify(mockResponseBuilder).entity(errorBody.capture());
		assertTrue( errorBody.getValue().getErrors().get(0).getMessage().contains("Unrecognized field"));
	}
	
	private void mockResponseClasses() {
		PowerMockito.mockStatic(Response.class);
		PowerMockito.when(Response.status(Mockito.any(Response.Status.class))).thenReturn(mockResponseBuilder);
		PowerMockito.when(Response.status(Mockito.anyInt())).thenReturn(mockResponseBuilder);
		PowerMockito.when(mockResponseBuilder.entity(Mockito.any(Object.class))).thenReturn(mockResponseBuilder);
		PowerMockito.when(mockResponseBuilder.header(Mockito.anyString(), Mockito.anyString())).thenReturn(mockResponseBuilder);
		PowerMockito.when(mockResponseBuilder.type(Mockito.any(String.class))).thenReturn(mockResponseBuilder);
		PowerMockito.when(mockResponseBuilder.build()).thenReturn(mockResponse);
	}

	private IAttachment mockAttachment(InputStream multipartData) throws IOException {
		IAttachment measurePart = mock(IAttachment.class);
		DataHandler zipHandler = mock(DataHandler.class);
		when( zipHandler.getInputStream() ).thenReturn(multipartData);
		when( measurePart.getDataHandler() ).thenReturn( zipHandler );
		return measurePart;
	}


	/**
	 * Test the successful building of a response.
	 */

	@PrepareForTest({ TenantManager.class, ServiceBaseUtility.class, DefaultFhirClientBuilder.class, FHIRRestUtils.class })
	@Test
	public void testGetMeasureParameters() throws Exception {
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.mockStatic(DefaultFhirClientBuilder.class);
		PowerMockito.mockStatic(FHIRRestUtils.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.GET_MEASURE_PARAMETERS.getName())).thenReturn(null);
		PowerMockito.when(FHIRRestUtils.getParametersForMeasureIdentifier(ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(parameterInfoList);

		Response loadResponse = restHandler.getMeasureParameters( VERSION, getFhirConfigFileBody(), "measureIdentifierValue", "measureIdentifierSystem", "measureVersion");
		validateParameterResponse(loadResponse);
	}
	
	/*
	 * Test API Setup failure
	 */
	
	@PrepareForTest({ServiceBaseUtility.class})
	@Test
	public void testGetMeasureParameters_apiSetupFailure() throws Exception{
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
			
		Response badResponse = Response.status(Status.BAD_REQUEST).build();
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.GET_MEASURE_PARAMETERS.getName())).thenReturn(badResponse);
		
		Response loadResponse = restHandler.getMeasureParameters( VERSION, getFhirConfigFileBody(), "measureIdentifierValue", "measureIdentifierSystem", "measureVersion");
		
		assertEquals(badResponse, loadResponse);
	}
	
	/*
	 * Test API Cleanup failure
	 */
	
	@PrepareForTest({ TenantManager.class, ServiceBaseUtility.class, DefaultFhirClientBuilder.class, FHIRRestUtils.class })
	@Test
	public void testGetMeasureParameters_apiCleanupFailure() throws Exception {
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.mockStatic(DefaultFhirClientBuilder.class);
		PowerMockito.mockStatic(FHIRRestUtils.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.GET_MEASURE_PARAMETERS.getName())).thenReturn(null);
		
		Response badResponse = Response.status(Status.INTERNAL_SERVER_ERROR).build();
		PowerMockito.when(ServiceBaseUtility.apiCleanup(logger, MethodNames.GET_MEASURE_PARAMETERS.getName())).thenReturn(badResponse);
		
		PowerMockito.when(FHIRRestUtils.getParametersForMeasureIdentifier(ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(parameterInfoList);

		Response loadResponse = restHandler.getMeasureParameters( VERSION, getFhirConfigFileBody(), "measureIdentifierValue", "measureIdentifierSystem", "measureVersion");
		
		assertEquals(badResponse, loadResponse);
	}

	/**
	 * Test the successful building of a response.
	 */

	@PrepareForTest({ TenantManager.class, ServiceBaseUtility.class, DefaultFhirClientBuilder.class,
			FHIRRestUtils.class })
	@Test
	public void testGetMeasureParametersById() throws Exception {
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.mockStatic(FHIRRestUtils.class);
		PowerMockito.mockStatic(DefaultFhirClientBuilder.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.GET_MEASURE_PARAMETERS_BY_ID.getName())).thenReturn(null);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withArguments(Mockito.any()).thenReturn(mockDefaultFhirClientBuilder);

		PowerMockito.when(FHIRRestUtils.getParametersForMeasureId(ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(parameterInfoList);

		Response loadResponse = restHandler.getMeasureParametersById(VERSION, getFhirConfigFileBody(), "measureId");
		validateParameterResponse(loadResponse);
	}
	
	/*
	 * Test API Setup failure
	 */
	
	@PrepareForTest({ ServiceBaseUtility.class })
	@Test
	public void testGetMeasureParametersById_apiSetupFailure() throws Exception{
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		
		Response badResponse = Response.status(Status.BAD_REQUEST).build();
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.GET_MEASURE_PARAMETERS_BY_ID.getName())).thenReturn(badResponse);
		
		Response loadResponse = restHandler.getMeasureParametersById(VERSION, getFhirConfigFileBody(), "measureId");
		
		assertEquals(badResponse, loadResponse);
	}
	
	/*
	 * Test API Cleanup failure
	 */
	
	@PrepareForTest({ TenantManager.class, ServiceBaseUtility.class, DefaultFhirClientBuilder.class,
			FHIRRestUtils.class })
	@Test
	public void testGetMeasureParametersById_apiCleanupFailure() throws Exception {
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.mockStatic(FHIRRestUtils.class);
		PowerMockito.mockStatic(DefaultFhirClientBuilder.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.GET_MEASURE_PARAMETERS_BY_ID.getName())).thenReturn(null);
		
		Response badResponse = Response.status(Status.INTERNAL_SERVER_ERROR).build();
		PowerMockito.when(ServiceBaseUtility.apiCleanup(logger, MethodNames.GET_MEASURE_PARAMETERS_BY_ID.getName())).thenReturn(badResponse);	
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withArguments(Mockito.any()).thenReturn(mockDefaultFhirClientBuilder);
		
		PowerMockito.when(FHIRRestUtils.getParametersForMeasureIdentifier(ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(parameterInfoList);

		Response loadResponse = restHandler.getMeasureParametersById(VERSION, getFhirConfigFileBody(), "measureId");
		
		assertEquals(badResponse, loadResponse);
	}


	@PrepareForTest({ Response.class, ValueSetUtil.class })
	@Test
	public void testLoadValueSets() throws Exception {
		prepMocks();
		mockResponseClasses();
		
		PowerMockito.mockStatic(ValueSetUtil.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withArguments(Mockito.any()).thenReturn(mockDefaultFhirClientBuilder);

		PowerMockito.when(ValueSetUtil.importArtifact(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(false)))
				.thenReturn("ID");

		File tempFile = new File(VALUE_SET_INPUT);
		IAttachment spreadsheetPart = PowerMockito.mock(IAttachment.class);
		DataHandler dataHandler = PowerMockito.mock(DataHandler.class);
		when(spreadsheetPart.getDataHandler()).thenReturn(dataHandler);
		when(dataHandler.getInputStream())
					.thenReturn(new ByteArrayInputStream(Files.readAllBytes(Paths.get(tempFile.getAbsolutePath()))));

		IMultipartBody body = getFhirConfigFileBody();
		when(body.getAttachment(CohortEngineRestHandler.VALUE_SET_PART)).thenReturn(spreadsheetPart);

		Response loadResponse = restHandler.createValueSet(
				VERSION,
				body,
				false
		);
		assertNotNull(loadResponse);
		PowerMockito.verifyStatic(Response.class);
		Response.status(Status.CREATED);
	}

	@PrepareForTest({ Response.class, ValueSetUtil.class, FHIRRestUtils.class })
	@Test
	public void testValueSetExists() throws Exception {
		prepMocks();
		mockResponseClasses();

		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.mockStatic(FHIRRestUtils.class);
		PowerMockito.mockStatic(ValueSetUtil.class);
		PowerMockito.mockStatic(DefaultFhirClientBuilder.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.CREATE_VALUE_SET.getName())).thenReturn(null);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withArguments(Mockito.any()).thenReturn(mockDefaultFhirClientBuilder);
		PowerMockito.when(ValueSetUtil.importArtifact(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(true)))
				.thenReturn("ID");

		File tempFile = new File(VALUE_SET_INPUT);
		IAttachment spreadsheetPart = PowerMockito.mock(IAttachment.class);
		byte[] x = Files.readAllBytes(Paths.get(tempFile.getAbsolutePath()));
		DataHandler dataHandler = PowerMockito.mock(DataHandler.class);
		when(spreadsheetPart.getDataHandler()).thenReturn(dataHandler);
		when(dataHandler.getInputStream())
				.thenReturn(new ByteArrayInputStream(x));

		IMultipartBody body = getFhirConfigFileBody();
		when(body.getAttachment(CohortEngineRestHandler.VALUE_SET_PART)).thenReturn(spreadsheetPart);

		Response loadResponse = restHandler.createValueSet(
				VERSION,
				body,
				false
		);
		assertNotNull(loadResponse);
		PowerMockito.verifyStatic(Response.class);
		Response.status(Status.CONFLICT);
	}

	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluatePatientListMeasureSuccess() throws Exception {
		prepMocks();

		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_PATIENT_LIST_MEASURE.getName())).thenReturn(null);

		mockResponseClasses();

		Library library = TestHelper.getTemplateLibrary();

		Measure measure = TestHelper.getTemplateMeasure(library);

		Patient patient1 = getPatient("patientId1", AdministrativeGender.MALE, 40);
		Patient patient2 = getPatient("patientId2", AdministrativeGender.FEMALE, 40);

		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());
		mockFhirResourceRetrieval(patient1);
		mockFhirResourceRetrieval(patient2);

		FhirServerConfig clientConfig = getFhirServerConfig();

		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(
				new DateParameter("2019-07-04")
				, true
				, new DateParameter("2020-07-04")
				, true));

		MeasureContext measureContext = new MeasureContext(measure.getId(), parameterOverrides);

		PatientListMeasureEvaluation request = new PatientListMeasureEvaluation();
		request.setDataServerConfig(clientConfig);
		ArrayList<String> patientIds = new ArrayList<>();
		patientIds.add(patient1.getId());
		patientIds.add(patient2.getId());
		request.setPatientIds(patientIds);
		request.setMeasureContext(measureContext);
		request.setExpandValueSets(true);
		request.setSearchPageSize(500);

		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);

		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(request);
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);

		// Create the ZIP part of the request
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		ByteArrayInputStream zipIs = new ByteArrayInputStream( baos.toByteArray() );
		IAttachment measurePart = mockAttachment(zipIs);

		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when(body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART)).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);

		Response loadResponse = restHandler.evaluatePatientListMeasure(mockRequestContext, VERSION, body);
		assertEquals(mockResponse, loadResponse);

		PowerMockito.verifyStatic(Response.class);
		Response.status(Response.Status.OK);
	}


	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluatePatientListMeasureMissingPatient() throws Exception {
		prepMocks();

		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(VERSION, logger, MethodNames.EVALUATE_PATIENT_LIST_MEASURE.getName())).thenReturn(null);

		mockResponseClasses();

		Library library = TestHelper.getTemplateLibrary();

		Measure measure = TestHelper.getTemplateMeasure(library);

		Patient patient = getPatient("patientId", AdministrativeGender.MALE, 40);

		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());
		mockNotFound("/Patient/" + patient.getId());

		FhirServerConfig clientConfig = getFhirServerConfig();

		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(
				new DateParameter("2019-07-04")
				, true
				, new DateParameter("2020-07-04")
				, true));

		MeasureContext measureContext = new MeasureContext(measure.getId(), parameterOverrides);

		PatientListMeasureEvaluation request = new PatientListMeasureEvaluation();
		request.setDataServerConfig(clientConfig);
		request.setPatientIds(Collections.singletonList(patient.getId()));
		request.setMeasureContext(measureContext);

		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);

		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(request);
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);

		// Create the ZIP part of the request
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		ByteArrayInputStream zipIs = new ByteArrayInputStream( baos.toByteArray() );
		IAttachment measurePart = mockAttachment(zipIs);

		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.REQUEST_DATA_PART) ).thenReturn(rootPart);
		when( body.getAttachment(CohortEngineRestHandler.MEASURE_PART) ).thenReturn(measurePart);

		Response loadResponse = restHandler.evaluatePatientListMeasure(mockRequestContext, VERSION, body);
		assertNotNull(loadResponse);

		PowerMockito.verifyStatic(Response.class);
		Response.status(400);
	}

	private void validateParameterResponse(Response loadResponse) {
		assertEquals(Status.OK.getStatusCode(), loadResponse.getStatus());
		String validResp = "class MeasureParameterInfoList {\n"
				+ "    parameterInfoList: [class MeasureParameterInfo {\n" + "        name: name\n"
				+ "        use: IN\n" + "        min: 0\n" + "        max: max\n" + "        type: type\n"
				+ "        defaultValue: null\n"
				+ "        documentation: documentation\n" + "    ]\n" + "}";
		assertEquals(validResp, loadResponse.getEntity().toString());
	}
	
	private IMultipartBody getFhirConfigFileBody() throws Exception {
		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String json = om.writeValueAsString(getFhirServerConfig());
		ByteArrayInputStream jsonIs = new ByteArrayInputStream(json.getBytes());
		IAttachment rootPart = mockAttachment(jsonIs);
		
		// Assemble them together into a reasonable facsimile of the real request
		IMultipartBody body = mock(IMultipartBody.class);
		when( body.getAttachment(CohortEngineRestHandler.FHIR_DATA_SERVER_CONFIG_PART) ).thenReturn(rootPart);
		
		return body;
	}

}