/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.api.service;

public class CohortEngineRestConstants {
	public static final String SERVICE_MAJOR_VERSION = "v1";
	public static final String SERVICE_TITLE = "IBM Cohort Engine";
	public static final String SERVICE_DESCRIPTION = "Service to evaluate cohorts and measures";

	// Provide a set of package names for swagger to search for REST APIs (comma delimited).
	// Static assignment example
	//public static final String SERVICE_SWAGGER_PACKAGES =  "com.ibm.watson.common.service.package1, com.ibm.watson.common.service.package2";

	// Dynamic assignment using base package name from this class
	private static String className = CohortEngineRestConstants.class.getCanonicalName();
	public static final String SERVICE_SWAGGER_PACKAGES = className.substring(0, className.lastIndexOf('.'));
	
	public static final String DARK_LAUNCHED_MEASURE_EVALUATION = "measure_evaluation_feature";
	public static final String DARK_LAUNCHED_PATIENT_LIST_MEASURE_EVALUATION = "patient_list_measure_evaluation_feature";
	public static final String DARK_LAUNCHED_VALUE_SET_UPLOAD = "value_set_upload_feature";
}
