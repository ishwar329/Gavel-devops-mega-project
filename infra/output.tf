#####################################
# VPC Outputs
#####################################

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "public_subnets" {
  description = "Public Subnet IDs"
  value       = module.vpc.public_subnets
}

output "private_subnets" {
  description = "Private Subnet IDs"
  value       = module.vpc.private_subnets
}


#####################################
# EC2 Outputs
#####################################

output "instance_ids" {
  description = "EC2 Instance IDs"
  value = {
    for name, instance in aws_instance.gavel_servers :
    name => instance.id
  }
}

output "instance_public_ips" {
  description = "Public IP addresses of EC2 instances"
  value = {
    for name, instance in aws_instance.gavel_servers :
    name => instance.public_ip
  }
}

output "instance_public_dns" {
  description = "Public DNS names of EC2 instances"
  value = {
    for name, instance in aws_instance.gavel_servers :
    name => instance.public_dns
  }
}

output "instance_private_ips" {
  description = "Private IP addresses of EC2 instances"
  value = {
    for name, instance in aws_instance.gavel_servers :
    name => instance.private_ip
  }
}


#####################################
# Security Group Outputs
#####################################

output "security_group_ids" {
  description = "Security Group IDs"
  value = {
    jenkins    = aws_security_group.jenkins_sg.id
    argo_cd    = aws_security_group.argo_cd_sg.id
    k8s_master = aws_security_group.k8s_master_sg.id
    k8s_worker = aws_security_group.k8s_worker_sg.id
    monitoring = aws_security_group.monitoring_sg.id
  }
}


#####################################
# Key Pair Output
#####################################

output "key_pair_name" {
  description = "Key Pair Name"
  value       = aws_key_pair.gavel_key.key_name
}


#####################################
# S3 Bucket Output
#####################################

output "bucket_name" {
  description = "S3 Bucket Name"
  value       = aws_s3_bucket.gavel_bucket.bucket
}