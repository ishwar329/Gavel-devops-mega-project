locals {
  ec2_instances = tomap({

    jenkins = {
      instance_type  = "t3.micro"
      name           = "${var.env}-jenkins"
      subnet_type    = "public"
      security_group = "jenkins"
    }

    argo_cd = {
      instance_type  = "t3.micro"
      name           = "${var.env}-argo-cd"
      subnet_type    = "public"
      security_group = "argo_cd"
    }

    k8s_master = {
      instance_type  = "t3.micro"
      name           = "${var.env}-k8s-master"
      subnet_type    = "private"
      security_group = "k8s_master"
    }

    k8s_worker = {
      instance_type  = "t3.micro"
      name           = "${var.env}-k8s-worker"
      subnet_type    = "private"
      security_group = "k8s_worker"
    }

    monitoring = {
      instance_type  = "t3.micro"
      name           = "${var.env}-monitoring"
      subnet_type    = "private"
      security_group = "monitoring"
    }
  })

  security_groups = {
    jenkins    = aws_security_group.jenkins_sg.id
    argo_cd    = aws_security_group.argo_cd_sg.id
    k8s_master = aws_security_group.k8s_master_sg.id
    k8s_worker = aws_security_group.k8s_worker_sg.id
    monitoring = aws_security_group.monitoring_sg.id
  }
}