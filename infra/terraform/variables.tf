variable "aws_region" {
  type    = string
  default = "eu-west-2"
}
variable "project" {
  type    = string
  default = "payments-portfolio"
}
variable "deploy_service" {
  description = "Keep false until a Docker image is pushed and a reachable Kafka cluster is configured."
  type        = bool
  default     = false
}
variable "image_tag" {
  type    = string
  default = "demo"
}
variable "kafka_bootstrap_servers" {
  description = "A reachable Kafka cluster endpoint, e.g. MSK within the VPC. Kafka/MSK is NOT provisioned by this example."
  type        = string
  default     = ""
}
variable "kafka_security_protocol" {
  description = "Use SSL for a TLS-enabled broker. Other Kafka authentication needs additional client configuration."
  type        = string
  default     = "SSL"
}
variable "allowed_cidr" {
  description = "Optional single trusted IPv4 CIDR allowed to access demo ECS app over HTTP; empty means no incoming connections."
  type        = string
  default     = ""
}
