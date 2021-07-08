/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.measure;

import org.hl7.fhir.r4.model.Measure;

public class MeasureHelper {
	
	/**
	 * Provide a search path for resolving a FHIR Measure resource from the given string. The search
	 * path tries to understand the provided resourceID first as a direct mapping to a FHIR resource
	 * and, if not, it tries to resolve it as the canonical URL of a FHIR resource.
	 * 
	 * @param resourceID String matching one of the expected lookup strategies
	 * @param provider Resolution implementation for the various lookup strategies
	 * @return Resolved FHIR Measure resource
	 */
	public static Measure loadMeasure(String resourceID, MeasureResolutionProvider<Measure> provider ) {
		Measure result = null;
		
		if( resourceID != null ) {
			if( resourceID.startsWith("Measure/") || ! resourceID.contains("/") ) {
				result = provider.resolveMeasureById(resourceID.replace("Measure/", ""));
			} else if( resourceID.contains("/Measure/") ) {
				result = provider.resolveMeasureByCanonicalUrl(resourceID);
			}
		}
		
		if( result == null ) {
			throw new IllegalArgumentException(String.format("Failed to resolve Measure resource '%s'", resourceID));
		}
		
		return result;
	}

	public static Measure loadMeasure(Identifier identifier, String version, MeasureResolutionProvider<Measure> provider) {
		Measure result;

		org.hl7.fhir.r4.model.Identifier fhirIdentfier = new org.hl7.fhir.r4.model.Identifier().setSystem(identifier.getSystem()).setValue(identifier.getValue());
		result = provider.resolveMeasureByIdentifier(fhirIdentfier, version);

		if ( result == null ) {
			throw new IllegalArgumentException(String.format("Failed to resolve Measure resource with identifier:'%s', version:'%s'", identifier, version));
		}

		return result;
	}

	public static Measure loadMeasure(MeasureContext context, MeasureResolutionProvider<Measure> provider) {
		Measure result = null;

		if (context.getMeasureId() != null) {
			result = loadMeasure(context.getMeasureId(), provider);
		} else if (context.getIdentifier() != null) {
			result = loadMeasure(context.getIdentifier(), context.getVersion(), provider);
		}

		if (result == null) {
			throw new IllegalArgumentException(String.format("Failed to resolve Measure: %s", context));
		}

		return result;
	}
}
