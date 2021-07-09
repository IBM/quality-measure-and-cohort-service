package com.ibm.cohort.engine.r4.cache;


import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;

public class CachingSearchParameterResolverTest {
	@Before
	public void clearCache() {
		CachingSearchParameterResolver.clearCache();
	}

	@Test
	public void testgetSearchParameterDefinition_usesCachedResponse() {
		CachingSearchParameterResolver resolver = spy(new CachingSearchParameterResolver(FhirContext.forCached(FhirVersionEnum.R4)));

		RuntimeSearchParam searchParameterDefinitionOrig = resolver.getSearchParameterDefinition("Location", "address", RestSearchParameterTypeEnum.STRING);
		RuntimeSearchParam searchParameterDefinitionSecond = resolver.getSearchParameterDefinition("Location", "address", RestSearchParameterTypeEnum.STRING);

		verify(resolver, times(1)).normalizePath("Location.address");
		assertEquals(searchParameterDefinitionOrig, searchParameterDefinitionSecond);
	}
}