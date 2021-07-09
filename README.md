ignore this PR
tabishop toolchain PR support test don't commit
## IBM Cohort and Quality Measure Service

The IBM® Cohort and Quality Measure Service supports evaluation of cohorts defined in Clinical Quality Language **CQL** and clinical quality measures defined using **FHIR®** (Fast Healthcare Interoperability Resources) Measure resource syntax.

The service makes use of and builds upon current open source projects supporting CQL-based cohort processing, namely:
- [CQL engine](https://github.com/DBCG/cql_engine)
- [Clinical Quality Language](https://github.com/cqframework/clinical_quality_language)
- [cqf-ruler](https://github.com/DBCG/cqf-ruler)

### Direction and Scope

This project looks to extend existing CQL-based cohort evaluation capabilities by delivering additional features in the following areas:
- Optimized FHIR server access patterns for improved cohort evaluation performance
- Ability to evaluate a set of quality measures against the same collection of patient data
- Addition of a distributed processing model to achieve improved throughput when evaluating a large number of cohorts and measures against a large patient population

### Contributing to the IBM Cohort and Quality Measure Service
The IBM Cohort and Quality Measure Service is under active development. To help develop the service, clone or download the project and build it using Maven.
See [Setting up for development]() for more information.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contributing your changes back to the project.

### License
The IBM Cohort and Quality Measure Service is licensed under the Apache 2.0 license. Full license text is
available at [LICENSE](LICENSE).

FHIR® is the registered trademark of HL7 and is used with the permission of HL7. Use of the FHIR trademark does not constitute endorsement of this product by HL7.
IBM and the IBM logo are trademarks of International Business Machines Corporation, registered in many jurisdictions worldwide. Other product and service names might be trademarks of IBM or other companies. A current list of IBM trademarks is available on [https://ibm.com/trademark](https://ibm.com/trademark).

