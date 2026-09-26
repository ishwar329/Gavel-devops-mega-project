module "gavel-infra"{
    source = "./modules"

    env = "gavel"
    bucket_name = "gavel-s3-bucket"
    ami_id = "ami-0e5497a77ef21b5ac"
    }