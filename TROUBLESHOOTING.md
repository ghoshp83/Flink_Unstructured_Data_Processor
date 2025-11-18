# Troubleshooting Guide

## Common Issues and Solutions

### Build Issues

#### Maven Build Fails
**Symptoms**: Build errors, compilation failures

**Solutions**:
```bash
# Clean and rebuild
mvn clean install

# Skip tests if needed
mvn clean package -DskipTests

# Update dependencies
mvn dependency:resolve
```

#### Dependency Conflicts
**Symptoms**: ClassNotFoundException, NoSuchMethodError

**Solutions**:
- Check `mvn dependency:tree`
- Exclude conflicting dependencies in pom.xml
- Verify Flink version compatibility

---

### Runtime Issues

#### Application Won't Start
**Symptoms**: Job fails immediately after submission

**Checklist**:
- [ ] Verify JAR file exists
- [ ] Check configuration file path
- [ ] Validate AWS credentials
- [ ] Review application logs
- [ ] Confirm S3 bucket access

**Debug**:
```bash
# Check logs
tail -f logs/flink-*.log

# Verify configuration
cat src/main/resources/application-properties-generic.json

# Test AWS access
aws s3 ls s3://your-bucket/
```

#### No Data Processing
**Symptoms**: Job runs but no output

**Checklist**:
- [ ] Verify S3 input path has files
- [ ] Check Grok pattern matches log format
- [ ] Confirm IAM permissions
- [ ] Review Flink UI for errors

**Debug**:
```bash
# List S3 files
aws s3 ls s3://your-bucket/logs/ --recursive

# Test Grok pattern
# Use https://grokdebug.herokuapp.com/

# Check Flink metrics
curl http://localhost:8081/jobs/<job-id>/metrics
```

---

### Parsing Issues

#### Logs Not Parsing
**Symptoms**: Empty or null records

**Solutions**:
1. **Verify Pattern**:
   ```bash
   # Test pattern online
   # https://grokdebug.herokuapp.com/
   ```

2. **Check Log Format**:
   ```bash
   # View sample log
   aws s3 cp s3://your-bucket/logs/sample.log -
   ```

3. **Update Pattern**:
   ```json
   {
     "pattern.custom": "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"
   }
   ```

#### Partial Field Extraction
**Symptoms**: Some fields are empty

**Solutions**:
- Make optional fields: `(?:%{PATTERN:field})?`
- Check for special characters (escape them)
- Verify field names match configuration

---

### Performance Issues

#### High Memory Usage
**Symptoms**: OutOfMemoryError, frequent GC

**Solutions**:
```properties
# Increase taskmanager memory
taskmanager.memory.process.size=4096m

# Adjust parallelism
parallelism.default=2

# Tune checkpoint interval
execution.checkpointing.interval=300000
```

#### Slow Processing
**Symptoms**: Backpressure, low throughput

**Solutions**:
1. **Increase Parallelism**:
   ```properties
   parallelism.default=8
   taskmanager.numberOfTaskSlots=4
   ```

2. **Optimize Grok Patterns**:
   - Use specific patterns instead of GREEDYDATA
   - Avoid complex regex

3. **Tune Iceberg Writes**:
   ```json
   {
     "write.target-file-size-bytes": "536870912",
     "write.distribution-mode": "hash"
   }
   ```

#### Checkpoint Failures
**Symptoms**: Checkpoint timeouts, job restarts

**Solutions**:
```properties
# Increase timeout
execution.checkpointing.timeout=600000

# Reduce frequency
execution.checkpointing.interval=300000

# Check state backend
state.backend=rocksdb
state.checkpoints.dir=s3://your-bucket/checkpoints/
```

---

### AWS Issues

#### S3 Access Denied
**Symptoms**: AccessDeniedException

**Solutions**:
1. **Check IAM Policy**:
   ```json
   {
     "Effect": "Allow",
     "Action": ["s3:GetObject", "s3:ListBucket"],
     "Resource": ["arn:aws:s3:::your-bucket/*"]
   }
   ```

2. **Verify Credentials**:
   ```bash
   aws sts get-caller-identity
   aws s3 ls s3://your-bucket/
   ```

#### Glue Catalog Errors
**Symptoms**: Table not found, schema mismatch

**Solutions**:
```bash
# Check database exists
aws glue get-database --name logs_db

# List tables
aws glue get-tables --database-name logs_db

# Verify permissions
aws glue get-table --database-name logs_db --name parsed_logs
```

---

### Docker Issues

#### Container Won't Start
**Symptoms**: Container exits immediately

**Debug**:
```bash
# Check logs
docker logs flink-jobmanager

# Inspect container
docker inspect flink-jobmanager

# Run interactively
docker run -it flink-log-processor:latest /bin/bash
```

#### Network Issues
**Symptoms**: Services can't communicate

**Solutions**:
```bash
# Check network
docker network ls
docker network inspect flink-network

# Verify DNS
docker exec flink-taskmanager ping jobmanager
```

---

### Kubernetes Issues

#### Pods Not Starting
**Symptoms**: CrashLoopBackOff, ImagePullBackOff

**Debug**:
```bash
# Check pod status
kubectl get pods -n flink-logs

# View logs
kubectl logs -f pod/flink-jobmanager-xxx -n flink-logs

# Describe pod
kubectl describe pod flink-jobmanager-xxx -n flink-logs
```

#### Resource Issues
**Symptoms**: Pods evicted, OOMKilled

**Solutions**:
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

### Data Quality Issues

#### Duplicate Records
**Symptoms**: Same log appears multiple times

**Solutions**:
- Enable exactly-once semantics
- Check checkpoint configuration
- Verify S3 file monitoring window

#### Missing Records
**Symptoms**: Some logs not processed

**Solutions**:
- Check for parsing errors in logs
- Verify Grok pattern coverage
- Review dead letter queue (if configured)

#### Schema Mismatch
**Symptoms**: Type conversion errors

**Solutions**:
- Verify field types in Grok pattern
- Check Iceberg table schema
- Update schema if needed

---

## Debugging Tools

### Flink UI
```
http://localhost:8081
```
- Job status
- Task metrics
- Checkpoint history
- Exception logs

### Logs
```bash
# Application logs
tail -f logs/flink-*.log

# Docker logs
docker-compose logs -f

# Kubernetes logs
kubectl logs -f deployment/flink-jobmanager -n flink-logs
```

### Metrics
```bash
# Flink metrics
curl http://localhost:8081/jobs/<job-id>/metrics

# JVM metrics
jconsole localhost:9010
```

---

## Getting Help

### Before Opening an Issue

1. **Check Documentation**:
   - [README.md](README.md)
   - [GENERIC_LOG_GUIDE.md](GENERIC_LOG_GUIDE.md)
   - [DEPLOYMENT.md](DEPLOYMENT.md)

2. **Gather Information**:
   - Flink version
   - Java version
   - Error messages
   - Configuration files
   - Sample log data

3. **Try Solutions**:
   - Search existing issues
   - Review troubleshooting guide
   - Test with sample data

### Opening an Issue

Include:
- Clear description of problem
- Steps to reproduce
- Expected vs actual behavior
- Environment details
- Relevant logs/errors
- Configuration files (sanitized)

---

## Quick Fixes

### Reset Everything
```bash
# Stop all services
docker-compose down -v

# Clean build
mvn clean

# Rebuild
mvn package

# Restart
docker-compose up -d
```

### Clear State
```bash
# Remove checkpoints
aws s3 rm s3://your-bucket/checkpoints/ --recursive

# Remove savepoints
aws s3 rm s3://your-bucket/savepoints/ --recursive

# Drop and recreate table
aws glue delete-table --database-name logs_db --name parsed_logs
```

### Test Configuration
```bash
# Validate JSON
cat application-properties-generic.json | jq .

# Test Grok pattern
# Use online debugger: https://grokdebug.herokuapp.com/

# Verify S3 access
aws s3 ls s3://your-bucket/logs/
```

---

## Performance Checklist

- [ ] Parallelism set appropriately
- [ ] Memory limits configured
- [ ] Checkpoint interval tuned
- [ ] Grok patterns optimized
- [ ] Iceberg properties set
- [ ] Monitoring enabled
- [ ] Logs reviewed regularly

---

## Support Resources

- [Apache Flink Documentation](https://flink.apache.org/docs/)
- [Apache Iceberg Documentation](https://iceberg.apache.org/docs/)
- [Grok Patterns](https://github.com/thekrakken/java-grok)
- [AWS Kinesis Analytics](https://docs.aws.amazon.com/kinesisanalytics/)
- [GitHub Issues](https://github.com/your-repo/issues)
