/*
 * (C) Copyright IBM Corp. 2021, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Measure;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ibm.cohort.engine.api.service.model.MeasureParameterInfo;
import com.ibm.cohort.engine.measure.RestFhirLibraryResolutionProvider;
import com.ibm.cohort.engine.measure.RestFhirMeasureResolutionProvider;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilder;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;

/**
 * Junit class to test the FHIRRestUtilsTest.
 */

public class FHIRRestUtilsTest {
	// Need to add below to get jacoco to work with powermockito
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	static {
		PowerMockAgent.initializeIfNeeded();
	}
	IGenericClient measureClient;
	List<String> httpHeadersList = Arrays.asList("Basic dXNlcm5hbWU6cGFzc3dvcmQ=");
	List<String> badHttpHeadersList = Arrays.asList("Basic !");
	List<String> noUserPassHttpHeadersList = Arrays.asList("Basic ");
	List<String> emptyStrHttpHeadersList = Arrays.asList("");
	List<String> nullHttpHeadersList = null;
	List<String> emptyHttpHeadersList = new ArrayList<>();
	List<String> justUserNameHttpHeadersList = Arrays.asList("Basic dXNlcm5hbWU=");

	String[] authParts = new String[] { "username", "password" };

	FhirContext ctx = FhirContext.forR4();

	Identifier identifier;

	String testMeasureDef = "{\n" +
			"  \"resourceType\": \"Measure\",\n" +
			"  \"id\": \"wh-cohort-Over-the-Hill-Female-1.0.0-identifier\",\n" +
			"  \"identifier\": [\n" +
			"    {\n" +
			"      \"use\": \"official\",\n" +
			"      \"system\": \"http://fakesystem.org\",\n" +
			"      \"value\": \"999\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"version\": \"1.0.0\",\n" +
			"  \"name\": \"Over-the-Hill-Female\",\n" +
			"  \"status\": \"active\",\n" +
			"  \"experimental\": true,\n" +
			"  \"publisher\": \"IBM WH Cohorting Test\",\n" +
			"  \"description\": \"Over-the-Hill-Female\",\n" +
			"  \"library\": [\n" +
			"    \"http://ibm.com/fhir/wh-cohort/Library/wh-cohort-Over-the-Hill-Female-1.0.0\"\n" +
			"  ],\n" +
			"  \"extension\": [\n" +
			"    {\n" +
			"      \"id\": \"measureParam\",\n" +
			"      \"url\": \"http://ibm.com/fhir/cdm/StructureDefinition/measure-parameter\",\n" +
			"      \"valueParameterDefinition\": {\n" +
			"        \"name\": \"aName\",\n" +
			"        \"use\": \"in\",\n" +
			"        \"max\": \"1\",\n" +
			"        \"min\": \"0\",\n" +
			"        \"type\": \"String\",\n" +
			"        \"extension\": [\n" +
			"          {\n" +
			"            \"id\": \"defaultExample\",\n" +
			"            \"url\": \"http://ibm.com/fhir/cdm/StructureDefinition/default-value\",\n" +
			"            \"valueString\": \"42\"\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"    }\n" +
			"  ]\n" +
			"}\n";

	@Mock
	private static HttpHeaders mockHttpHeaders;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test
	public void testGetFHIRClient() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockDefaultFhirClientBuilder.createFhirClient(ArgumentMatchers.any())).thenReturn(null);

		FHIRRestUtils.getFHIRClient("fhirEndpoint", "userName", "password", "", "fhirTenantId", "", "fhirDataSourceId");

	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test
	public void testGetFHIRClientEmptyHeaders() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockDefaultFhirClientBuilder.createFhirClient(ArgumentMatchers.any())).thenReturn(null);

		FHIRRestUtils.getFHIRClient("fhirEndpoint", "userName", "password", null, "fhirTenantId", null,
				"fhirDataSourceId");

	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test
	public void testParseAuthenticationHeaderInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(httpHeadersList);

		String[] authParts = FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
		assertEquals("username", authParts[0]);
		assertEquals("password", authParts[1]);

	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test(expected = IllegalArgumentException.class)
	public void testParseEmptyAuthenticationHeaderInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(emptyHttpHeadersList);

		FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test(expected = IllegalArgumentException.class)
	public void testParseEmptyAuthenticationHeaderStrInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(emptyStrHttpHeadersList);

		FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test(expected = IllegalArgumentException.class)
	public void testParseNoUserPassAuthenticationHeaderStrInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(noUserPassHttpHeadersList);

		FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test(expected = IllegalArgumentException.class)
	public void testParseAuthenticationHeaderMissingPasswordInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(justUserNameHttpHeadersList);

		FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test(expected = IllegalArgumentException.class)
	public void testParseNUllAuthenticationHeaderInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(nullHttpHeadersList);

		FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
	}

	@PrepareForTest({ FHIRRestUtils.class, DefaultFhirClientBuilder.class })
	@Test(expected = IllegalArgumentException.class)
	public void testParseAuthenticationHeaderBadPasswordInfo() throws Exception {

		DefaultFhirClientBuilder mockDefaultFhirClientBuilder = Mockito.mock(DefaultFhirClientBuilder.class);
		PowerMockito.whenNew(DefaultFhirClientBuilder.class).withAnyArguments()
				.thenReturn(mockDefaultFhirClientBuilder);
		when(mockHttpHeaders.getRequestHeader(HttpHeaders.AUTHORIZATION)).thenReturn(badHttpHeadersList);

		FHIRRestUtils.parseAuthenticationHeaderInfo(mockHttpHeaders);
	}

	@PrepareForTest({ FHIRRestUtils.class, RestFhirLibraryResolutionProvider.class,
			RestFhirMeasureResolutionProvider.class })
	@Test
	public void testGetParametersForMeasureId() throws Exception {

		RestFhirLibraryResolutionProvider mockLibraryResolutionProvider = Mockito
				.mock(RestFhirLibraryResolutionProvider.class);
		PowerMockito.whenNew(RestFhirLibraryResolutionProvider.class).withAnyArguments()
				.thenReturn(mockLibraryResolutionProvider);
		RestFhirMeasureResolutionProvider mockMeasureResolutionProvider = Mockito
				.mock(RestFhirMeasureResolutionProvider.class);
		PowerMockito.whenNew(RestFhirMeasureResolutionProvider.class).withAnyArguments()
				.thenReturn(mockMeasureResolutionProvider);

		when(mockMeasureResolutionProvider.resolveMeasureById(ArgumentMatchers.any()))
				.thenReturn(createMeasure(testMeasureDef));
		List<MeasureParameterInfo> parameterInfoList = FHIRRestUtils.getParametersForMeasureId(null, "measureId");

		MeasureParameterInfo expectedParamInfo = new MeasureParameterInfo();
		expectedParamInfo.setname("aName");
		expectedParamInfo.setUse("In");
		expectedParamInfo.setMax("1");
		expectedParamInfo.setMin(0);
		expectedParamInfo.setType("String");
		expectedParamInfo.setDocumentation(null);
		expectedParamInfo.defaultValue("42");

		assertThat(parameterInfoList, containsInAnyOrder(expectedParamInfo));
	}

	@PrepareForTest({ FHIRRestUtils.class, RestFhirLibraryResolutionProvider.class,
			RestFhirMeasureResolutionProvider.class })
	@Test
	public void testGetParametersWithDefaultsForMeasureId() throws Exception {
		RestFhirLibraryResolutionProvider mockLibraryResolutionProvider = Mockito
				.mock(RestFhirLibraryResolutionProvider.class);
		PowerMockito.whenNew(RestFhirLibraryResolutionProvider.class).withAnyArguments()
				.thenReturn(mockLibraryResolutionProvider);
		RestFhirMeasureResolutionProvider mockMeasureResolutionProvider = Mockito
				.mock(RestFhirMeasureResolutionProvider.class);
		PowerMockito.whenNew(RestFhirMeasureResolutionProvider.class).withAnyArguments()
				.thenReturn(mockMeasureResolutionProvider);

		when(mockMeasureResolutionProvider.resolveMeasureById(ArgumentMatchers.any()))
				.thenReturn(createMeasure(testMeasureDef));

		List<MeasureParameterInfo> parameterInfoList = FHIRRestUtils.getParametersForMeasureId(null, "measureId");

		MeasureParameterInfo expectedParamInfo = new MeasureParameterInfo();
		expectedParamInfo.setname("aName");
		expectedParamInfo.setUse("In");
		expectedParamInfo.setMax("1");
		expectedParamInfo.setMin(0);
		expectedParamInfo.setType("String");
		expectedParamInfo.setDocumentation(null);
		expectedParamInfo.defaultValue("42");

		assertThat(parameterInfoList, containsInAnyOrder(expectedParamInfo));
	}

	/**
	 * Test the successful building of a response.
	 */

	@PrepareForTest({ FHIRRestUtils.class, RestFhirLibraryResolutionProvider.class,
			RestFhirMeasureResolutionProvider.class })
	@Test
	public void testGetParametersForMeasureIdentifier() throws Exception {
		RestFhirLibraryResolutionProvider mockLibraryResolutionProvider = Mockito
				.mock(RestFhirLibraryResolutionProvider.class);
		PowerMockito.whenNew(RestFhirLibraryResolutionProvider.class).withAnyArguments()
				.thenReturn(mockLibraryResolutionProvider);
		RestFhirMeasureResolutionProvider mockMeasureResolutionProvider = Mockito
				.mock(RestFhirMeasureResolutionProvider.class);
		PowerMockito.whenNew(RestFhirMeasureResolutionProvider.class).withAnyArguments()
				.thenReturn(mockMeasureResolutionProvider);
		when(mockMeasureResolutionProvider.resolveMeasureByIdentifier(ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(createMeasure(testMeasureDef));

		List<MeasureParameterInfo> parameterInfoList = FHIRRestUtils.getParametersForMeasureIdentifier(measureClient,
				identifier, "");

		MeasureParameterInfo expectedParamInfo = new MeasureParameterInfo();
		expectedParamInfo.setname("aName");
		expectedParamInfo.setUse("In");
		expectedParamInfo.setMax("1");
		expectedParamInfo.setMin(0);
		expectedParamInfo.setType("String");
		expectedParamInfo.setDocumentation(null);
		expectedParamInfo.defaultValue("42");

		assertThat(parameterInfoList, containsInAnyOrder(expectedParamInfo));
	}

	private Measure createMeasure(String inputString) throws JsonProcessingException {
		// Instantiate a new parser
		ca.uhn.fhir.parser.IParser parser = ctx.newJsonParser();
		// Parse it
		return parser.parseResource(Measure.class, inputString);
	}

}