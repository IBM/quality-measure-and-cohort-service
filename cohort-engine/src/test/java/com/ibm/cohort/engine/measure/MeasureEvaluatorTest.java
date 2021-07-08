/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.measure;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.ibm.cohort.engine.cdm.CDMConstants.MEASURE_PARAMETER_URL;
import static com.ibm.cohort.engine.cdm.CDMConstants.MEASURE_PARAMETER_VALUE_URL;
import static com.ibm.cohort.engine.cdm.CDMConstants.PARAMETER_DEFAULT_URL;
import static com.ibm.cohort.engine.cdm.CDMConstants.PARAMETER_VALUE_URL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.ParameterDefinition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UriType;
import org.junit.Before;
import org.junit.Test;
import org.opencds.cqf.common.evaluation.MeasurePopulationType;
import org.opencds.cqf.cql.engine.exception.InvalidOperatorArgument;

import com.ibm.cohort.engine.LibraryFormat;
import com.ibm.cohort.engine.cdm.CDMConstants;
import com.ibm.cohort.engine.cqfruler.MeasureSupplementalDataEvaluation;
import com.ibm.cohort.engine.measure.evidence.MeasureEvidenceOptions;
import com.ibm.cohort.engine.measure.evidence.MeasureEvidenceOptions.DefineReturnOptions;
import com.ibm.cohort.engine.measure.parameter.UnsupportedFhirTypeException;
import com.ibm.cohort.engine.parameter.BooleanParameter;
import com.ibm.cohort.engine.parameter.CodeParameter;
import com.ibm.cohort.engine.parameter.ConceptParameter;
import com.ibm.cohort.engine.parameter.DateParameter;
import com.ibm.cohort.engine.parameter.DatetimeParameter;
import com.ibm.cohort.engine.parameter.DecimalParameter;
import com.ibm.cohort.engine.parameter.IntegerParameter;
import com.ibm.cohort.engine.parameter.IntervalParameter;
import com.ibm.cohort.engine.parameter.Parameter;
import com.ibm.cohort.engine.parameter.QuantityParameter;
import com.ibm.cohort.engine.parameter.RatioParameter;
import com.ibm.cohort.engine.parameter.StringParameter;
import com.ibm.cohort.engine.parameter.TimeParameter;

public class MeasureEvaluatorTest extends BaseMeasureTest {

	public static final String DEFAULT_VERSION = "1.0.0";
	
	private MeasureEvaluator evaluator;
	
	@Before
	public void setUp() {
		super.setUp();

		FHIRClientContext clientContext = new FHIRClientContext.Builder()
				.withDefaultClient(client)
				.build();
		evaluator = new R4MeasureEvaluatorBuilder()
				.withClientContext(clientContext)
				.build();
	}

	@Test
	public void elm_in_initial_population___cohort_evaluated_correctly() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.xml",
				LibraryFormat.MIME_TYPE_APPLICATION_ELM_XML);

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null);
		assertNotNull(report);
		assertEquals(1, report.getGroupFirstRep().getPopulationFirstRep().getCount());

		verify(1, getRequestedFor(urlEqualTo("/Patient/123")));

		// These ensure that the cache is working
		verify(1, getRequestedFor(urlMatching("/Library\\?url=http.*")));
		verify(1, getRequestedFor(urlEqualTo("/Library?name%3Aexact=" + library.getName() + "&version=1.0.0")));
	}
	
	@Test
	public void elm_and_cql_in_initial_population___cohort_evaluated_correctly() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.cql",
				"text/cql", "cql/fhir-measure/test-dummy-populations.xml", "application/elm+xml");

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null);
		assertNotNull(report);
		assertEquals(1, report.getGroupFirstRep().getPopulationFirstRep().getCount());
	}

	@Test
	public void in_initial_population_denominator_and_nothing_else___proportion_evaluated_correctly() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.cql");

		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null);
		assertNotNull(report);
		assertEquals(expressionsByPopulationType.size(), report.getGroupFirstRep().getPopulation().size());
		for (MeasureReport.MeasureReportGroupPopulationComponent pop : report.getGroupFirstRep().getPopulation()) {
			MeasurePopulationType type = MeasurePopulationType.fromCode(pop.getCode().getCodingFirstRep().getCode());
			switch (type) {
			case INITIALPOPULATION:
				assertEquals(1, pop.getCount());
				break;
			case DENOMINATOR:
				assertEquals(1, pop.getCount());
				break;
			case NUMERATOR:
				assertEquals(0, pop.getCount());
				break;
			default:
				fail("Unexpected population type in result");
			}
		}
	}

	@Test
	public void in_initial_population_and_denominator___caregaps_evaluated_correctly() throws Exception {

		Map<String, Integer> careGapExpectations = new HashMap<>();
		careGapExpectations.put("CareGap1", 0);
		careGapExpectations.put("CareGap2", 1);

		runCareGapTest(null, careGapExpectations);
	}

	@Test
	public void not_in_initial_population___caregaps_evaluated_correctly() throws Exception {

		Map<String, Parameter> parameters = new HashMap<>();
		parameters.put("InInitialPopulation", new BooleanParameter(false));

		expectationsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, 0);
		expectationsByPopulationType.put(MeasurePopulationType.DENOMINATOR, 0);

		Map<String, Integer> expectationsByCareGap = new HashMap<>();
		expectationsByCareGap.put("CareGap1", 0);
		expectationsByCareGap.put("CareGap2", 0);

		runCareGapTest(parameters, expectationsByCareGap);
	}

	@Test
	public void not_in_denominator___caregaps_evaluated_correctly() throws Exception {

		Map<String, Parameter> parameters = new HashMap<>();
		parameters.put("InDenominator", new BooleanParameter(false));

		expectationsByPopulationType.put(MeasurePopulationType.DENOMINATOR, 0);

		Map<String, Integer> expectationsByCareGap = new HashMap<>();
		expectationsByCareGap.put("CareGap1", 0);
		expectationsByCareGap.put("CareGap2", 0);

		runCareGapTest(parameters, expectationsByCareGap);
	}

	@Test
	public void in_denominator_and_denominator_exclusions___caregaps_evaluated_correctly() throws Exception {

		Map<String, Parameter> parameters = new HashMap<>();
		parameters.put("InDenominator", new BooleanParameter(true));
		parameters.put("InDenominatorExclusion", new BooleanParameter(true));

		expectationsByPopulationType.put(MeasurePopulationType.DENOMINATOR, 0);
		expectationsByPopulationType.put(MeasurePopulationType.DENOMINATOREXCLUSION, 1);

		Map<String, Integer> expectationsByCareGap = new HashMap<>();
		expectationsByCareGap.put("CareGap1", 0);
		expectationsByCareGap.put("CareGap2", 0);

		runCareGapTest(parameters, expectationsByCareGap);
	}

	@Test
	public void in_numerator_and_numerator_exclusions___caregaps_evaluated_correctly() throws Exception {

		Map<String, Parameter> parameters = new HashMap<>();
		parameters.put("InNumerator", new BooleanParameter(true));
		parameters.put("InNumeratorExclusion", new BooleanParameter(true));

		expectationsByPopulationType.put(MeasurePopulationType.NUMERATOR, 0);
		expectationsByPopulationType.put(MeasurePopulationType.NUMERATOREXCLUSION, 1);

		Map<String, Integer> expectationsByCareGap = new HashMap<>();
		expectationsByCareGap.put("CareGap1", 0);
		expectationsByCareGap.put("CareGap2", 1);

		runCareGapTest(parameters, expectationsByCareGap);
	}
	
	@Test
	public void in_one_initial_population_for_two_measures___two_measure_reports_returned() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.cql");

		String measure1Name = "ProportionMeasureName1";
		Measure measure1 = getCareGapMeasure(measure1Name, library, expressionsByPopulationType, "CareGap1",
											"CareGap2");
		mockFhirResourceRetrieval(measure1);

		String measure2Name = "ProportionMeasureName2";
		Measure measure2 = getCareGapMeasure(measure2Name, library, expressionsByPopulationType, "CareGap1",
											"CareGap2");
		mockFhirResourceRetrieval(measure2);

		Map<String, Parameter> passingParameters = new HashMap<>();
		passingParameters.put("InInitialPopulation", new BooleanParameter(true));

		Map<String, Parameter> failingParameters = new HashMap<>();
		failingParameters.put("InInitialPopulation", new BooleanParameter(false));

		List<MeasureContext> measureContexts = new ArrayList<>();
		measureContexts.add(new MeasureContext(measure1.getId(), passingParameters));
		measureContexts.add(new MeasureContext(measure2.getId(), failingParameters));

		List<MeasureReport> measureReports = evaluator.evaluatePatientMeasures(patient.getId(), measureContexts);
		assertEquals(2, measureReports.size());
		// Make sure reports have measure references with meta version included
		assertEquals("Measure/" + measure1Name + "/_history/" + BaseMeasureTest.MEASURE_META_VERSION_ID, measureReports.get(0).getMeasure());
		assertEquals("Measure/" + measure2Name + "/_history/" + BaseMeasureTest.MEASURE_META_VERSION_ID, measureReports.get(1).getMeasure());
	}
	
	@Test
	public void multilevel_library_dependencies___successfully_loaded_and_evaluated() throws Exception {
		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library nestedHelperLibrary = mockLibraryRetrieval("NestedChild", DEFAULT_VERSION, "cql/fhir-measure-nested-libraries/test-nested-child.cql");
		
		Library helperLibrary = mockLibraryRetrieval("Child", DEFAULT_VERSION, "cql/fhir-measure-nested-libraries/test-child.cql");
		helperLibrary.addRelatedArtifact( asRelation(nestedHelperLibrary) );
		
		Library library = mockLibraryRetrieval("Parent", DEFAULT_VERSION, "cql/fhir-measure-nested-libraries/test-parent.cql");
		library.addRelatedArtifact( asRelation(helperLibrary) );
		
		Measure measure = getCareGapMeasure("NestedLibraryMeasure", library, expressionsByPopulationType, "CareGap1",
											"CareGap2");
		mockFhirResourceRetrieval(measure);

		Map<String, Parameter> passingParameters = new HashMap<>();
		passingParameters.put("InInitialPopulation", new BooleanParameter(true));

		List<MeasureContext> measureContexts = new ArrayList<>();
		measureContexts.add(new MeasureContext(measure.getId(), passingParameters));

		List<MeasureReport> reports = evaluator.evaluatePatientMeasures(patient.getId(), measureContexts);
		assertEquals(1, reports.size());
		
		MeasureReport report = reports.get(0);
		verifyStandardPopulationCounts(report);
	}
	
	@Test
	public void id_based_library_link___successfully_loaded_and_evaluated() throws Exception {
		mockFhirResourceRetrieval("/metadata", getCapabilityStatement());

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);
		
		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.cql");
		
		Measure measure = getCareGapMeasure("IDBasedLibraryMeasure", library, expressionsByPopulationType, "CareGap1",
											"CareGap2");
		
		measure.setLibrary( Arrays.asList( new CanonicalType( "Library/" + library.getId()) ) );
		mockFhirResourceRetrieval(measure);

		Map<String, Parameter> passingParameters = new HashMap<>();
		passingParameters.put("InInitialPopulation", new BooleanParameter(true));

		List<MeasureContext> measureContexts = new ArrayList<>();
		measureContexts.add(new MeasureContext(measure.getId(), passingParameters));

		List<MeasureReport> reports = evaluator.evaluatePatientMeasures(patient.getId(), measureContexts);
		assertEquals(1, reports.size());
		
		MeasureReport report = reports.get(0);
		verifyStandardPopulationCounts(report);
		
		verify(1, getRequestedFor(urlEqualTo("/Library/" + library.getId())));
	}

	@Test
	public void measure_default_valid() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestAdultMales", DEFAULT_VERSION, "cql/fhir-measure/test-parameter-defaults.cql");

		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);

		measure.addExtension(createMeasureParameter("SomeAge", new IntegerType(20)));
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions());
		assertNotNull(report);
	}

	@Test(expected = InvalidOperatorArgument.class)
	public void measure_default_invalid_type() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestAdultMales", DEFAULT_VERSION, "cql/fhir-measure/test-parameter-defaults.cql");

		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);

		measure.addExtension(createMeasureParameter("SomeAge", new StringType("invalid")));

		mockFhirResourceRetrieval(measure);

		evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions());
	}

	@Test(expected = UnsupportedFhirTypeException.class)
	public void measure_default_unsupported_type() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestAdultMales", DEFAULT_VERSION, "cql/fhir-measure/test-parameter-defaults.cql");

		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);

		Address unsupportedType = new Address();
		unsupportedType.setCity("Cleaveland");

		measure.addExtension(createMeasureParameter("SomeAge", unsupportedType));
		mockFhirResourceRetrieval(measure);

		evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions());
	}

	private void runCareGapTest(Map<String, Parameter> parameters, Map<String, Integer> careGapExpectations)
			throws ParseException, Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.cql");

		Measure measure = getCareGapMeasure("ProportionMeasureName", library, expressionsByPopulationType, "CareGap1",
				"CareGap2");
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), parameters);
		assertNotNull(report);
		assertPopulationExpectations(measure, report, careGapExpectations);
	}

	protected void assertPopulationExpectations(Measure measure, MeasureReport report,
			Map<String, Integer> expectations) {
		assertEquals(measure.getGroupFirstRep().getPopulation().size(),
				report.getGroupFirstRep().getPopulation().size());
		List<MeasureReport.MeasureReportGroupPopulationComponent> careGapPopulations = verifyStandardPopulationCounts(
				report);

		assertEquals(2, careGapPopulations.size());
		for (MeasureReport.MeasureReportGroupPopulationComponent pop : careGapPopulations) {
			assertEquals(CDMConstants.CDM_CODE_SYSTEM_MEASURE_POPULATION_TYPE,
					pop.getCode().getCodingFirstRep().getSystem());
			assertEquals(CDMConstants.CARE_GAP, pop.getCode().getCodingFirstRep().getCode());
			assertTrue(pop.getId().startsWith("CareGap")); // this is part of the test fixture and may not match
															// production behavior
			assertEquals(pop.getId(), expectations.get(pop.getId()).intValue(), pop.getCount());
		}
	}
	
	private Library setupDefineReturnLibrary() throws Exception {
		Library fhirHelpers = mockLibraryRetrieval("FHIRHelpers", "4.0.0", "cql/fhir-helpers/FHIRHelpers.cql");
		Library library2 = mockLibraryRetrieval("TestAdultMales2", DEFAULT_VERSION, "cql/fhir-measure/test-adult-males2.cql");
		Library library3 = mockLibraryRetrieval("TestAdultMales3", DEFAULT_VERSION, "cql/fhir-measure/test-adult-males3.cql");
		Library library = mockLibraryRetrieval("TestAdultMales", DEFAULT_VERSION, "cql/fhir-measure/test-adult-males.cql");

		library.addRelatedArtifact(asRelation(fhirHelpers));
		library.addRelatedArtifact(asRelation(library2));
		library.addRelatedArtifact(asRelation(library3));
		
		return library;
	}
	
	@Test
	public void in_populations_defines_returned() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		
		//Add 2 names to test list
		HumanName name1 = new HumanName();
		name1.setFamily("Jones");
		HumanName name2 = new HumanName();
		name2.setFamily("Smith");
		patient.setName(Arrays.asList(name1, name2));
		
		// Add marital status to test codeable concept
		CodeableConcept maritalStatus = new CodeableConcept();
		Coding maritalCoding = new Coding();
		maritalCoding.setCode("M");
		maritalCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus");
		maritalCoding.setDisplay("Married");
		
		maritalStatus.setCoding(Arrays.asList(maritalCoding));
		maritalStatus.setText("Married");
		
		patient.setMaritalStatus(maritalStatus);
		
		mockFhirResourceRetrieval(patient);
		Library library = setupDefineReturnLibrary();
		
		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions(true, DefineReturnOptions.ALL));
		
		assertNotNull(report);
		
		assertFalse(report.getEvaluatedResource().isEmpty());
		
		List<Extension> returnedExtensions = report.getExtensionsByUrl(CDMConstants.EVIDENCE_URL);
		assertFalse(returnedExtensions.isEmpty());
		assertEquals(30, returnedExtensions.size());
	}
	
	@Test
	public void in_populations_evaluated_define_only_returned() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = setupDefineReturnLibrary();
		
		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions(false, DefineReturnOptions.ALL));
		assertNotNull(report);
		
		assertTrue(report.getEvaluatedResource().isEmpty());
		assertFalse(report.getExtensionsByUrl(CDMConstants.EVIDENCE_URL).isEmpty());
	}
	
	@Test
	public void in_populations_evaluated_boolean_define_only_returned() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = setupDefineReturnLibrary();
		
		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions(false, DefineReturnOptions.BOOLEAN));
		assertNotNull(report);
		
		assertTrue(report.getEvaluatedResource().isEmpty());
		assertFalse(report.getExtensionsByUrl(CDMConstants.EVIDENCE_URL).isEmpty());
	}
	
	@Test
	public void in_populations_evaluated_resources_returned() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = setupDefineReturnLibrary();
		
		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions(true, DefineReturnOptions.NONE));
		assertNotNull(report);
		
		assertFalse(report.getEvaluatedResource().isEmpty());
		assertEquals(null, report.getExtensionByUrl(CDMConstants.EVIDENCE_URL));
	}
	
	@Test
	public void in_populations_no_evaluated_resources_returned() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = setupDefineReturnLibrary();
		
		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions());
		assertNotNull(report);
		
		assertTrue(report.getEvaluatedResource().isEmpty());
		assertEquals(null, report.getExtensionByUrl(CDMConstants.EVIDENCE_URL));
	}

	private Extension createMeasureParameter(String name, Type defaultValue) {
		Extension measureParameter = new Extension();
		measureParameter.setUrl(MEASURE_PARAMETER_URL);

		ParameterDefinition parameterDefinition = new ParameterDefinition();
		parameterDefinition.setName(name);
		measureParameter.setValue(parameterDefinition);

		Extension defaultValueExtension = new Extension();
		defaultValueExtension.setUrl(PARAMETER_DEFAULT_URL);
		defaultValueExtension.setValue(defaultValue);
		parameterDefinition.addExtension(defaultValueExtension);

		return measureParameter;
	}
	
	@Test
	public void in_populations_supplemental_data() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = setupDefineReturnLibrary();
		
		expressionsByPopulationType.clear();
		expressionsByPopulationType.put(MeasurePopulationType.INITIALPOPULATION, INITIAL_POPULATION);
		expressionsByPopulationType.put(MeasurePopulationType.DENOMINATOR, DENOMINATOR);
		expressionsByPopulationType.put(MeasurePopulationType.NUMERATOR, NUMERATOR);

		Measure measure = getProportionMeasure("ProportionMeasureName", library, expressionsByPopulationType);
		measure.setSupplementalData(Arrays.asList(createSupplementalDataComponent("SDE Sex", MeasureSupplementalDataEvaluation.SDE_SEX)));
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions());
		assertNotNull(report);
		
		// EvaluatedResource should contain a reference to an observation record created for supplemental data, despite the evidence options indicating returning no evaluatedResources
		assertEquals(1, report.getEvaluatedResource().size());
		
		// See MeasureSupplementalDataEvaluationTest for more details on what is returned here
	}

	@Test
	public void measure_report_generated___named_parameters_on_measure_report() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.xml",
											   LibraryFormat.MIME_TYPE_APPLICATION_ELM_XML);

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);
		mockFhirResourceRetrieval(measure);

		Map<String, Parameter> parameterMap = new HashMap<>();
		parameterMap.put("integerParam", new IntegerParameter(1));
		parameterMap.put("decimalParam", new DecimalParameter("1.0"));
		parameterMap.put("stringParam", new StringParameter("1"));
		parameterMap.put("booleanParam", new BooleanParameter(false));
		parameterMap.put("datetimeParam", new DatetimeParameter("2020-01-01"));
		parameterMap.put("dateParam", new DateParameter("2020-01-01"));
		parameterMap.put("timeParam", new TimeParameter("01:00:00"));
		parameterMap.put("quantityParam", new QuantityParameter("1.0", "ml"));
		parameterMap.put("ratioParam", new RatioParameter(new QuantityParameter("1.0", "ml"), new QuantityParameter("2.0", "ml")));
		parameterMap.put("codeParam", new CodeParameter("1", "2", "3", "4"));
		parameterMap.put("conceptParam", new ConceptParameter("1", new CodeParameter("1", "2", "3", "4")));
		parameterMap.put("datetimeIntervalParam", new IntervalParameter(new DatetimeParameter("2020-01-01"), true, new DatetimeParameter("2021-01-01"), true));
		parameterMap.put("quantityIntervalParam", new IntervalParameter(new QuantityParameter("1.0", "ml"), true, new QuantityParameter("2.0", "ml"), true));

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), parameterMap);
		assertNotNull(report);

		List<String> parameterNames = report.getExtension()
				.stream()
				.filter(x -> x.getUrl().equals(MEASURE_PARAMETER_VALUE_URL))
				.map(x -> (ParameterDefinition) x.getValue())
				.map(ParameterDefinition::getName)
				.collect(Collectors.toList());

		// Expected parameters are the ones listed above, plus the special parameters
		// measurement period and product line
		assertEquals(parameterMap.size() + 2, parameterNames.size());

		assertTrue(parameterNames.containsAll(parameterMap.keySet()));
		assertTrue(parameterNames.contains(CDMConstants.MEASUREMENT_PERIOD));
		assertTrue(parameterNames.contains(CDMConstants.PRODUCT_LINE));
	}

	@Test
	public void measure_report_generated___FHIR_measure_parameters_on_measure_report() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.xml",
											   LibraryFormat.MIME_TYPE_APPLICATION_ELM_XML);

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);

		Map<String, Type> measureParameters = new HashMap<>();
		measureParameters.put("base64Param", new Base64BinaryType("AAA"));
		measureParameters.put("booleanParam", new BooleanType(false));
		measureParameters.put("dateParam", new DateType("2020-01-01"));
		measureParameters.put("dateTimeParam", new DateTimeType("2020-01-01T12:00:00"));
		measureParameters.put("decimalParam", new DecimalType(12.0));
		measureParameters.put("instantParam", new InstantType("2020-01-01T12:00:00-04:00"));
		measureParameters.put("integerParam", new IntegerType(1));
		measureParameters.put("stringParam", new StringType("str"));
		measureParameters.put("timeParam", new TimeType("05:30:00"));
		measureParameters.put("uriParam", new UriType("abcde"));
		measureParameters.put("codeableConceptParam",
							  new CodeableConcept()
									  .setText("display")
									  .addCoding(new Coding().setCode("val").setSystem("sys").setDisplay("display")));
		measureParameters.put("codingParam", new Coding().setCode("v").setSystem("s").setDisplay("d"));
		measureParameters.put("periodParam", new Period().setStart(new Date(1)).setEnd(new Date(2)));
		measureParameters.put("quantityParam", new Quantity().setValue(1).setUnit("g"));
		measureParameters.put("rangeParam", new Range()
				.setLow(new Quantity().setUnit("g").setValue(1))
				.setHigh(new Quantity().setUnit("g").setValue(5)));
		measureParameters.put("ratioParam", new Ratio()
				.setNumerator(new Quantity().setUnit("g").setValue(1))
				.setDenominator(new Quantity().setUnit("g").setValue(5)));

		List<Extension> parameterExtensions = measureParameters.entrySet().stream()
				.map(x -> createMeasureParameter(x.getKey(), x.getValue()))
				.collect(Collectors.toList());

		measure.setExtension(parameterExtensions);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null);
		assertNotNull(report);

		List<String> parameterNames = report.getExtension()
				.stream()
				.filter(x -> x.getUrl().equals(MEASURE_PARAMETER_VALUE_URL))
				.map(x -> (ParameterDefinition) x.getValue())
				.map(ParameterDefinition::getName)
				.collect(Collectors.toList());

		// Expected parameters are the ones listed above, plus the special parameters
		// measurement period and product line
		assertEquals(measureParameters.size() + 2, parameterNames.size());

		assertTrue(parameterNames.containsAll(measureParameters.keySet()));
		assertTrue(parameterNames.contains(CDMConstants.MEASUREMENT_PERIOD));
		assertTrue(parameterNames.contains(CDMConstants.PRODUCT_LINE));
	}

	@Test
	public void measure_report_generated___java_overrides_overwrite_measure_params() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.xml",
											   LibraryFormat.MIME_TYPE_APPLICATION_ELM_XML);

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);

		String duplicateParamName = "duplicateParam";
		int fhirMeasureIntValue = 10;
		int javaParameterIntValue = 99;

		measure.addExtension(createParameterExtension(duplicateParamName, new IntegerType(fhirMeasureIntValue)));

		mockFhirResourceRetrieval(measure);

		Map<String, Parameter> parameterMap = new HashMap<>();
		parameterMap.put(duplicateParamName, new IntegerParameter(javaParameterIntValue));

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), parameterMap);
		assertNotNull(report);

		// Make sure report only contained one entry for the duplicate parameter
		List<Type> filteredReportParams = report.getExtension().stream()
				.filter(x -> x.getUrl().equals(MEASURE_PARAMETER_VALUE_URL))
				.map(x -> (ParameterDefinition) x.getValue())
				.filter(x -> x.getName().equals(duplicateParamName))
				.map(x -> x.getExtensionByUrl(PARAMETER_VALUE_URL).getValue())
				.collect(Collectors.toList());

		assertEquals(1, filteredReportParams.size());

		// Sanity check input parameter values were different before checking for correct value
		assertNotEquals(fhirMeasureIntValue, javaParameterIntValue);
		assertEquals(javaParameterIntValue, ((IntegerType) filteredReportParams.get(0)).getValue().intValue());
	}

	@Test
	public void measure_report_generated___datetime_parameters_on_report_in_utc() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.xml",
											   LibraryFormat.MIME_TYPE_APPLICATION_ELM_XML);

		String fhirDefaultDatetimeParamterName = "fhirDatetimeParamDefault";
		String fhirTimezoneDatetimeParameterName = "fhirDatetimeParamGMTPlus4";

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);
		measure.addExtension(createParameterExtension(fhirDefaultDatetimeParamterName, new DateTimeType("2020-04-04")));
		measure.addExtension(createParameterExtension(fhirTimezoneDatetimeParameterName, new DateTimeType("2020-04-04T00:00:00.0+04:00")));
		mockFhirResourceRetrieval(measure);

		String javaDefaultDatetimeParamterName = "javaDatetimeParamDefault";
		String javaTimezoneDatetimeParameterName = "javaDatetimeParamGMTPlus4";

		Map<String, Parameter> parameterMap = new HashMap<>();
		parameterMap.put(javaDefaultDatetimeParamterName, new DatetimeParameter("2020-01-01"));
		parameterMap.put(javaTimezoneDatetimeParameterName, new DatetimeParameter("2020-01-01T00:00:00.0+04:00"));

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), parameterMap);
		assertNotNull(report);

		Map<String, DateTimeType> parameterNames = report.getExtension()
				.stream()
				.filter(x -> x.getUrl().equals(MEASURE_PARAMETER_VALUE_URL))
				.map(x -> (ParameterDefinition) x.getValue())
				.filter(x -> x.getExtensionByUrl(PARAMETER_VALUE_URL).getValue() instanceof DateTimeType)
				.collect(Collectors.toMap(ParameterDefinition::getName, x -> (DateTimeType)x.getExtensionByUrl(PARAMETER_VALUE_URL).getValue()));

		DateTimeType javaDefaultResult = parameterNames.get(javaDefaultDatetimeParamterName);
		DateTimeType javaTimezoneResult = parameterNames.get(javaTimezoneDatetimeParameterName);

		assertTrue(new DateTimeType("2020-01-01T00:00:00.000Z").equalsUsingFhirPathRules(javaDefaultResult));
		assertEquals(TimeZone.getTimeZone("UTC"), javaDefaultResult.getTimeZone());
		assertTrue(new DateTimeType("2019-12-31T20:00:00.000Z").equalsUsingFhirPathRules(javaTimezoneResult));
		assertEquals(TimeZone.getTimeZone("UTC"), javaTimezoneResult.getTimeZone());

		DateTimeType fhirDefaultResult = parameterNames.get(fhirDefaultDatetimeParamterName);
		DateTimeType fhirTimezoneResult = parameterNames.get(fhirTimezoneDatetimeParameterName);

		assertTrue(new DateTimeType("2020-04-04T00:00:00.000Z").equalsUsingFhirPathRules(fhirDefaultResult));
		assertEquals(TimeZone.getTimeZone("UTC"), fhirDefaultResult.getTimeZone());
		assertTrue(new DateTimeType("2020-04-03T20:00:00.000Z").equalsUsingFhirPathRules(fhirTimezoneResult));
		assertEquals(TimeZone.getTimeZone("UTC"), fhirTimezoneResult.getTimeZone());
	}

	@Test
	public void in_initial_population_when_engine_run_in_utc__engine_defaults_to_utc() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient = getPatient("123", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient);

		// Test CQL written to pass when engine run with a timezone of UTC
		// and should fail otherwise
		Library library = mockLibraryRetrieval("TestDatetimeDefaultTimezones", DEFAULT_VERSION,
											   "cql/fhir-measure/test-datetime-default-timezones.cql", "text/cql");

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);
		mockFhirResourceRetrieval(measure);

		MeasureReport report = evaluator.evaluatePatientMeasure(measure.getId(), patient.getId(), null, new MeasureEvidenceOptions(false, DefineReturnOptions.BOOLEAN));

		assertNotNull(report);

		report.getExtension().stream()
				.filter(x -> x.getUrl().equals(CDMConstants.EVIDENCE_URL))
				.forEach(x -> validateBooleanEvidence(x, true));
	}

	@Test
	public void measure_patient_list_report_generated() throws Exception {
		CapabilityStatement metadata = getCapabilityStatement();
		mockFhirResourceRetrieval("/metadata", metadata);

		Patient patient1 = getPatient("1", AdministrativeGender.MALE, "1970-10-10");
		mockFhirResourceRetrieval(patient1);
		Patient patient2 = getPatient("2", AdministrativeGender.FEMALE, "1970-10-10");
		mockFhirResourceRetrieval(patient2);

		List<String> patientIds = new ArrayList<>();
		patientIds.add(patient1.getId());
		patientIds.add(patient2.getId());

		Library library = mockLibraryRetrieval("TestDummyPopulations", DEFAULT_VERSION, "cql/fhir-measure/test-dummy-populations.cql",
		                                       "text/cql", "cql/fhir-measure/test-dummy-populations.xml", "application/elm+xml");

		Measure measure = getCohortMeasure("CohortMeasureName", library, INITIAL_POPULATION);

		MeasureReport report = evaluator.evaluatePatientListMeasure(patientIds, measure, null, null);

		assertNotNull(report);
		List<ListResource.ListEntryComponent> patientList = ((ListResource) report.getContained().get(0)).getEntry();
		List<String> actualPatientIds = patientList.stream().map(a -> StringUtils.removeStart(a.getItem().getReference(), "Patient/")).collect(Collectors.toList());

		assertThat(actualPatientIds, Matchers.containsInAnyOrder(patient1.getId(), patient2.getId()));
	}

	private void validateBooleanEvidence(Extension evidenceExtension, Boolean expectedValue) {
		String name = evidenceExtension.getExtensionByUrl(CDMConstants.EVIDENCE_TEXT_URL).getValue().toString();
		assertEquals("cql define " + name + " did not match expected value",
					 new BooleanType(expectedValue).booleanValue(),
					 ((BooleanType) evidenceExtension.getExtensionByUrl(CDMConstants.EVIDENCE_VALUE_URL).getValue()).booleanValue());
	}

	private Extension createParameterExtension(String name, Type innerValue) {
		ParameterDefinition parameterDefinition = new ParameterDefinition()
				.setName(name)
				.setUse(ParameterDefinition.ParameterUse.IN)
				.setType(innerValue.fhirType());

		parameterDefinition.addExtension(new Extension()
												 .setUrl(PARAMETER_DEFAULT_URL)
												 .setValue(innerValue));

		return new Extension().setUrl(MEASURE_PARAMETER_URL).setValue(parameterDefinition);
	}
}
