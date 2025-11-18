# Architecture Documentation

## System Overview

The Flink Unstructured Data Processor is a distributed stream processing application built on Apache Flink that ingests, parses, and stores log data in Apache Iceberg tables.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Data Sources                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Apache   │  │ Syslog   │  │  Custom  │  │   JSON   │           │
│  │  Logs    │  │  Logs    │  │   Logs   │  │   Logs   │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       │             │              │              │                  │
│       └─────────────┴──────────────┴──────────────┘                 │
│                            │                                         │
└────────────────────────────┼─────────────────────────────────────────┘
                             ▼
                    ┌─────────────────┐
                    │   Amazon S3     │
                    │  (Raw Logs)     │
                    └────────┬────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Apache Flink Cluster                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     Job Manager                               │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐             │  │
│  │  │ Scheduler  │  │ Checkpoint │  │  Resource  │             │  │
│  │  │            │  │ Coordinator│  │  Manager   │             │  │
│  │  └────────────┘  └────────────┘  └────────────┘             │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                             │                                        │
│  ┌──────────────────────────┼────────────────────────────────────┐ │
│  │                    Task Managers                               │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │ │
│  │  │   Source    │  │   Parser    │  │    Sink     │          │ │
│  │  │  Operator   │─▶│  Operator   │─▶│  Operator   │          │ │
│  │  │             │  │             │  │             │          │ │
│  │  │ S3 Monitor  │  │    Grok     │  │  Iceberg    │          │ │
│  │  │ File Reader │  │   Pattern   │  │   Writer    │          │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘          │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Data Storage Layer                              │
│  ┌──────────────────┐              ┌──────────────────┐            │
│  │  Apache Iceberg  │              │   AWS Glue       │            │
│  │     Tables       │◀────────────▶│   Catalog        │            │
│  │   (Parquet)      │              │   (Metadata)     │            │
│  └────────┬─────────┘              └──────────────────┘            │
│           │                                                          │
│           ▼                                                          │
│  ┌──────────────────┐                                               │
│  │   Amazon S3      │                                               │
│  │ (Data Warehouse) │                                               │
│  └──────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### 1. Source Layer

**FileSource (S3 Monitor)**
- Continuously monitors S3 paths
- Reads new log files
- Configurable polling interval
- Supports multiple file formats

```java
FileSource<String> source = FileSource
    .forRecordStreamFormat(new TextLineInputFormat(), new Path(s3path))
    .monitorContinuously(Duration.ofMinutes(window))
    .build();
```

### 2. Processing Layer

**Grok Pattern Parser**
- Parses unstructured logs
- Configurable patterns
- Pattern caching
- Error handling

```
Input:  "2024-01-15T10:30:00Z INFO Application started"
Pattern: "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"
Output: {timestamp: "2024-01-15T10:30:00Z", level: "INFO", message: "Application started"}
```

**Data Transformation**
- Maps parsed data to RowData
- Type conversion
- Field validation
- Schema inference

### 3. Sink Layer

**Iceberg Writer**
- Writes to Iceberg tables
- ACID guarantees
- Schema evolution
- Partitioning support

## Data Flow

```
┌──────────┐
│ Raw Log  │
│  File    │
└────┬─────┘
     │
     ▼
┌──────────────────┐
│  File Source     │  Read line by line
│  (S3 Monitor)    │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│  Process         │  Parse with Grok
│  Function        │  pattern
│  (Parser)        │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│  Map Function    │  Convert to
│  (Transformer)   │  RowData
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│  Iceberg Sink    │  Write to table
│  (Writer)        │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│  Iceberg Table   │  Stored in S3
│  (Storage)       │  with Glue catalog
└──────────────────┘
```

## Deployment Architectures

### Local Development

```
┌─────────────────────────────────────┐
│         Developer Machine            │
│  ┌────────────────────────────────┐ │
│  │  Flink Standalone Cluster      │ │
│  │  ┌──────────┐  ┌──────────┐   │ │
│  │  │   JM     │  │   TM     │   │ │
│  │  └──────────┘  └──────────┘   │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │  LocalStack (S3 + Glue)        │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### Docker Deployment

```
┌─────────────────────────────────────────────┐
│         Docker Compose                       │
│  ┌────────────┐  ┌────────────┐            │
│  │ JobManager │  │TaskManager │            │
│  │ Container  │  │ Container  │            │
│  └─────┬──────┘  └─────┬──────┘            │
│        │                │                    │
│        └────────┬───────┘                    │
│                 │                            │
│  ┌──────────────┴──────────────┐            │
│  │   LocalStack Container      │            │
│  │   (S3 + Glue)                │            │
│  └─────────────────────────────┘            │
└─────────────────────────────────────────────┘
```

### AWS Deployment

```
┌──────────────────────────────────────────────────────────┐
│                      AWS Cloud                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Kinesis Data Analytics (Flink)                    │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐        │  │
│  │  │   JM     │  │   TM     │  │   TM     │        │  │
│  │  └──────────┘  └──────────┘  └──────────┘        │  │
│  └────────────────────────────────────────────────────┘  │
│                         │                                 │
│  ┌──────────────────────┼──────────────────────────────┐ │
│  │                      ▼                               │ │
│  │  ┌─────────┐  ┌─────────┐  ┌──────────┐           │ │
│  │  │   S3    │  │  Glue   │  │   IAM    │           │ │
│  │  │ (Input) │  │(Catalog)│  │  (Auth)  │           │ │
│  │  └─────────┘  └─────────┘  └──────────┘           │ │
│  │                                                      │ │
│  │  ┌─────────────────────────────────────┐           │ │
│  │  │  S3 (Iceberg Warehouse)             │           │ │
│  │  └─────────────────────────────────────┘           │ │
│  └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### Kubernetes Deployment

```
┌──────────────────────────────────────────────────────────┐
│              Kubernetes Cluster                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Namespace: flink-logs                             │  │
│  │  ┌──────────────────┐  ┌──────────────────┐       │  │
│  │  │  JobManager Pod  │  │ TaskManager Pod  │       │  │
│  │  │  ┌────────────┐  │  │  ┌────────────┐ │       │  │
│  │  │  │ Container  │  │  │  │ Container  │ │       │  │
│  │  │  └────────────┘  │  │  └────────────┘ │       │  │
│  │  └──────────────────┘  └──────────────────┘       │  │
│  │                                                     │  │
│  │  ┌──────────────────────────────────────┐         │  │
│  │  │  Service: flink-jobmanager           │         │  │
│  │  │  Type: LoadBalancer                  │         │  │
│  │  └──────────────────────────────────────┘         │  │
│  └────────────────────────────────────────────────────┘  │
│                         │                                 │
│                         ▼                                 │
│  ┌──────────────────────────────────────────────────┐   │
│  │  External Services (S3, Glue)                    │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

## Scalability

### Horizontal Scaling
- Add more TaskManagers
- Increase parallelism
- Partition data by key

### Vertical Scaling
- Increase memory per TaskManager
- Add more CPU cores
- Optimize JVM settings

## Fault Tolerance

### Checkpointing
```
Time ──────────────────────────────────────▶
       │         │         │         │
       CP1       CP2       CP3       CP4
       │         │         │         │
    [State]   [State]   [State]   [State]
       │         │         │         │
       └─────────┴─────────┴─────────┘
              Stored in S3
```

### Recovery
1. Job fails
2. Restore from last checkpoint
3. Resume processing
4. Exactly-once semantics maintained

## Performance Optimization

### Parallelism Strategy
```
Source (P=4) ──▶ Parser (P=8) ──▶ Sink (P=4)
```

### Memory Management
- TaskManager: 4GB
- Network buffers: 512MB
- Managed memory: 2GB
- JVM heap: 1.5GB

### Checkpoint Strategy
- Interval: 5 minutes
- Timeout: 10 minutes
- Mode: Exactly-once
- Storage: S3

## Monitoring Points

1. **Source**: Files read/sec, bytes read
2. **Parser**: Parse success rate, errors
3. **Sink**: Records written, write latency
4. **System**: CPU, memory, network

## Security

### Data Flow Security
```
S3 (Encrypted) ──▶ Flink (In-memory) ──▶ Iceberg (Encrypted)
     │                    │                      │
     └────────────────────┴──────────────────────┘
              TLS/SSL in transit
```

### Access Control
- IAM roles for AWS resources
- RBAC for Kubernetes
- Network policies
- Encryption at rest and in transit

## Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Stream Processing | Apache Flink 1.18.1 | Core engine |
| Data Lake | Apache Iceberg 1.6.1 | Table format |
| Storage | Amazon S3 | Object storage |
| Catalog | AWS Glue | Metadata |
| Pattern Matching | Grok | Log parsing |
| Container | Docker | Packaging |
| Orchestration | Kubernetes | Deployment |
| CI/CD | GitHub Actions | Automation |

## Design Patterns

### 1. Pipeline Pattern
Sequential processing stages with clear boundaries

### 2. Strategy Pattern
Configurable Grok patterns for different log types

### 3. Factory Pattern
Dynamic schema creation based on log structure

### 4. Observer Pattern
Continuous S3 monitoring for new files

## Future Enhancements

- [ ] Kafka source support
- [ ] Real-time alerting
- [ ] ML-based anomaly detection
- [ ] Multi-cloud support
- [ ] GraphQL API
- [ ] Web UI dashboard

---

For deployment instructions, see [DEPLOYMENT.md](DEPLOYMENT.md)  
For troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
