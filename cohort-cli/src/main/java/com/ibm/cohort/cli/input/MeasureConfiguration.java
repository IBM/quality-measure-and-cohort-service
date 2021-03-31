/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.cli.input;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ibm.cohort.engine.parameter.Parameter;
import com.ibm.cohort.serialization.MapKeyFieldNameDeserializer;
import com.ibm.cohort.serialization.MapKeyFieldNameSerializer;

@JsonInclude(Include.NON_NULL)
public class MeasureConfiguration {
	@JsonProperty("measureId")
	private String measureId;

	@JsonProperty("parameters")
	@JsonSerialize(keyUsing=MapKeyFieldNameSerializer.class)
	@JsonDeserialize(keyUsing=MapKeyFieldNameDeserializer.class)
	private Map<String,Parameter> parameters;

	@JsonProperty("identifier")
	private MeasureIdentifier identifier;

	@JsonProperty("version")
	private String version;

	public String getMeasureId() {
		return measureId;
	}
	protected void setMeasureId(String measureId) {
		this.measureId = measureId;
	}

	public Map<String, Parameter> getParameters() {
		return parameters;
	}
	protected void setParameters(Map<String, Parameter> parameters) {
		this.parameters = parameters;
	}

	public MeasureIdentifier getIdentifier() {
		return identifier;
	}
	protected void setIdentifier(MeasureIdentifier identifier) {
		this.identifier = identifier;
	}

	public String getVersion() {
		return version;
	}
	protected void setVersion(String version) {
		this.version = version;
	}

	public void validate() {
		boolean measureIdSpecified = !isEmpty(measureId);
		boolean identifierSpecified = (identifier != null && !isEmpty(identifier.getValue()));
		
		if (measureIdSpecified == identifierSpecified) {
			throw new IllegalArgumentException("Invalid measure parameter file: Exactly one of id or identifier with a value must be provided for each measure.");
		}

		if (identifier !=  null) {
			identifier.validate();
		}
	}
}
