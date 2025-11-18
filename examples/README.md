# Examples - Local Development with Docker Compose

This folder contains everything you need to run the Flink Log Processor locally using Docker Compose and LocalStack.

## Quick Start

### Prerequisites
- Docker and Docker Compose installed
- At least 4GB RAM available for Docker
- Ports 4566, 8081 available

### 1. Build the Application

```bash
# From project root
mvn clean package
```

### 2. Start the Environment

```bash
# Start LocalStack and Flink cluster
docker-compose -f docker-compose-local.yml up -d

# Check services are running
docker-compose -f docker-compose-local.yml ps
```

### 3. Verify LocalStack Setup

```bash
# Check S3 buckets
docker exec flink-localstack awslocal s3 ls

# Check uploaded logs
docker exec flink-localstack awslocal s3 ls s3://flink-logs/

# Check Glue database
docker exec flink-localstack awslocal glue get-database --name logs_db
```

### 4. Access Flink UI

Open http://localhost:8081 in your browser to access the Flink Dashboard.

### 5. Submit the Job

```bash
# Submit job to Flink
docker exec flink-jobmanager flink run \
  -c com.github.ghoshp83.flinklogprocessor.UnstructuredDataProcessor \
  /opt/flink/usrlib/flink-log-processor-0.1.jar
```

### 6. Monitor Processing

- **Flink UI**: http://localhost:8081
- **LocalStack Logs**: `docker logs flink-localstack`
- **Job Logs**: Check Flink UI → Running Jobs → Your Job → Logs

### 7. Query Results

```bash
# List processed files in Iceberg warehouse
docker exec flink-localstack awslocal s3 ls s3://flink-iceberg-warehouse/ --recursive
```

### 8. Stop the Environment

```bash
docker-compose -f docker-compose-local.yml down

# Remove volumes (clean slate)
docker-compose -f docker-compose-local.yml down -v
```

## Sample Logs

### Application Logs (`sample-logs/application.log`)
Standard application logs with timestamp, level, and message:
```
2024-01-15T10:30:00.123Z INFO Application started successfully
2024-01-15T10:30:05.678Z ERROR Failed to connect to external API
```

**Grok Pattern**: `%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}`

### Apache Access Logs (`sample-logs/apache-access.log`)
Standard Apache/Nginx access logs:
```
127.0.0.1 - - [15/Jan/2024:10:30:00 +0000] "GET /index.html HTTP/1.1" 200 2326
```

**Grok Pattern**: `%{COMBINEDAPACHELOG}`

## Configuration

The `config/application-properties-local.json` file contains LocalStack-specific settings:

- **S3 Endpoint**: `http://localstack:4566`
- **S3 Path**: `s3://flink-logs/`
- **Iceberg Warehouse**: `s3://flink-iceberg-warehouse`
- **Glue Database**: `logs_db`

## Architecture

```
┌─────────────────┐
│   LocalStack    │
│  (S3 + Glue)    │
│   Port: 4566    │
└────────┬────────┘
         │
         │ S3 API
         │
┌────────▼────────┐      ┌──────────────┐
│  Flink Job      │─────▶│ Flink Task   │
│  Manager        │      │  Manager     │
│  Port: 8081     │      │              │
└─────────────────┘      └──────────────┘
```

## Troubleshooting

### LocalStack not starting
```bash
# Check logs
docker logs flink-localstack

# Restart
docker-compose -f docker-compose-local.yml restart localstack
```

### Flink job fails
```bash
# Check JobManager logs
docker logs flink-jobmanager

# Check TaskManager logs
docker logs flink-taskmanager
```

### S3 connection issues
Ensure the job uses LocalStack endpoint:
- Endpoint: `http://localstack:4566`
- Path style access: `true`

### Port conflicts
If ports 4566 or 8081 are in use, modify `docker-compose-local.yml`:
```yaml
ports:
  - "14566:4566"  # Change LocalStack port
  - "18081:8081"  # Change Flink UI port
```

## Adding Your Own Logs

1. Add log files to `sample-logs/` folder
2. Update `init-localstack.sh` to upload them:
   ```bash
   awslocal s3 cp /tmp/sample-logs/your-log.txt s3://flink-logs/
   ```
3. Update `config/application-properties-local.json` with appropriate Grok pattern
4. Restart: `docker-compose -f docker-compose-local.yml restart`

## Performance Testing

For performance testing, generate larger log files:

```bash
# Generate 10,000 log lines
for i in {1..10000}; do
  echo "2024-01-15T10:30:$((i%60)).123Z INFO Processing record $i" >> sample-logs/large-app.log
done

# Upload to LocalStack
docker exec flink-localstack awslocal s3 cp /tmp/sample-logs/large-app.log s3://flink-logs/
```

## Next Steps

- Modify Grok patterns in configuration
- Add more sample log formats
- Test with different parallelism settings
- Monitor resource usage in Flink UI
- Experiment with Iceberg table properties

## Resources

- [Flink Documentation](https://flink.apache.org/docs/stable/)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [Apache Iceberg Documentation](https://iceberg.apache.org/docs/latest/)
- [Grok Patterns](https://github.com/thekrakken/java-grok)
