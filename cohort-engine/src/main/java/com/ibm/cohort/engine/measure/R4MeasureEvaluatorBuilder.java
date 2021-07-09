/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine.measure;

import java.util.Map;

import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import com.ibm.cohort.engine.measure.cache.RetrieveCacheContext;
import com.ibm.cohort.engine.terminology.R4RestFhirTerminologyProvider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;

/**
 * A builder intended to allow for easy creation of {@link MeasureEvaluator} instances tailored for use
 * with FHIR R4.
 */
public class R4MeasureEvaluatorBuilder {

	private FHIRClientContext clientContext;
	private RetrieveCacheContext cacheContext;
	private boolean isExpandValueSets = R4DataProviderFactory.DEFAULT_IS_EXPAND_VALUE_SETS;
	private Integer pageSize = R4DataProviderFactory.DEFAULT_PAGE_SIZE;
	private R4FhirModelResolver modelResolver = new R4FhirModelResolver();
	private SearchParameterResolver searchParameterResolver = new SearchParameterResolver(FhirContext.forCached(FhirVersionEnum.R4));

	public R4MeasureEvaluatorBuilder withClientContext(FHIRClientContext value) {
		this.clientContext = value;
		return this;
	}

	public R4MeasureEvaluatorBuilder withRetrieveCacheContext(RetrieveCacheContext value) {
		this.cacheContext = value;
		return this;
	}
	
	public R4MeasureEvaluatorBuilder withExpandValueSets(boolean value) {
		this.isExpandValueSets = value;
		return this;
	}
	
	public R4MeasureEvaluatorBuilder withPageSize(Integer value) {
		this.pageSize = value;
		return this;
	}

	public R4MeasureEvaluatorBuilder withModelResolver(R4FhirModelResolver modelResolver) {
		this.modelResolver = modelResolver;
		return this;
	}
	
	public R4MeasureEvaluatorBuilder withSearchParameterResolver(SearchParameterResolver searchParameterResolver) {
		this.searchParameterResolver = searchParameterResolver;
		return this;
	}

	public MeasureEvaluator build() {
		if (clientContext == null) {
			throw new IllegalArgumentException("Client context not provided");
		}

		TerminologyProvider terminologyProvider = new R4RestFhirTerminologyProvider(clientContext.getTerminologyClient());
		Map<String, DataProvider> dataProviders = R4DataProviderFactory.createDataProviderMap(
				clientContext.getDataClient(),
				terminologyProvider,
				cacheContext,
				modelResolver,
				searchParameterResolver,
				isExpandValueSets,
				pageSize
		);
		MeasureResolutionProvider<Measure> measureProvider = new RestFhirMeasureResolutionProvider(clientContext.getMeasureClient());
		LibraryResolutionProvider<Library> libraryProvider = new RestFhirLibraryResolutionProvider(clientContext.getLibraryClient());

		return new MeasureEvaluator(measureProvider, libraryProvider, terminologyProvider, dataProviders);
	}

}
