# Flink Unstructured Data Processor

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-11+-orange.svg)](https://openjdk.org/)
[![Flink](https://img.shields.io/badge/flink-1.18.1-red.svg)](https://flink.apache.org/)
[![Iceberg](https://img.shields.io/badge/iceberg-1.6.1-blue.svg)](https://iceberg.apache.org/)
[![Security](https://img.shields.io/badge/security-hardened-green.svg)](SECURITY.md)

A robust, production-ready Apache Flink application for processing unstructured log data, parsing it using configurable Grok patterns, and storing it in Apache Iceberg tables.

## Overview

This Flink-based data processor is designed to ingest unstructured log data from various sources, parse different log formats using customizable Grok patterns, and store the processed data in Apache Iceberg tables for further analysis. The application leverages Apache Flink for stream processing and AWS services for storage and catalog management.

**NEW**: Now supports **generic log processing** with configurable patterns! Process Apache logs, Syslog, custom application logs, or any format you define.

📚 **Documentation**: [User Guide](GENERIC_LOG_GUIDE.md) | [Architecture](ARCHITECTURE.md) | [Deployment](DEPLOYMENT.md) | [Troubleshooting](TROUBLESHOOTING.md)

## Features

- **Continuous Monitoring**: Real-time monitoring of S3 paths for new log files
- **Flexible Parsing**: Configurable Grok patterns for parsing various log formats
- **Data Transformation**: Automatic mapping of unstructured data to structured formats
- **Iceberg Integration**: Optimized storage in Apache Iceberg tables with ACID guarantees
- **AWS Integration**: Support for AWS Glue catalog and S3 storage
- **Privacy Protection**: Built-in PII data anonymization capabilities
- **Scalable**: Designed for high-throughput, distributed processing
- **Resilient**: Fault-tolerant with Flink checkpointing

## Architecture

The application follows a streaming architecture:

```
┌─────────────┐      ┌──────────────┐      ┌─────────────────┐      ┌──────────────┐
│   S3 Logs   │─────▶│ Flink Source │─────▶│  Grok Parser    │─────▶│   Iceberg    │
│  (Input)    │      │  (Monitor)   │      │ (Transform)     │      │   Tables     │
└─────────────┘      └──────────────┘      └─────────────────┘      └──────────────┘
```

**Processing Flow:**
1. **Data Ingestion**: Continuously monitors and reads log files from Amazon S3
2. **Data Parsing**: Applies configurable Grok patterns to extract structured data
3. **Data Transformation**: Maps parsed records to Flink RowData format
4. **Data Storage**: Writes data to Apache Iceberg tables with AWS Glue catalog

## Prerequisites

- Java 11 or higher
- Apache Maven 3.6+
- AWS account with appropriate permissions
- Apache Flink 1.18.1
- Apache Iceberg 1.6.1

## Configuration

The application uses a JSON configuration file (`application-properties-local.json`) with the following sections:

### Job Configuration
```json
{
  "PropertyGroupId": "job.config",
  "PropertyMap": {
    "source.window": 1,
    "source.s3path": "s3://your-bucket/logs/"
  }
}
```

### Iceberg Table Properties
```json
{
  "PropertyGroupId": "iceberg.table.properties",
  "PropertyMap": {
    "format-version": "2",
    "write.target-file-size-bytes": "536870912",
    "write.parquet.row-group-size-bytes": "134217728",
    "write.parquet.page-size-bytes": "1048576",
    "write.metadata.delete-after-commit.enabled": "true",
    "write.metadata.previous-versions-max": "5",
    "write.distribution-mode": "hash"
  }
}
```

### Iceberg Configuration
```json
{
  "PropertyGroupId": "iceberg.config",
  "PropertyMap": {
    "iceberg.database.name": "logs_db",
    "iceberg.table.name.default": "parsed_logs",
    "iceberg.warehouse": "s3://your-iceberg-warehouse",
    "iceberg.write.parallelism": "4"
  }
}
```

## Building the Project

```bash
mvn clean package
```

This will create a fat JAR in the `target` directory.

## Running the Application

### Local Development

```bash
java -cp target/flink-log-processor-0.1.jar com.github.ghoshp83.flinklogprocessor.UnstructuredDataProcessor
```

### AWS Kinesis Data Analytics

1. Upload the JAR to an S3 bucket
2. Create a Kinesis Data Analytics application
3. Configure the application to use the JAR from S3
4. Set up the application properties as described in the Configuration section

## Log Format Configuration

The application uses Grok patterns to parse log files. Configure patterns in `application-properties-generic.json`:

```json
{
  "PropertyGroupId": "log.patterns",
  "PropertyMap": {
    "pattern.apache": "%{COMBINEDAPACHELOG}",
    "pattern.syslog": "%{SYSLOGBASE} %{GREEDYDATA:message}",
    "pattern.custom": "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"
  }
}
```

### Supported Log Formats
- ✅ Apache/Nginx access logs
- ✅ Syslog format
- ✅ Custom application logs
- ✅ JSON structured logs
- ✅ Any Grok-parseable format

### Quick Start Examples

**Apache Access Log:**
```
127.0.0.1 - - [10/Oct/2024:13:55:36 -0700] "GET /index.html HTTP/1.1" 200 2326
```

**Application Log:**
```
2024-01-15T10:30:00.123Z INFO Application started successfully
```

**Syslog:**
```
Jan 15 10:30:00 server1 app[1234]: User login successful
```

For comprehensive guide, see [GENERIC_LOG_GUIDE.md](GENERIC_LOG_GUIDE.md)  
For Grok patterns, see [Grok Pattern Documentation](https://github.com/thekrakken/java-grok)

## Quick Start

### Local Development with Docker Compose + LocalStack

The easiest way to try the application locally:

```bash
# Run the quick start script
cd examples
./quick-start.sh

# Access Flink UI
open http://localhost:8081
```

This will:
- Build the application
- Start LocalStack (S3 + Glue)
- Start Flink cluster (JobManager + TaskManager)
- Upload sample logs to S3
- Create Glue database

See [examples/README.md](examples/README.md) for detailed instructions.

### Using Docker (Production)
```bash
# Build
mvn clean package

# Run with Docker Compose
docker-compose up -d

# Access Flink UI
open http://localhost:8081
```

### Local Development
```bash
# Build
mvn clean package

# Run
java -cp target/flink-log-processor-0.1.jar \
  com.github.ghoshp83.flinklogprocessor.UnstructuredDataProcessor
```

## Testing

The project includes comprehensive unit and integration tests. Run them with:

```bash
mvn test
```

## Documentation

### User Guides
- **[GENERIC_LOG_GUIDE.md](GENERIC_LOG_GUIDE.md)** - Comprehensive user guide
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture and design
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Deployment instructions (AWS, K8s, Docker)
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and solutions
- **[PERFORMANCE.md](PERFORMANCE.md)** - Performance benchmarks
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - How to contribute



## Project Structure

```
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── enterprise
│   │   │           └── department
│   │   │               ├── catalog        # Iceberg catalog and schema management
│   │   │               ├── config         # Configuration utilities
│   │   │               ├── functions      # Flink mapping functions
│   │   │               ├── model          # Data models
│   │   │               ├── parser         # Log parsing logic
│   │   │               └── UnstructuredDataProcessor.java  # Main application
│   │   └── resources
│   │       ├── application-properties-local.json  # Configuration file
│   │       └── log4j2.properties  # Logging configuration
│   └── test
│       ├── java  # Unit and integration tests
│       └── resources  # Test resources
├── pom.xml  # Maven configuration
└── README.md  # This file
```

## Security Considerations

### Input Validation
- **Grok Pattern Validation**: All Grok patterns are validated against a whitelist of allowed elements
- **Log Line Length Limits**: Maximum log line length is enforced (10,000 characters)
- **S3 Path Validation**: S3 paths are validated for proper format and security
- **Log Type Validation**: Log types are restricted to alphanumeric characters and underscores

### Infrastructure Security
- **Parameterized S3 Buckets**: Use organization prefix and environment variables to prevent bucket sniping
- **IAM Roles**: AWS credentials are handled through IAM roles, never hardcoded
- **Network Security**: All communication uses HTTPS/TLS encryption

### Dependency Security
- **Updated Dependencies**: All dependencies are kept up-to-date with latest security patches
- **Vulnerability Scanning**: Regular dependency vulnerability scanning in CI/CD
- **Minimal Dependencies**: Only necessary dependencies are included

### Runtime Security
- **Error Handling**: Comprehensive error handling prevents information leakage
- **Logging Security**: Sensitive data is not logged or is properly sanitized
- **Resource Limits**: Memory and processing limits prevent DoS attacks

### Configuration Security
```bash
# Set required environment variables
export ORGANIZATION_PREFIX="pralaydata"
export ENVIRONMENT="prod"

# S3 bucket names will be: pralaydata-flink-logs-prod, pralaydata-iceberg-warehouse-prod
```

## Performance Considerations

- Iceberg tables use hash distribution mode for optimal write performance
- Flink checkpointing is configured for fault tolerance
- Iceberg table properties are optimized for file size and row group size

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## Use Cases

- **Application Log Processing**: Parse and analyze application logs from microservices
- **Web Server Analytics**: Process Apache/Nginx access logs for traffic analysis
- **Security Log Analysis**: Process security logs for threat detection
- **System Monitoring**: Analyze syslog data from servers and network devices
- **IoT Data Processing**: Handle sensor and device logs at scale
- **Audit Trail Management**: Store and query audit logs with time-travel capabilities
- **Multi-Format Processing**: Handle multiple log formats in a single pipeline

## Roadmap

- [x] Generic log format support with configurable Grok patterns
- [x] Dynamic schema creation based on log structure
- [x] Comprehensive documentation and examples
- [ ] Support for Kafka as input source
- [ ] Real-time alerting based on log patterns
- [ ] Built-in data quality checks
- [ ] Support for multiple output formats (Parquet, ORC)
- [ ] Web UI for monitoring and configuration
- [ ] CI/CD pipeline with GitHub Actions
- [ ] Docker and Kubernetes deployment

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Contact

For questions or support, please open an issue on GitHub.
