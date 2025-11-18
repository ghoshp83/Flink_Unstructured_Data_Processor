#!/bin/bash

echo "Initializing LocalStack for Flink Log Processor..."

# Wait for LocalStack to be ready
sleep 5

# Create S3 buckets
awslocal s3 mb s3://flink-logs
awslocal s3 mb s3://flink-iceberg-warehouse

# Upload sample logs to S3
awslocal s3 cp /tmp/sample-logs/application.log s3://flink-logs/application.log
awslocal s3 cp /tmp/sample-logs/apache-access.log s3://flink-logs/apache-access.log
awslocal s3 cp /tmp/sample-logs/syslog.log s3://flink-logs/syslog.log
awslocal s3 cp /tmp/sample-logs/json-structured.log s3://flink-logs/json-structured.log
awslocal s3 cp /tmp/sample-logs/nginx-error.log s3://flink-logs/nginx-error.log
awslocal s3 cp /tmp/sample-logs/custom-app.log s3://flink-logs/custom-app.log

# Create Glue database
awslocal glue create-database --database-input '{"Name":"logs_db","Description":"Database for log processing"}'

echo "LocalStack initialization complete!"
echo "S3 buckets created: flink-logs, flink-iceberg-warehouse"
echo "Glue database created: logs_db"
echo "Sample logs uploaded to s3://flink-logs/"
