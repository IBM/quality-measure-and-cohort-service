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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.ibm.cohort.engine.api.service.model.MeasureEvaluation;
import com.ibm.cohort.engine.api.service.model.MeasureParameterInfo;
import com.ibm.cohort.engine.api.service.model.ServiceErrorList;
import com.ibm.cohort.engine.measure.MeasureContext;
import com.ibm.cohort.engine.parameter.DateParameter;
import com.ibm.cohort.engine.parameter.IntervalParameter;
import com.ibm.cohort.engine.parameter.Parameter;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilder;
import com.ibm.cohort.fhir.client.config.FhirServerConfig;
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

	private static final Logger logger = LoggerFactory.getLogger(CohortEngineRestHandlerTest.class.getName());
	String version;
	String methodName;
	HttpServletRequest requestContext;
	IGenericClient measureClient;
	List<String> httpHeadersList = Arrays.asList("Basic dXNlcm5hbWU6cGFzc3dvcmQ=");
	String[] authParts = new String[] { "username", "password" };
	List<MeasureParameterInfo> parameterInfoList = new ArrayList<MeasureParameterInfo>(
			Arrays.asList(new MeasureParameterInfo().documentation("documentation").name("name").min(0).max("max")
					.use("IN").type("type")));

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
	public void setUp() throws Exception {
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
		PowerMockito.when(ServiceBaseUtility.apiSetup(version, logger, methodName)).thenReturn(null);
		
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
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, "version", body);
		assertEquals(mockResponse, loadResponse);
		
		PowerMockito.verifyStatic(Response.class);
		Response.status(Response.Status.OK);
	}
	
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureMissingMeasureId() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(version, logger, methodName)).thenReturn(null);
		
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
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, "version", body);
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
		PowerMockito.when(ServiceBaseUtility.class, "apiSetup", Mockito.isNull(), Mockito.any(), Mockito.eq("evaluateMeasure")).thenReturn(badRequest);
		
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
		ServiceBaseUtility.apiCleanup(Mockito.any(), Mockito.eq("evaluateMeasure"));
	}
	
	@PrepareForTest({ Response.class, TenantManager.class, ServiceBaseUtility.class })
	@Test
	public void testEvaluateMeasureInvalidMeasureJSON() throws Exception {
		prepMocks();
		
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(version, logger, methodName)).thenReturn(null);
		
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
		
		Response loadResponse = restHandler.evaluateMeasure(mockRequestContext, "version", body);
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

	@PrepareForTest({ TenantManager.class, ServiceBaseUtility.class, DefaultFhirClientBuilder.class,
			FHIRRestUtils.class })
	@Test
	public void testGetMeasureParameters() throws Exception {
		prepMocks(false);
		PowerMockito.mockStatic(ServiceBaseUtility.class);
		PowerMockito.mockStatic(FHIRRestUtils.class);
		PowerMockito.mockStatic(DefaultFhirClientBuilder.class);
		PowerMockito.when(ServiceBaseUtility.apiSetup(version, logger, methodName)).thenReturn(null);
		PowerMockito.when(FHIRRestUtils.parseAuthenticationHeaderInfo(ArgumentMatchers.any())).thenReturn(authParts);
		PowerMockito.when(
				FHIRRestUtils.getFHIRClient(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
						ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(measureClient);
		PowerMockito.when(FHIRRestUtils.getParametersForMeasureIdentifier(ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(parameterInfoList);

		Response loadResponse = restHandler.getMeasureParameters(mockHttpHeaders, "version", "fhirEndpoint",
				"fhirTenantId", "measureIdentifierValue", "measureIdentifierSystem", "measureVersion",
				"fhirTenantIdHeader", "fhirDataSourceIdHeader", "fhirDataSourceId");
		validateParameterResponse(loadResponse);
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
		PowerMockito.when(ServiceBaseUtility.apiSetup(version, logger, methodName)).thenReturn(null);
		PowerMockito.when(FHIRRestUtils.parseAuthenticationHeaderInfo(ArgumentMatchers.any())).thenReturn(authParts);
		PowerMockito.when(
				FHIRRestUtils.getFHIRClient(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
						ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(measureClient);
		PowerMockito.when(FHIRRestUtils.getParametersForMeasureId(ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(parameterInfoList);

		Response loadResponse = restHandler.getMeasureParametersById(mockHttpHeaders, "version", "fhirEndpoint",
				"fhirTenantId", "measureId", "fhirTenantIdHeader", "fhirDataSourceIdHeader", "fhirDataSourceId");
		validateParameterResponse(loadResponse);
	}

	private void validateParameterResponse(Response loadResponse) {
		assertEquals(Status.OK.getStatusCode(), loadResponse.getStatus());
		String validResp = "class MeasureParameterInfoList {\n"
				+ "    parameterInfoList: [class MeasureParameterInfo {\n" + "        name: name\n"
				+ "        use: IN\n" + "        min: 0\n" + "        max: max\n" + "        type: type\n"
				+ "        documentation: documentation\n" + "    ]\n" + "}";
		assertEquals(validResp, loadResponse.getEntity().toString());
	}
}