# Deployment Guide

## Overview

This guide covers deploying the Flink Unstructured Data Processor in various environments.

## Prerequisites

- Java 11+
- Maven 3.6+
- Docker (for containerized deployment)
- AWS CLI (for AWS deployment)
- kubectl (for Kubernetes deployment)

---

## Local Development

### Build
```bash
mvn clean package
```

### Run
```bash
java -cp target/unstructured-dataprocessor-0.1.jar \
  com.enterprise.department.UnstructuredDataProcessor
```

---

## Docker Deployment

### Build Image
```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t flink-log-processor:latest .
```

### Run with Docker Compose
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Access Flink UI
```
http://localhost:8081
```

---

## AWS Kinesis Data Analytics

### 1. Upload JAR to S3
```bash
aws s3 cp target/unstructured-dataprocessor-0.1.jar \
  s3://your-bucket/flink-apps/
```

### 2. Create Application
```bash
aws kinesisanalyticsv2 create-application \
  --application-name flink-log-processor \
  --runtime-environment FLINK-1_18 \
  --service-execution-role arn:aws:iam::ACCOUNT:role/KDA-Role \
  --application-configuration '{
    "ApplicationCodeConfiguration": {
      "CodeContent": {
        "S3ContentLocation": {
          "BucketARN": "arn:aws:s3:::your-bucket",
          "FileKey": "flink-apps/unstructured-dataprocessor-0.1.jar"
        }
      },
      "CodeContentType": "ZIPFILE"
    }
  }'
```

### 3. Configure Properties
Upload `application-properties-generic.json` as runtime properties.

### 4. Start Application
```bash
aws kinesisanalyticsv2 start-application \
  --application-name flink-log-processor
```

---

## Kubernetes Deployment

### 1. Create Namespace
```bash
kubectl create namespace flink-logs
```

### 2. Deploy Flink Cluster
```yaml
# flink-deployment.yaml
apiVersion: v1
kind: Service
metadata:
  name: flink-jobmanager
spec:
  type: LoadBalancer
  ports:
  - name: rpc
    port: 6123
  - name: ui
    port: 8081
  selector:
    app: flink
    component: jobmanager
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flink-jobmanager
spec:
  replicas: 1
  selector:
    matchLabels:
      app: flink
      component: jobmanager
  template:
    metadata:
      labels:
        app: flink
        component: jobmanager
    spec:
      containers:
      - name: jobmanager
        image: flink-log-processor:latest
        args: ["jobmanager"]
        ports:
        - containerPort: 6123
        - containerPort: 8081
        env:
        - name: FLINK_PROPERTIES
          value: "jobmanager.rpc.address: flink-jobmanager"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flink-taskmanager
spec:
  replicas: 2
  selector:
    matchLabels:
      app: flink
      component: taskmanager
  template:
    metadata:
      labels:
        app: flink
        component: taskmanager
    spec:
      containers:
      - name: taskmanager
        image: flink-log-processor:latest
        args: ["taskmanager"]
        env:
        - name: FLINK_PROPERTIES
          value: "jobmanager.rpc.address: flink-jobmanager"
```

### 3. Apply Configuration
```bash
kubectl apply -f flink-deployment.yaml -n flink-logs
```

### 4. Access Flink UI
```bash
kubectl port-forward svc/flink-jobmanager 8081:8081 -n flink-logs
```

---

## Configuration

### Environment Variables
```bash
# AWS Configuration
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret

# S3 Configuration
export S3_INPUT_PATH=s3://your-bucket/logs/
export S3_WAREHOUSE=s3://your-bucket/warehouse/

# Flink Configuration
export FLINK_PARALLELISM=4
export CHECKPOINT_INTERVAL=60000
```

### Application Properties
Edit `application-properties-generic.json`:
```json
{
  "PropertyGroupId": "job.config",
  "PropertyMap": {
    "source.s3path": "s3://your-bucket/logs/",
    "source.window": "1"
  }
}
```

---

## Monitoring

### Flink Metrics
Access metrics at: `http://jobmanager:8081/metrics`

### CloudWatch (AWS)
```bash
aws logs tail /aws/kinesisanalytics/flink-log-processor --follow
```

### Prometheus (Kubernetes)
Add Prometheus annotations to deployment:
```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "9249"
```

---

## Scaling

### Horizontal Scaling
```bash
# Kubernetes
kubectl scale deployment flink-taskmanager --replicas=4 -n flink-logs

# Docker Compose
docker-compose up -d --scale taskmanager=4
```

### Vertical Scaling
Update resource limits in deployment configuration:
```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"
```

---

## Troubleshooting

### Check Logs
```bash
# Docker
docker-compose logs -f jobmanager

# Kubernetes
kubectl logs -f deployment/flink-jobmanager -n flink-logs

# AWS
aws logs tail /aws/kinesisanalytics/flink-log-processor
```

### Common Issues

**Issue**: Application fails to start
- Check JAR file exists
- Verify configuration file
- Check AWS credentials

**Issue**: No data processing
- Verify S3 path is correct
- Check IAM permissions
- Review Grok patterns

**Issue**: High memory usage
- Reduce parallelism
- Adjust checkpoint interval
- Increase taskmanager memory

---

## Security

### IAM Permissions (AWS)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:ListBucket",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::your-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "glue:GetDatabase",
        "glue:GetTable",
        "glue:CreateTable",
        "glue:UpdateTable"
      ],
      "Resource": "*"
    }
  ]
}
```

### Network Security
- Restrict Flink UI access (port 8081)
- Use VPC endpoints for AWS services
- Enable SSL/TLS for data in transit

---

## Backup & Recovery

### Savepoints
```bash
# Create savepoint
flink savepoint <job-id> s3://your-bucket/savepoints/

# Restore from savepoint
flink run -s s3://your-bucket/savepoints/savepoint-xxx \
  target/unstructured-dataprocessor-0.1.jar
```

### Checkpoints
Configured in application properties:
```properties
execution.checkpointing.interval=60000
execution.checkpointing.mode=EXACTLY_ONCE
state.backend=rocksdb
state.checkpoints.dir=s3://your-bucket/checkpoints/
```

---

## Performance Tuning

### Parallelism
```properties
parallelism.default=4
taskmanager.numberOfTaskSlots=2
```

### Memory
```properties
taskmanager.memory.process.size=4096m
taskmanager.memory.flink.size=3072m
```

### Checkpointing
```properties
execution.checkpointing.interval=300000
execution.checkpointing.min-pause=60000
execution.checkpointing.timeout=600000
```

---

## Maintenance

### Updates
1. Build new version
2. Create savepoint
3. Stop application
4. Deploy new version
5. Restore from savepoint

### Monitoring Checklist
- [ ] Check Flink UI for job status
- [ ] Monitor CPU/Memory usage
- [ ] Review error logs
- [ ] Verify data processing rate
- [ ] Check checkpoint success rate

---

## Support

For issues or questions:
- Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- Review [GENERIC_LOG_GUIDE.md](GENERIC_LOG_GUIDE.md)
- Open GitHub issue
