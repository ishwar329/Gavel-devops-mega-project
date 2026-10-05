# Gavel DynamoDB Tables


# -------------------------
# Users table
# -------------------------
resource "aws_dynamodb_table" "users" {
  name         = "Users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "email"
    type = "S"
  }

  global_secondary_index {
    name            = "email-index"
    hash_key        = "email"
    projection_type = "ALL"
  }
}


# -------------------------
# Shops table
# -------------------------
resource "aws_dynamodb_table" "shops" {
  name         = "Shops"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "shopId"

  attribute {
    name = "shopId"
    type = "S"
  }

  attribute {
    name = "ownerId"
    type = "S"
  }

  global_secondary_index {
    name            = "owner_id-index"
    hash_key        = "ownerId"
    projection_type = "ALL"
  }
}


# -------------------------
# Items table
# -------------------------
resource "aws_dynamodb_table" "items" {
  name         = "Items"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "itemId"

  attribute {
    name = "itemId"
    type = "S"
  }

  attribute {
    name = "shopId"
    type = "S"
  }

  global_secondary_index {
    name            = "shop_id-index"
    hash_key        = "shopId"
    projection_type = "ALL"
  }
}


# -------------------------
# Payments table
# -------------------------
resource "aws_dynamodb_table" "payments" {
  name         = "payments"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "paymentId"

  attribute {
    name = "paymentId"
    type = "S"
  }

  attribute {
    name = "auctionId"
    type = "S"
  }

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  global_secondary_index {
    name            = "auction-index"
    hash_key        = "auctionId"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "user-index"
    hash_key        = "userId"
    range_key       = "createdAt"
    projection_type = "ALL"
  }
}


# -------------------------
# Reviews table
# -------------------------
resource "aws_dynamodb_table" "reviews" {
  name         = "Reviews"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "reviewId"

  attribute {
    name = "reviewId"
    type = "S"
  }

  attribute {
    name = "shopId"
    type = "S"
  }

  attribute {
    name = "auctionId"
    type = "S"
  }

  attribute {
    name = "reviewerId"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  global_secondary_index {
    name            = "shop_id-index"
    hash_key        = "shopId"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "auction_id-reviewer-index"
    hash_key        = "auctionId"
    range_key       = "reviewerId"
    projection_type = "ALL"
  }
}


# -------------------------
# Auction Templates table
# -------------------------
resource "aws_dynamodb_table" "auction_templates" {
  name         = "AuctionTemplates"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "template_id"

  attribute {
    name = "template_id"
    type = "S"
  }

  attribute {
    name = "shop_id"
    type = "S"
  }

  global_secondary_index {
    name            = "shop_id-index"
    hash_key        = "shop_id"
    projection_type = "ALL"
  }
}
