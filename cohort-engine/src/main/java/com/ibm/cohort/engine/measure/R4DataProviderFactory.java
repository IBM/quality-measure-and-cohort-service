/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine.measure;

import java.util.HashMap;
import java.util.Map;

import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import com.ibm.cohort.engine.measure.cache.CachingRetrieveProvider;
import com.ibm.cohort.engine.measure.cache.RetrieveCacheContext;
import com.ibm.cohort.engine.retrieve.R4RestFhirRetrieveProvider;

import ca.uhn.fhir.rest.client.api.IGenericClient;

/**
 * <p>An internal class intended to ease the burden of creating the URI to {@link DataProvider} mapping
 * required by the {@link MeasureEvaluator}.
 *
 * <p>This should only be used if the {@link R4MeasureEvaluatorBuilder} is insufficient for your usecase.
 */
public class R4DataProviderFactory {

	/**
	 * This is used to control whether the data provider tries to use the 
	 * token:in modifier to do ValueSet expansion on the FHIR server or
	 * uses the TerminologyProvider to expand the ValueSet prior to 
	 * calling the FHIR search API to retrieve data.
	 */
	public static final boolean DEFAULT_IS_EXPAND_VALUE_SETS = true;
	
	/**
	 * This controls how many records to request in a FHIR search query. By default
	 * the FHIR servers tend to use very small numbers (HAPI:20, IBM:10) which
	 * causes a large number of network requests for even moderately sized datasets.
	 * This default matches the largest allowed page size in the IBM FHIR server.
	 */
	public static final Integer DEFAULT_PAGE_SIZE = 1000;
	
	protected static final String FHIR_R4_URL = "http://hl7.org/fhir";

	public static Map<String, DataProvider> createDataProviderMap(
			IGenericClient client,
			TerminologyProvider terminologyProvider,
			RetrieveCacheContext retrieveCacheContext,
			boolean isExpandValueSets,
			Integer pageSize
	) {
		return createDataProviderMap(client, terminologyProvider, retrieveCacheContext, new R4FhirModelResolver(), new SearchParameterResolver(client.getFhirContext()), isExpandValueSets, pageSize);
	}
	
	public static Map<String, DataProvider> createDataProviderMap(
			IGenericClient client,
			TerminologyProvider terminologyProvider,
			RetrieveCacheContext retrieveCacheContext,
			R4FhirModelResolver modelResolver,
			SearchParameterResolver searchParameterResolver,
			boolean isExpandValueSets,
			Integer pageSize
	) {
		R4RestFhirRetrieveProvider baseRetrieveProvider = new R4RestFhirRetrieveProvider(searchParameterResolver, client);
		baseRetrieveProvider.setExpandValueSets(isExpandValueSets);
		baseRetrieveProvider.setSearchPageSize(pageSize);
		baseRetrieveProvider.setTerminologyProvider(terminologyProvider);
		RetrieveProvider retrieveProvider = retrieveCacheContext != null
				? new CachingRetrieveProvider(baseRetrieveProvider, retrieveCacheContext)
				: baseRetrieveProvider;

		DataProvider dataProvider = new CompositeDataProvider(modelResolver, retrieveProvider);

		Map<String, DataProvider> retVal = new HashMap<>();
		retVal.put(FHIR_R4_URL, dataProvider);

		return retVal;
	}
	
	public static Map<String, DataProvider> createDataProviderMap(
			IGenericClient client,
			TerminologyProvider terminologyProvider,
			RetrieveCacheContext retrieveCacheContext
	) {
		return createDataProviderMap( client, terminologyProvider, retrieveCacheContext, DEFAULT_IS_EXPAND_VALUE_SETS, DEFAULT_PAGE_SIZE );
	}

}
