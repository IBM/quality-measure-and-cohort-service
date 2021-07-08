/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.api.service.vt;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.JsonAssert.assertJsonStructureEquals;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_VALUES;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLNotEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathNotExists;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathValuesEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathValuesNotEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathsEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathsNotEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.javacrumbs.jsonunit.JsonAssert;
import net.javacrumbs.jsonunit.core.Option;

import org.apache.http.HttpStatus;
import org.custommonkey.xmlunit.Diff;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cohort.engine.api.service.CohortEngineRestConstants;
import com.ibm.cohort.engine.api.service.CohortEngineRestHandler;
import com.ibm.cohort.engine.api.service.ServiceBuildConstants;
import com.ibm.cohort.engine.api.service.TestHelper;
import com.ibm.cohort.engine.api.service.model.MeasureEvaluation;
import com.ibm.cohort.engine.api.service.model.PatientListMeasureEvaluation;
import com.ibm.cohort.engine.measure.MeasureContext;
import com.ibm.cohort.engine.measure.evidence.MeasureEvidenceOptions;
import com.ibm.cohort.engine.parameter.DateParameter;
import com.ibm.cohort.engine.parameter.IntervalParameter;
import com.ibm.cohort.engine.parameter.Parameter;
import com.ibm.cohort.fhir.client.config.DefaultFhirClientBuilder;
import com.ibm.cohort.fhir.client.config.FhirServerConfig;
import com.ibm.watson.common.service.base.utilities.BVT;
import com.ibm.watson.common.service.base.utilities.DVT;
import com.ibm.watson.common.service.base.utilities.ServiceAPIGlobalSpec;
import com.ibm.watson.common.service.base.utilities.UNDERCONSTRUCTION;
import com.ibm.watson.common.service.base.vt.ServiceVTBase;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Headers;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;

/*
 * PLEASE READ THIS FIRST
 * @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
 *
 * Down this class you shall see some basic samples of using JSONUNIT and XMLUNIT to show case XML or JSON object/
 * string comparison. Recommended way of comparing the expected vs actual JSON or XML responses.
 *
 * @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
 *
 * ALSO note the usage of Categories @Category BVT and DVT and they can be tagged at class level OR method level.
 * 1. ALL classes that need to be run as BVT or regression should be tagged @Category(BVT.class)
 * 2. ALL  classes that need to be run as DVT should be tagged @Category(DVT.class)
 *
 * @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
 *
 * FINALLY, note you can get the required information related to the deployment ENV using below api calls
 * getUrlBase()
 *
 */

@Category(BVT.class) 			// all classes must be tagged as BVT, otherwise will not be picked up by automation runs
public class DefaultVT extends ServiceVTBase {
	private static final String VALID_PATIENT_ID = "eb068c3f-3954-50c6-0c67-2b82d29865f0";
	private static final String ANOTHER_VALID_PATIENT_ID = "5858467d-0a49-ad8c-3f76-dcf25067e175";

	private static final Logger logger = LoggerFactory.getLogger(DefaultVT.class.getName());

	private static FhirServerConfig dataServerConfig;
	private static FhirServerConfig termServerConfig;
	
	@BeforeClass
	public static void setUp() throws Exception {
		// Instantiate service location
		instantiateServiceLocation();
		
		if( System.getProperty("test.host") == null ) {
			System.setProperty("test.host", "localhost");
		}

		if(  System.getProperty("test.httpPort") == null ) {
			System.setProperty("test.httpPort", "9080");
		}
		
		if(  System.getProperty("test.httpSslPort") == null ) { 
			System.setProperty("test.httpSslPort", "9443");
		}
		
		if( System.getProperty("test.contextRoot") == null ) {
			System.setProperty("test.contextRoot", "services/cohort");
		}
		
		if( System.getProperty("test.enabledDarkFeatures") == null ) {
			System.setProperty("test.enabledDarkFeatures", SERVICE_ENABLED_DARK_FEATURES_ALL);
		}
		ObjectMapper om = new ObjectMapper();

		String dataClientConfigPath = System.getProperty("test.dataConfig");
		if( dataClientConfigPath != null ) {
			File dataClientConfigFile = new File(dataClientConfigPath);
			dataServerConfig = om.readValue(dataClientConfigFile, FhirServerConfig.class);
		} else {
			fail("Missing required system property 'test.dataConfig'");
		}
		
		String termClientConfigPath = System.getProperty("test.terminologyConfig");
		if( termClientConfigPath != null ) {
			File termClientConfigFile = new File(termClientConfigPath);
			termServerConfig = om.readValue(termClientConfigFile, FhirServerConfig.class);
		} else {
			termServerConfig = dataServerConfig;
		}

	}

	@AfterClass
	public static void tearDown() throws Exception {
		// Run code after all test cases complete
		JsonAssert.resetOptions();
	}

	@Category(DVT.class) // to tag a specific test to be part of DVT (deployment verification test)
	@Test
	/**
	 * Test a successful measure evaluation using Resource.id as the lookup key
	 */
	public void testMeasureEvaluationByMeasureID() throws Exception {
		
		// You want -Denabled.dark.features=all in your Liberty jvm.options
		Assume.assumeTrue(isServiceDarkFeatureEnabled(CohortEngineRestConstants.DARK_LAUNCHED_MEASURE_EVALUATION));
		
		final String RESOURCE = getUrlBase() + CohortServiceAPISpec.CREATE_DELETE_EVALUATION_PATH;
		
		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
		
		Library library = TestHelper.getTemplateLibrary();
		
		Measure measure = TestHelper.getTemplateMeasure(library);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		
		//Files.write( baos.toByteArray(), new File("target/test_measure_v1_0_0.zip"));
		
		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(new DateParameter("2019-07-04")
				, true
				, new DateParameter( "2020-07-04")
				, true));;
		
		MeasureEvaluation requestData = new MeasureEvaluation();
		requestData.setDataServerConfig(dataServerConfig);
		requestData.setTerminologyServerConfig(termServerConfig);
		// This is a patient ID that is assumed to exist in the target FHIR server
		requestData.setPatientId(VALID_PATIENT_ID);
		requestData.setMeasureContext(new MeasureContext(measure.getId(), parameterOverrides));
		requestData.setEvidenceOptions(new MeasureEvidenceOptions(false, MeasureEvidenceOptions.DefineReturnOptions.NONE));
		
		ObjectMapper om = new ObjectMapper();
		System.out.println( om.writeValueAsString(requestData) );
		
		RequestSpecification request = buildBaseRequest(new Headers())
				.queryParam(CohortEngineRestHandler.VERSION, ServiceBuildConstants.DATE)
				.multiPart(CohortEngineRestHandler.REQUEST_DATA_PART, requestData, "application/json")
				.multiPart(CohortEngineRestHandler.MEASURE_PART, "test_measure_v1_0_0.zip", new ByteArrayInputStream(baos.toByteArray()));
		
		ValidatableResponse response = request.post(RESOURCE,getServiceVersion()).then();
		ValidatableResponse vr = runSuccessValidation(response, ContentType.JSON, HttpStatus.SC_OK);
		
		String expected = getJsonFromFile(ServiceAPIGlobalSpec.EXP_FOLDER_TYPE,"measure_evaluation_exp.json");
		String actual = vr.extract().asString();
		
		assertMeasureReportEquals(parser, expected, actual, false);
	}
	
	@Category(DVT.class) // to tag a specific test to be part of DVT (deployment verification test)
	@Test
	/**
	 * Test a successful measure evaluation using identifier and version as the lookup key
	 */
	public void testMeasureEvaluationByMeasureIdentifier() throws Exception {
		
		// You want -Denabled.dark.features=all in your Liberty jvm.options
		Assume.assumeTrue(isServiceDarkFeatureEnabled(CohortEngineRestConstants.DARK_LAUNCHED_MEASURE_EVALUATION));
		
		final String RESOURCE = getUrlBase() + CohortServiceAPISpec.CREATE_DELETE_EVALUATION_PATH;
		
		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
		
		Library library = TestHelper.getTemplateLibrary();
		
		Identifier identifier = new Identifier().setValue("measure-identifier").setSystem("http://ibm.com/health/test");
		Measure measure = TestHelper.getTemplateMeasure(library);
		measure.setIdentifier(Arrays.asList(identifier));
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);
		
		//Files.write( baos.toByteArray(), new File("target/test_measure_v1_0_0.zip"));

		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(new DateParameter("2019-07-04")
				, true
				, new DateParameter( "2020-07-04")
				, true));
		
		MeasureEvaluation requestData = new MeasureEvaluation();
		requestData.setDataServerConfig(dataServerConfig);
		requestData.setTerminologyServerConfig(termServerConfig);
		// This is a patient ID that is assumed to exist in the target FHIR server
		requestData.setPatientId(VALID_PATIENT_ID);
		requestData.setMeasureContext(new MeasureContext(null, parameterOverrides, new com.ibm.cohort.engine.measure.Identifier(identifier.getSystem(), identifier.getValue()), measure.getVersion()));
		requestData.setEvidenceOptions(new MeasureEvidenceOptions(false,MeasureEvidenceOptions.DefineReturnOptions.NONE));
		
		ObjectMapper om = new ObjectMapper();
		System.out.println( om.writeValueAsString(requestData) );
		
		RequestSpecification request = buildBaseRequest(new Headers())
				.queryParam(CohortEngineRestHandler.VERSION, ServiceBuildConstants.DATE)
				.multiPart(CohortEngineRestHandler.REQUEST_DATA_PART, requestData, "application/json")
				.multiPart(CohortEngineRestHandler.MEASURE_PART, "test_measure_v1_0_0.zip", new ByteArrayInputStream(baos.toByteArray()));
		
		ValidatableResponse response = request.post(RESOURCE,getServiceVersion()).then();
		ValidatableResponse vr = runSuccessValidation(response, ContentType.JSON, HttpStatus.SC_OK);
		
		String expected = getJsonFromFile(ServiceAPIGlobalSpec.EXP_FOLDER_TYPE,"measure_evaluation_exp.json");
		String actual = vr.extract().asString();
		
		assertMeasureReportEquals(parser, expected, actual, false);
	}

	@Category(DVT.class) // to tag a specific test to be part of DVT (deployment verification test)
	@Test
	/**
	 * Test a successful measure evaluation using Resource.id as the lookup key
	 */
	public void testPatientListMeasureEvaluation() throws Exception {

		// You want -Denabled.dark.features=all in your Liberty jvm.options
		Assume.assumeTrue(isServiceDarkFeatureEnabled(CohortEngineRestConstants.DARK_LAUNCHED_PATIENT_LIST_MEASURE_EVALUATION));

		final String RESOURCE = getUrlBase() + CohortServiceAPISpec.POST_PATIENT_LIST_EVALUATION_PATH;

		FhirContext fhirContext = FhirContext.forR4();
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);

		Library library = TestHelper.getTemplateLibrary();

		Measure measure = TestHelper.getTemplateMeasure(library);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TestHelper.createMeasureArtifact(baos, parser, measure, library);

		Map<String,Parameter> parameterOverrides = new HashMap<>();
		parameterOverrides.put("Measurement Period", new IntervalParameter(new DateParameter("2019-07-04")
				, true
				, new DateParameter("2020-07-04")
				, true));;

		PatientListMeasureEvaluation requestData = new PatientListMeasureEvaluation();
		requestData.setDataServerConfig(dataServerConfig);
		requestData.setTerminologyServerConfig(termServerConfig);

		// These patients are assumed to exist in the target FHIR server
		List<String> patientIds = new ArrayList<>();
		patientIds.add(VALID_PATIENT_ID);
		patientIds.add(ANOTHER_VALID_PATIENT_ID);
		requestData.setPatientIds(patientIds);
		requestData.setMeasureContext(new MeasureContext(measure.getId(), parameterOverrides));
		requestData.setEvidenceOptions(new MeasureEvidenceOptions(false, MeasureEvidenceOptions.DefineReturnOptions.NONE));

		ObjectMapper om = new ObjectMapper();
		System.out.println( om.writeValueAsString(requestData) );

		RequestSpecification request = buildBaseRequest(new Headers())
				.queryParam(CohortEngineRestHandler.VERSION, ServiceBuildConstants.DATE)
				.multiPart(CohortEngineRestHandler.REQUEST_DATA_PART, requestData, "application/json")
				.multiPart(CohortEngineRestHandler.MEASURE_PART, "test_measure_v1_0_0.zip", new ByteArrayInputStream(baos.toByteArray()));

		ValidatableResponse response = request.post(RESOURCE,getServiceVersion()).then();
		ValidatableResponse vr = runSuccessValidation(response, ContentType.JSON, HttpStatus.SC_OK);

		String expected = getJsonFromFile(ServiceAPIGlobalSpec.EXP_FOLDER_TYPE, "patient_list_measure_evaluation_exp.json");
		String actual = vr.extract().asString();

		assertMeasureReportEquals(parser, expected, actual, true);
	}

	private void assertMeasureReportEquals(IParser parser, String expected, String actual, boolean isPatientListMeasure) {
		MeasureReport expectedReport = parser.parseResource(MeasureReport.class, expected);
		MeasureReport actualReport = parser.parseResource(MeasureReport.class, actual);
		
		// The order is not guaranteed and the Measure ID changes between runs
		assertEquals( expectedReport.getEvaluatedResource().size(), actualReport.getEvaluatedResource().size() );
		expectedReport.setEvaluatedResource(Collections.emptyList());
		actualReport.setEvaluatedResource(Collections.emptyList());
		
		// The period in the report has the timezone encoded, so we can't use a point-in-time
		// snapshot to evaluate equality. The point-in-time might have a different
		// timezone offset than the actual value. This will ignore timezone.
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		assertEquals( sdf.format(expectedReport.getPeriod().getStart()), sdf.format(actualReport.getPeriod().getStart()));
		assertEquals( sdf.format(expectedReport.getPeriod().getEnd()), sdf.format(actualReport.getPeriod().getEnd()));
		expectedReport.setPeriod(null);
		actualReport.setPeriod(null);

		// clear reference id, since those will vary by run
		if (isPatientListMeasure) {
			expectedReport.getContained().forEach(contained -> contained.setId(""));
			actualReport.getContained().forEach(contained -> contained.setId(""));

			expectedReport.getGroup().stream()
					.flatMap(group -> group.getPopulation().stream())
					.map(MeasureReport.MeasureReportGroupPopulationComponent::getSubjectResults)
					.forEach(result -> result.setReference(""));
			actualReport.getGroup().stream()
					.flatMap(group -> group.getPopulation().stream())
					.map(MeasureReport.MeasureReportGroupPopulationComponent::getSubjectResults)
					.forEach(result -> result.setReference(""));
		}
		
		// The remainder should match naturally
		assertJsonEquals(parser.encodeResourceToString(expectedReport),parser.encodeResourceToString(actualReport));
	}

	@Test
	public void testJsonunitAssertSample() {
		assertJsonEquals("{\"test\":{\"a\":1,\"b\":2,\"c\":3}}", "{\"test\":{\"a\":1,\"b\":2,\"c\":3}}");
		assertJsonStructureEquals("{\"test\":{\"a\":1,\"b\":2,\"c\":3}}", "{\"test\":{\"a\":1,\"b\":2,\"c\":3}}");
	}

    /*
     * Match and Assert a specific section or node of JsonObject to
     * ignore fields with null values
     *
     */

    @Test
    public void testIf_responseJsonSpecificNode_ValueNullsAreIgnored() {
            JsonAssert.setOptions(Option.TREATING_NULL_AS_ABSENT);
            assertJsonEquals("{\"test\":{\"a\":1}}",
                 "{\"test\":{\"a\":1, \"b\": null}}");
    }

    /*
     * Match and Assert a specific section or node of JsonObject to
     * ignore values and extra fields
     *
     */

    @Test
    public void testIf_responseJson_IgnoreValuesAndFields() {
            JsonAssert.setOptions(IGNORING_VALUES,Option.IGNORING_EXTRA_FIELDS);
            assertJsonEquals("{\"test\":{\"a\":1,\"b\":2,\"c\":3}}",
            "{\"test\":{\"a\":3,\"b\":2,\"c\":1,\"d\":4}}");
    }


	/*@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	 *
	 * Below are few samples on how to use XMLUnit asserts to compare
	 * XML documents.
	 *
	 * @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	 *
	 */

	@Category(UNDERCONSTRUCTION.class) // tag as mentioned, to ignore any tests to be picked up from continuous integration/automation
	@Test
	public void testXMLUnitAssertForEquality() throws Exception {
        String myControlXML = "<msg><uuid>0x00435A8C</uuid></msg>";
        String myTestXML = "<msg><uuid>0x00435A8C</uuid></msg>";
        String myTestXMLDIFFER = "<msg><localId>2376</localId></msg>";

        assertXMLEqual("comparing test xml to control xml", myControlXML, myTestXML);

        assertXMLNotEqual("test xml differ not similar to control xml", myControlXML, myTestXMLDIFFER);
    }


	@Test
    public void testXMLUnitAssertIdenticalvsSimilar() throws Exception {
        String myControlXML = "<struct><int>3</int><boolean>false</boolean></struct>";
        String myTestXMLIDENTICAL = "<struct><int>3</int><boolean>false</boolean></struct>";
        String myTestXMLSIMILAR = "<struct><boolean>false</boolean><int>3</int></struct>";

        Diff myDiff = new Diff(myControlXML, myTestXMLSIMILAR);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());

        myDiff = new Diff(myControlXML, myTestXMLIDENTICAL);
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }


	@Test
    public void testXMLUnitAssertXPaths() throws Exception {
        String mySolarSystemXML = "<solar-system><planet name='Earth' position='3' supportsLife='yes'/>"
            + "<planet name='Venus' position='4'/></solar-system>";
        assertXpathExists("//planet[@name='Earth']", mySolarSystemXML);
        assertXpathNotExists("//star[@name='alpha centauri']", mySolarSystemXML);
        assertXpathsEqual("//planet[@name='Earth']", "//planet[@position='3']", mySolarSystemXML);
        assertXpathsNotEqual("//planet[@name='Venus']", "//planet[@supportsLife='yes']", mySolarSystemXML);
    }


	@Test
    public void testXMLUnitAssertXPathValues() throws Exception {
        String myJavaFlavours = "<java-flavours><jvm current='some platforms'>1.1.x</jvm>"
            + "<jvm current='no'>1.2.x</jvm><jvm current='yes'>1.3.x</jvm>"
            + "<jvm current='yes' latest='yes'>1.4.x</jvm></java-flavours>";
        assertXpathEvaluatesTo("1.4.x", "//jvm[@latest='yes']", myJavaFlavours);
        assertXpathEvaluatesTo("2", "count(//jvm[@current='yes'])", myJavaFlavours);
        assertXpathValuesEqual("//jvm[4]/@latest", "//jvm[4]/@current", myJavaFlavours);
        assertXpathValuesNotEqual("//jvm[2]/@current", "//jvm[3]/@current", myJavaFlavours);
    }

    @Test
	public void testValueSetUpload(){
		final String RESOURCE = getUrlBase() + "/{version}/valueset";
		Assume.assumeTrue(isServiceDarkFeatureEnabled(CohortEngineRestConstants.DARK_LAUNCHED_VALUE_SET_UPLOAD));

		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String fhirConfigjson = "";
		try {
			fhirConfigjson = om.writeValueAsString(dataServerConfig);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			fail();
		}

		RequestSpecification request = buildBaseRequest(new Headers())
				.param(CohortEngineRestHandler.VERSION, ServiceBuildConstants.DATE)
				.queryParam(CohortEngineRestHandler.UPDATE_IF_EXISTS_PARM, false)
				.multiPart(CohortEngineRestHandler.FHIR_DATA_SERVER_CONFIG_PART, fhirConfigjson, "application/json")
				.multiPart(CohortEngineRestHandler.VALUE_SET_PART, new File("src/test/resources/2.16.840.1.113762.1.4.1114.7.xlsx"));

		ValidatableResponse response = request.post(RESOURCE, getServiceVersion()).then();
		runSuccessValidation(response, ContentType.JSON, HttpStatus.SC_CREATED);
	}

	@Test
	public void testValueSetAlreadyExists(){
		testValueSetUpload();
		final String RESOURCE = getUrlBase() + "/{version}/valueset";
		
		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String fhirConfigjson = "";
		try {
			fhirConfigjson = om.writeValueAsString(dataServerConfig);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			fail();
		}

		RequestSpecification request = buildBaseRequest(new Headers())
				.param(CohortEngineRestHandler.VERSION, ServiceBuildConstants.DATE)
				.queryParam(CohortEngineRestHandler.UPDATE_IF_EXISTS_PARM, false)
				.multiPart(CohortEngineRestHandler.FHIR_DATA_SERVER_CONFIG_PART, fhirConfigjson, "application/json")
				.multiPart(CohortEngineRestHandler.VALUE_SET_PART, new File("src/test/resources/2.16.840.1.113762.1.4.1114.7.xlsx"));

		ValidatableResponse response = request.post(RESOURCE, getServiceVersion()).then();
		runSuccessValidation(response, ContentType.JSON, HttpStatus.SC_CONFLICT);
	}

	@Test
	public void testValueSetOverride(){
		testValueSetUpload();
		final String RESOURCE = getUrlBase() + "/{version}/valueset";

		// Create the metadata part of the request
		ObjectMapper om = new ObjectMapper();
		String fhirConfigjson = "";
		try {
			fhirConfigjson = om.writeValueAsString(dataServerConfig);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			fail();
		}

		RequestSpecification request = buildBaseRequest(new Headers())
				.param(CohortEngineRestHandler.VERSION, ServiceBuildConstants.DATE)
				.queryParam(CohortEngineRestHandler.UPDATE_IF_EXISTS_PARM, true)
				.multiPart(CohortEngineRestHandler.FHIR_DATA_SERVER_CONFIG_PART, fhirConfigjson, "application/json")
				.multiPart(CohortEngineRestHandler.VALUE_SET_PART, new File("src/test/resources/2.16.840.1.113762.1.4.1114.7.xlsx"));

		ValidatableResponse response = request.post(RESOURCE, getServiceVersion()).then();
		runSuccessValidation(response, ContentType.JSON, HttpStatus.SC_CREATED);
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}


	@After
	public void cleanUp(){
		//get the fhir client object used to call to FHIR
		FhirContext ctx = FhirContext.forR4();
		DefaultFhirClientBuilder builder = new DefaultFhirClientBuilder(ctx);
		IGenericClient terminologyClient = builder.createFhirClient(dataServerConfig);
		terminologyClient.delete().resourceConditionalByType("ValueSet").where(ValueSet.URL.matches().value("http://cts.nlm.nih.gov/fhir/ValueSet/testValueSet")).execute();
	}
}
