# *****************************************************************
#
# Licensed Materials - Property of IBM
#
# (C) Copyright IBM Corp. 2021. All Rights Reserved.
#
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
#
# *****************************************************************
variable "namespace" {
  description = "Kubernetes namespace to deploy to."
  type        = string
}

variable "service_names" {
  description = "List of services names (DNS names) include as ALT names in generated TLS certificate."
  type        = list(string)
}

variable "organization" {
  description = "Organization name (OU) for TLS certificate"
  type        = string
}

variable "kubernetes_config_context" {
  description = "k8s cluster config context used by the k8s terraform provider"
  type = string
}