terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# S3 Buckets
resource "aws_s3_bucket" "input" {
  bucket = "${var.project_name}-input-${var.environment}"
  tags = var.tags
}

resource "aws_s3_bucket" "warehouse" {
  bucket = "${var.project_name}-warehouse-${var.environment}"
  tags = var.tags
}

resource "aws_s3_bucket" "checkpoints" {
  bucket = "${var.project_name}-checkpoints-${var.environment}"
  tags = var.tags
}

# Glue Database
resource "aws_glue_catalog_database" "logs" {
  name = "${var.project_name}_${var.environment}"
}

# IAM Role for Kinesis Analytics
resource "aws_iam_role" "kda_role" {
  name = "${var.project_name}-kda-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "kinesisanalytics.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy" "kda_policy" {
  name = "${var.project_name}-kda-policy"
  role = aws_iam_role.kda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
          "s3:PutObject"
        ]
        Resource = [
          aws_s3_bucket.input.arn,
          "${aws_s3_bucket.input.arn}/*",
          aws_s3_bucket.warehouse.arn,
          "${aws_s3_bucket.warehouse.arn}/*",
          aws_s3_bucket.checkpoints.arn,
          "${aws_s3_bucket.checkpoints.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "glue:GetDatabase",
          "glue:GetTable",
          "glue:CreateTable",
          "glue:UpdateTable"
        ]
        Resource = "*"
      }
    ]
  })
}

# Outputs
output "input_bucket" {
  value = aws_s3_bucket.input.id
}

output "warehouse_bucket" {
  value = aws_s3_bucket.warehouse.id
}

output "glue_database" {
  value = aws_glue_catalog_database.logs.name
}

output "kda_role_arn" {
  value = aws_iam_role.kda_role.arn
}
