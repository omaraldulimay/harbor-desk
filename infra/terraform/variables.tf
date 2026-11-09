variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "name" {
  type    = string
  default = "harbor-desk"
}

variable "image" {
  description = "Container image of the app, for example an ECR image URI with tag"
  type        = string
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "desired_count" {
  type    = number
  default = 2
}
