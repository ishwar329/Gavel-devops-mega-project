#key pair login
resource "aws_key_pair" "gavel_key" {
    key_name = "${var.env}-terraform-key"
    public_key = file("")

}

resource "aws_instance" "gavel_servers" {
    for_each = local.ec2_instances
    key_name = aws_key_pair.gavel_key.key_name
    subnet_id = (
        each.value.subnet_type == "public" ? module.vpc.public_subnets[0] : module.vpc.private_subnets[0]
    )
      vpc_security_group_ids = [
    local.security_groups[each.value.security_group]
  ]
  ami = var.ami_id
    associate_public_ip_address = (
    each.value.subnet_type == "public"
  )
  instance_type = each.value.instance_type

    root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }
  tags = {
    Name        = each.value.name
    Environment = var.env
  }

}