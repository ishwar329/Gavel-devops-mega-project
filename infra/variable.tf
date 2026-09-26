variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-2"
}

variable "ami_id" {
  description = "AMI ID"
  type        = string
}

variable "env" {
  description = "Environment name"
  type        = string
  default     = "gavel"
}

variable "bucket_name" {
  description = "S3 bucket name"
  type        = string
  default     = "gavel-s3-bucket"
}