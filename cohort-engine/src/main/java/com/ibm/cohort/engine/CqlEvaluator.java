/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.FunctionDef;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.cql.engine.execution.CqlEngine;
import org.opencds.cqf.cql.engine.execution.EvaluationResult;
import org.opencds.cqf.cql.engine.execution.LibraryLoader;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import com.ibm.cohort.engine.cdm.CDMConstants;
import com.ibm.cohort.engine.cqfruler.CDMContext;
import com.ibm.cohort.engine.parameter.Parameter;
import com.ibm.cohort.engine.r4.cache.CachingR4FhirModelResolver;
import com.ibm.cohort.engine.r4.cache.CachingSearchParameterResolver;
import com.ibm.cohort.engine.retrieve.R4RestFhirRetrieveProvider;
import com.ibm.cohort.engine.terminology.R4RestFhirTerminologyProvider;
import com.ibm.cohort.fhir.client.config.FhirClientBuilderFactory;
import com.ibm.cohort.fhir.client.config.FhirServerConfig;

import ca.uhn.fhir.rest.client.api.IGenericClient;

/**
 * Provide a wrapper around the open source reference implementation of a CQL
 * engine using sensible defaults for our target execution environment and
 * providing utilities for loading library contents from various sources.
 */
public class CqlEvaluator {

	protected static final List<String> SUPPORTED_MODELS = Arrays.asList("http://hl7.org/fhir",
			"http://hl7.org/fhir/us/core", "http://hl7.org/fhir/us/qicore", CDMConstants.BASE_URL);

	private LibraryLoader libraryLoader = null;

	private FhirClientBuilderFactory clientBuilderFactory;

	private IGenericClient dataServerClient;
	private IGenericClient measureServerClient;
	private IGenericClient terminologyServerClient;
	
	private Integer searchPageSize = 1000;
	private boolean expandValueSets = true;

	public CqlEvaluator() {
		this(FhirClientBuilderFactory.newInstance());
	}

	public CqlEvaluator(FhirClientBuilderFactory clientBuilderFactory) {
		this.clientBuilderFactory = clientBuilderFactory;
	}

	/**
	 * Configure the library loader to be used when loading CQL libraries.
	 * 
	 * @param libraryLoader the libraryLoader
	 */
	public void setLibraryLoader(LibraryLoader libraryLoader) {
		this.libraryLoader = libraryLoader;
	}

	/**
	 * Configure the FHIR server used for retrieve operations.
	 * 
	 * @param config data server connection properties
	 */
	public void setDataServerConnectionProperties(FhirServerConfig config) {
		setDataServerClient(clientBuilderFactory.newFhirClientBuilder().createFhirClient(config));
	}

	/**
	 * Set the FHIR client used for data operations.
	 * 
	 * @param client data server client
	 */
	public void setDataServerClient(IGenericClient client) {
		this.dataServerClient = client;
	}

	/**
	 * Get the data server client object
	 * 
	 * @return data server client
	 */
	public IGenericClient getDataServerClient() {
		return this.dataServerClient;
	}

	/**
	 * Configure the FHIR server used for quality measure operations.
	 * 
	 * @param config measure server connection properties
	 */
	public void setMeasureServerConnectionProperties(FhirServerConfig config) {
		setMeasureServerClient(clientBuilderFactory.newFhirClientBuilder().createFhirClient(config));
	}

	/**
	 * Set the FHIR client used to interact with the FHIR measure server.
	 * 
	 * @param client measure server client
	 */
	public void setMeasureServerClient(IGenericClient client) {
		this.measureServerClient = client;
	}

	/**
	 * Get the measure server client.
	 * 
	 * @return measure server client.
	 */
	public IGenericClient getMeasureServerClient() {
		return this.measureServerClient;
	}

	/**
	 * Configure the FHIR server used for terminology operations.
	 * 
	 * @param config terminology server connection properties
	 */
	public void setTerminologyServerConnectionProperties(FhirServerConfig config) {
		this.terminologyServerClient = clientBuilderFactory.newFhirClientBuilder().createFhirClient(config);
	}

	/**
	 * Set the FHIR client used for terminology data access.
	 * 
	 * @param client terminology server client
	 */
	public void setTerminologyServerClient(IGenericClient client) {
		this.terminologyServerClient = client;
	}

	/**
	 * Get the terminology server client.
	 * 
	 * @return terminology server client.
	 */
	public IGenericClient getTerminologyServerClient() {
		return this.terminologyServerClient;
	}
	
	/**
	 * Set the number of records that will be requested per response in FHIR search
	 * queries via the _count search parameter.
	 * 
	 * @param searchPageSize positive integer
	 */
	public void setSearchPageSize(Integer searchPageSize) {
		this.searchPageSize = searchPageSize;
	}
	
	/**
	 * Get the number of records that will be requested per response in FHIR search
	 * queries via the _count search parameter.
	 * 
	 * @return positive integer or null if no value has been configured. If no value
	 *         is configured, the server will use its default setting.
	 */
	public Integer getSearchPageSize() {
		return this.searchPageSize;
	}
	
	/**
	 * Set the expand value sets flag that is used by the engine's data provider.
	 * This determines whether or not the queries generated will use the FHIR token
	 * :in modifier when possible vs. using the TerminologyProvider to pre-expand
	 * the ValueSets.
	 * 
	 * @param expandValueSets true if ValueSets should be pre-expanded using the
	 *                        terminology provider or false if the FHIR :in modifier
	 *                        should be used.
	 */
	public void setExpandValueSets(boolean expandValueSets) {
		this.expandValueSets = expandValueSets;
	}
	
	/**
	 * Get the expand value sets flag.
	 * 
	 * @return true if ValueSets should be pre-expanded using the terminology
	 *         provider or false if the FHIR :in modifier should be used.
	 */
	public boolean isExpandValueSets() {
		return this.expandValueSets;
	}
	
	/**
	 * Usage pattern of CQL Engine based on the Executor class in the
	 * cql_execution_service. This is an amount of detail that should be handled by
	 * the CqlEngine interface, and generally is, but there are advantages to doing
	 * it ourselves including the ability to bypass the parameter bug in releases up
	 * to 1.5.0 and managing context switches that might happen in a very
	 * theoretical world. We saw some behavior in 1.4.0 and earlier releases where
	 * this execution pattern caused a lot more interactions with the FHIR server
	 * so, care needs to be taken in its use. There are tests that check for the
	 * number of server calls.
	 * 
	 * @param libraryName    Library identifier (required)
	 * @param libraryVersion Library version (optional/null)
	 * @param parameters     parameter values for required input parameters in the
	 *                       CQL (optional/null)
	 * @param expressions    list of defines to be executed from the specified
	 *                       <code>libraryName</code> (optional/null). When not
	 *                       provided, all defines in the library will be executed.
	 * @param contextIds     list of contexts (generally patient IDs) for which the
	 *                       specified <code>expressions</code> will be executed. At
	 *                       least one value is required.
	 * @param callback       callback function to be evaluated once per context per
	 *                       executed define (required).             
	 */
	protected void evaluateExpressionByExpression(final String libraryName, final String libraryVersion,
			final Map<String, Parameter> parameters, final Set<String> expressions, final List<String> contextIds,
			final EvaluationResultCallback callback) {
		if (this.libraryLoader == null || this.dataServerClient == null || this.terminologyServerClient == null
				|| this.measureServerClient == null) {
			throw new IllegalArgumentException(
					"Missing one or more required initialization parameters (libraries, dataServerClient, terminologyServerClient, measureServerClient)");
		}

		if (libraryName == null || contextIds == null || contextIds.isEmpty()) {
			throw new IllegalArgumentException(
					"Missing one or more required input parameters (libraryName, contextIds)");
		}

		TerminologyProvider termProvider = getTerminologyProvider();
		
		Map<String, DataProvider> dataProviders = getDataProviders(termProvider);

		VersionedIdentifier libraryId = new VersionedIdentifier().withId(libraryName);
		if (libraryVersion != null) {
			libraryId.setVersion(libraryVersion);
		}

		Library library = libraryLoader.load(libraryId);
		LibraryUtils.requireNoTranslationErrors(library);
		LibraryUtils.requireValuesForNonDefaultParameters(library, parameters);

		for (String contextId : contextIds) {
			callback.onContextBegin(contextId);

			Context context = new CDMContext(library);
			for (Map.Entry<String, DataProvider> e : dataProviders.entrySet()) {
				context.registerDataProvider(e.getKey(), e.getValue());
			}
			context.registerTerminologyProvider(termProvider);
			context.registerLibraryLoader(libraryLoader);
			context.setExpressionCaching(true);

			if (parameters != null) {
				Map<String,Object> typedParameters = mapToCqlTypes(parameters);
				for (Map.Entry<String, Object> entry : typedParameters.entrySet()) {
					context.setParameter(/* libraryName= */null, entry.getKey(), entry.getValue());
				}
			}

			Set<String> exprToEvaluate = new LinkedHashSet<String>();
			if (expressions != null) {
				exprToEvaluate.addAll(expressions);
			} else {
				if (library.getStatements() != null && library.getStatements().getDef() != null) {
					exprToEvaluate
							.addAll(library.getStatements().getDef().stream().filter(e -> !(e instanceof FunctionDef))
									.map(e -> e.getName()).collect(Collectors.toList()));
				}
			}

			// This style of invocation allows us to potentially be Context agnostic and
			// support switching contexts between defines if that ever becomes something we
			// need.
			// Of course, we would need to map the contextIds in the input to multiple
			// context
			// paths, so this isn't really complete yet.
			for (String expression : exprToEvaluate) {
				ExpressionDef def = context.resolveExpressionRef(expression);
				context.enterContext(def.getContext());
				context.setContextValue(context.getCurrentContext(), contextId);

				// Executor.java uses def.getExpression().evaluate(), but that causes
				// an extra evaluation for some reason.
				Object result = def.evaluate(context);

				callback.onEvaluationComplete(contextId, def.getName(), result);
			}

			callback.onContextComplete(contextId);
		}
	}

	/**
	 * Initialize the data providers for the CQL Engine. By default this will perform
	 * ValueSet expansion during the retrieve operation using the terminology provider 
	 * instead of trying to use the FHIR :in token modifier because IBM FHIR does not
	 * yet support the :in modifier. Consumers should override this method with their
	 * own implementation if they wish to use the :in modifier.  
	 * 
	 * @param terminologyProvider TerminologyProvider that will be used to support
	 * retrieve operations that test valueset membership.
	 * @return Map of supported model URL to data provider
	 */
	protected Map<String, DataProvider> getDataProviders(TerminologyProvider terminologyProvider) {
		SearchParameterResolver resolver = new CachingSearchParameterResolver(this.dataServerClient.getFhirContext());
		R4RestFhirRetrieveProvider retrieveProvider = new R4RestFhirRetrieveProvider(resolver, this.dataServerClient);
		retrieveProvider.setTerminologyProvider(terminologyProvider);
		//Ideally, we would determine this using the FHIR CapabilityStatement, but there isn't a strongly
		//reliable way to do that right now using HAPI and IBM FHIR as examples.
		retrieveProvider.setExpandValueSets(isExpandValueSets());
		retrieveProvider.setSearchPageSize(getSearchPageSize());
		CompositeDataProvider dataProvider = new CompositeDataProvider(new CachingR4FhirModelResolver(), retrieveProvider);

		return mapSupportedModelsToDataProvider(dataProvider);
	}

	/**
	 * Helper method for turning the list of supported models into a Map of
	 * DataProviders to be used when registering with the CQLEngine.
	 * 
	 * @param dataProvider DataProvider that will be used in support of the
	 *                     SUPPORTED_MODELS
	 * @return Map of model URL to the <code>dataProvider</code>
	 */
	protected Map<String, DataProvider> mapSupportedModelsToDataProvider(DataProvider dataProvider) {
		return mapSupportedModelsToDataProvider(SUPPORTED_MODELS, dataProvider);
	}

	/**
	 * Helper method for turning the list of supported models into a Map of
	 * DataProviders to be used when registering with the CQLEngine.
	 * 
	 * @param supportedModels List of data models that are supported (i.e. base FHIR, QICore, etc)
	 * @param dataProvider DataProvider that will be used in support of the
	 *                     SUPPORTED_MODELS
	 * @return Map of model URL to the <code>dataProvider</code>
	 */
	protected Map<String, DataProvider> mapSupportedModelsToDataProvider(List<String> supportedModels,
			DataProvider dataProvider) {
		return supportedModels.stream()
				.map(url -> new SimpleEntry<String, DataProvider>(url, dataProvider))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * Initialize the terminology provider for the CQL Engine
	 * 
	 * @return terminology provider
	 */
	protected TerminologyProvider getTerminologyProvider() {
		return new R4RestFhirTerminologyProvider(this.terminologyServerClient);
	}

	/**
	 * Usage pattern that uses the CqlEngine interface to execute provided CQL. This
	 * is the preferred usage pattern, but
	 * @see #evaluateExpressionByExpression(String, String, Map, Set, List,
	 * EvaluationResultCallback) for details on why it might not be used.
	 * 
	 * @param libraryName    Library identifier
	 * @param libraryVersion Library version (optional/null)
	 * @param parameters     parameter values for required input parameters in the
	 *                       CQL (optional/null)
	 * @param expressions    list of defines to be executed from the specified
	 *                       <code>libraryName</code> (optional/null). When not
	 *                       provided, all defines in the library will be executed.
	 * @param contextIds     list of contexts (generally patient IDs) for which the
	 *                       specified <code>expressions</code> will be executed. At
	 *                       least one value is required.
	 * @param callback       callback function to be evaluated once per context per
	 *                       executed define
	 */
	protected void evaluateWithEngineWrapper(String libraryName, String libraryVersion, Map<String, Parameter> parameters,
			Set<String> expressions, List<String> contextIds, EvaluationResultCallback callback) {
		if (this.libraryLoader == null || this.dataServerClient == null || this.terminologyServerClient == null
				|| this.measureServerClient == null) {
			throw new IllegalArgumentException(
					"Missing one or more required initialization parameters (libraries, dataServerClient, terminologyServerClient, measureServerClient)");
		}

		if (libraryName == null || contextIds == null || contextIds.isEmpty()) {
			throw new IllegalArgumentException(
					"Missing one or more required input parameters (libraryName, contextIds)");
		}
		
		TerminologyProvider termProvider = getTerminologyProvider();

		Map<String, DataProvider> dataProviders = getDataProviders(termProvider);


		VersionedIdentifier libraryId = new VersionedIdentifier().withId(libraryName);
		if (libraryVersion != null) {
			libraryId.setVersion(libraryVersion);
		}

		Library library = libraryLoader.load(libraryId);
		LibraryUtils.requireNoTranslationErrors(library);
		LibraryUtils.requireValuesForNonDefaultParameters(library, parameters);
		
		Map<String, Object> typedParameters = mapToCqlTypes(parameters);		

		CqlEngine cqlEngine = new CqlEngine(libraryLoader, dataProviders, termProvider);

		for (String contextId : contextIds) {
			callback.onContextBegin(contextId);
			EvaluationResult er = cqlEngine.evaluate(libraryId, expressions, Pair.of(ContextNames.PATIENT, contextId),
					typedParameters, /* debugMap= */null);
			for (Map.Entry<String, Object> result : er.expressionResults.entrySet()) {
				callback.onEvaluationComplete(contextId, result.getKey(), result.getValue());
			}
			callback.onContextComplete(contextId);
		}
	}

	private Map<String, Object> mapToCqlTypes(Map<String, Parameter> parameters) {
		Map<String, Object> typedParameters = null;
		if (parameters != null) {
			typedParameters = parameters.entrySet().stream()
					.map(entry -> new SimpleEntry<String,Object>(entry.getKey(), entry.getValue().toCqlType()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}
		return typedParameters;
	}

	/**
	 * Execute the given <code>libraryName</code> for each context id specified in
	 * <code>contextIds</code> using a FHIR R4 data provider and FHIR R4 terminology
	 * provider. Library content and FHIR server configuration data should be
	 * configured prior to invoking this method.
	 * 
	 * @param libraryName    Library identifier
	 * @param libraryVersion Library version (optional/null)
	 * @param parameters     parameter values for required input parameters in the
	 *                       CQL (optional/null)
	 * @param expressions    list of defines to be executed from the specified
	 *                       <code>libraryName</code> (optional/null). When not
	 *                       provided, all defines in the library will be executed.
	 * @param contextIds     list of contexts (generally patient IDs) for which the
	 *                       specified <code>expressions</code> will be executed. At
	 *                       least one value is required.
	 * @param callback       callback function for receiving engine execution events
	 */
	public void evaluate(String libraryName, String libraryVersion, Map<String, Parameter> parameters,
			Set<String> expressions, List<String> contextIds, EvaluationResultCallback callback) {
		evaluateWithEngineWrapper(libraryName, libraryVersion, parameters, expressions, contextIds, callback);
	}

	/**
	 * Execute the given <code>libraryName</code> for each context id specified in
	 * <code>contextIds</code> using a FHIR R4 data provider and FHIR R4 terminology
	 * provider. Library content and FHIR server configuration data should be
	 * configured prior to invoking this method.
	 * 
	 * @param libraryName    Library identifier
	 * @param libraryVersion Library version (optional/null)
	 * @param parameters     parameter values for required input parameters in the
	 *                       CQL (optional/null)
	 * @param expressions    list of defines to be executed from the specified
	 *                       <code>libraryName</code> (optional/null). When not
	 *                       provided, all defines in the library will be executed.
	 * @param contextIds     list of contexts (generally patient IDs) for which the
	 *                       specified <code>expressions</code> will be executed. At
	 *                       least one value is required.
	 * @param callback       callback function to be evaluated once per context per
	 *                       executed define
	 */
	public void evaluate(String libraryName, String libraryVersion, Map<String, Parameter> parameters,
			Set<String> expressions, List<String> contextIds, ExpressionResultCallback callback) {
		evaluateWithEngineWrapper(libraryName, libraryVersion, parameters, expressions, contextIds,
				new ProxyingEvaluationResultCallback(callback));
	}
}
