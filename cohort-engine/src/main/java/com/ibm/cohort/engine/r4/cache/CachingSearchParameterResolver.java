package com.ibm.cohort.engine.r4.cache;

import java.util.ArrayList;
import java.util.List;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;

import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;

public class CachingSearchParameterResolver extends SearchParameterResolver {
	
	private FhirContext context;

	private static final String CACHE_ID = "default-search-parameter-normalized-path-cache";

	private static CompleteConfiguration<String, String> getDefaultConfiguration() {
		return new MutableConfiguration<String, String>()
				.setStoreByValue(false);
	}

	private static final Cache<String, String> CACHE = Caching.getCachingProvider().getCacheManager().createCache(CACHE_ID, getDefaultConfiguration());

	public CachingSearchParameterResolver(FhirContext context) {
		super(context);
		this.context = context;
	}

	@Override
	public RuntimeSearchParam getSearchParameterDefinition(String dataType, String path, RestSearchParameterTypeEnum paramType) {
		if (dataType == null || path == null) {
			return null;
		}

		// Special case for system params. They need to be resolved by name.
		// TODO: All the others like "_language"
		String name = null;
		if (path.equals("id")) {
			name = "_id";
			path = "";
		}

		List<RuntimeSearchParam> params = this.context.getResourceDefinition(dataType).getSearchParams();

		for (RuntimeSearchParam param : params) {
			// If name matches, it's the one we want.
			if (name != null && param.getName().equals(name))
			{
				return param;
			}

			// Filter out parameters that don't match our requested type.
			if (paramType != null && !param.getParamType().equals(paramType)) {
				continue;
			}

			// Short circuit if path is in the cache
			String paramPath = param.getPath();
			String normalizedPath = CACHE.get(paramPath);
			if(normalizedPath == null) {
				normalizedPath = normalizePath(paramPath);
				CACHE.put(paramPath, normalizedPath);

			}

			if (path.equals(normalizedPath) ) {
				return param;
			}
		}

		return null;
	}

	protected String normalizePath(String path) {
		// TODO: What we really need is FhirPath parsing to just get the path
		//MedicationAdministration.medication.as(CodeableConcept)
		//MedicationAdministration.medication.as(Reference)
		//(MedicationAdministration.medication as CodeableConcept)
		//(MedicationAdministration.medication as Reference)

		// Trim off outer parens
		if (path.equals("(")) {
			path = path.substring(1, path.length() - 1);
		}

		// Trim off DataType
		path = path.substring(path.indexOf(".") + 1, path.length());


		// Split into components
		String[] pathSplit = path.split("\\.");
		List<String> newPathParts = new ArrayList<>();

		for (String p : pathSplit) {
			// Skip the "as(X)" part.
			if (p.startsWith("as(")) {
				continue;
			}

			// Skip the "[x]" part.
			if (p.startsWith("[x]")) {
				continue;
			}

			// Filter out spaces and everything after "medication as Reference"
			String[] ps = p.split(" ");
			if (ps != null && ps.length > 0){
				newPathParts.add(ps[0]);
			}
		}

		path = String.join(".", newPathParts);
		
		return path;
	}
	
	protected static void clearCache() {
		CACHE.clear();
	}
}
