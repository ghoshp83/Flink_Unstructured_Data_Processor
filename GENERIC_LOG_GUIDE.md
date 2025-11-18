# Generic Log Processing Guide

## Overview

The Flink Unstructured Data Processor now supports generic log processing, allowing you to parse and process any log format using configurable Grok patterns.

## Features

### Generic Log Record
- **Flexible Schema**: Dynamically adapts to any log format
- **Type Support**: Handles strings, integers, and other data types
- **Easy Configuration**: Define patterns in configuration files

### Supported Log Formats

Out of the box, the processor supports:
- Apache/Nginx access logs
- Syslog format
- Custom application logs
- JSON logs
- Any format definable with Grok patterns

## Quick Start

### 1. Define Your Log Pattern

Edit `application-properties-generic.json`:

```json
{
  "PropertyGroupId": "log.patterns",
  "PropertyMap": {
    "pattern.custom": "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"
  }
}
```

### 2. Configure Your Table

```json
{
  "PropertyGroupId": "iceberg.config",
  "PropertyMap": {
    "iceberg.database.name": "logs_db",
    "iceberg.table.name.generic": "parsed_logs",
    "iceberg.warehouse": "s3://your-warehouse"
  }
}
```

### 3. Process Logs

The processor will automatically:
1. Read logs from S3
2. Parse using your Grok pattern
3. Create Iceberg table with appropriate schema
4. Store parsed data

## Example Patterns

### Apache Access Log
```
%{COMBINEDAPACHELOG}
```

**Sample Log:**
```
127.0.0.1 - - [10/Oct/2024:13:55:36 -0700] "GET /index.html HTTP/1.1" 200 2326
```

**Parsed Fields:**
- clientip: 127.0.0.1
- timestamp: 10/Oct/2024:13:55:36 -0700
- verb: GET
- request: /index.html
- httpversion: 1.1
- response: 200
- bytes: 2326

### Syslog Format
```
%{SYSLOGBASE} %{GREEDYDATA:message}
```

**Sample Log:**
```
Jan 15 10:30:00 server1 app[1234]: User login successful
```

**Parsed Fields:**
- timestamp: Jan 15 10:30:00
- logsource: server1
- program: app
- pid: 1234
- message: User login successful

### Custom Application Log
```
%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}
```

**Sample Log:**
```
2024-01-15T10:30:00.123Z INFO Application started successfully
```

**Parsed Fields:**
- timestamp: 2024-01-15T10:30:00.123Z
- level: INFO
- message: Application started successfully

### JSON Log
```
%{GREEDYDATA:json_data}
```

**Sample Log:**
```json
{"timestamp":"2024-01-15T10:30:00Z","level":"INFO","service":"api","message":"Request processed"}
```

**Parsed Fields:**
- json_data: (entire JSON string, can be further processed)

## Advanced Usage

### Custom Patterns

Define your own patterns:

```json
{
  "PropertyGroupId": "log.patterns",
  "PropertyMap": {
    "pattern.myapp": "%{TIMESTAMP_ISO8601:timestamp} \\[%{DATA:thread}\\] %{LOGLEVEL:level} %{DATA:logger} - %{GREEDYDATA:message}"
  }
}
```

### Multiple Log Types

Process different log types simultaneously:

```json
{
  "PropertyGroupId": "log.patterns",
  "PropertyMap": {
    "pattern.apache": "%{COMBINEDAPACHELOG}",
    "pattern.application": "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}",
    "pattern.security": "%{TIMESTAMP_ISO8601:timestamp} %{DATA:event_type} %{DATA:user} %{GREEDYDATA:details}"
  }
}
```

### Dynamic Schema

The processor automatically creates Iceberg tables based on parsed fields:

```java
// Parsed fields from log
Map<String, Object> fields = {
    "timestamp": "2024-01-15T10:30:00Z",
    "level": "INFO",
    "message": "Test",
    "count": 42
}

// Automatically creates table with schema:
// - timestamp: STRING
// - level: STRING
// - message: STRING
// - count: INTEGER
// - event_date: STRING (partition)
```

## Grok Pattern Reference

### Common Patterns

| Pattern | Description | Example |
|---------|-------------|---------|
| `%{TIMESTAMP_ISO8601}` | ISO 8601 timestamp | 2024-01-15T10:30:00.123Z |
| `%{LOGLEVEL}` | Log level | INFO, WARN, ERROR |
| `%{IP}` | IP address | 192.168.1.1 |
| `%{NUMBER}` | Numeric value | 123, 45.67 |
| `%{INT}` | Integer | 123 |
| `%{WORD}` | Single word | application |
| `%{DATA}` | Non-greedy data | any text |
| `%{GREEDYDATA}` | Greedy data | rest of line |
| `%{COMBINEDAPACHELOG}` | Apache combined log | (full format) |
| `%{SYSLOGBASE}` | Syslog base | (syslog header) |

### Named Captures

Capture fields with names:
```
%{PATTERN:field_name}
```

Example:
```
%{IP:client_ip} %{WORD:method} %{NUMBER:response_time}
```

### Optional Fields

Make fields optional:
```
(?:%{PATTERN:field_name})?
```

Example:
```
%{IP:ip} (?:%{USER:user})? %{GREEDYDATA:message}
```

## Testing Your Patterns

### 1. Use Sample Logs

Place sample logs in `src/test/resources/`:
- `sample-apache-log.txt`
- `sample-generic-log.txt`

### 2. Test Parsing

```java
GrokPatternParser parser = new GrokPatternParser();
String pattern = "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}";
GenericLogRecord record = parser.parseGenericRecord(logLine, "custom", pattern);
```

### 3. Verify Fields

```java
assertEquals("2024-01-15T10:30:00.123Z", record.getStringField("timestamp"));
assertEquals("INFO", record.getStringField("level"));
```

## Best Practices

### 1. Pattern Design
- Start simple, add complexity as needed
- Use named captures for all important fields
- Test patterns with sample data first

### 2. Performance
- Avoid overly complex patterns
- Use specific patterns instead of `%{GREEDYDATA}` when possible
- Consider field cardinality for partitioning

### 3. Schema Management
- Keep field names consistent
- Use appropriate data types
- Plan for schema evolution

### 4. Monitoring
- Monitor parsing success rate
- Track failed records
- Validate data quality

## Troubleshooting

### Pattern Not Matching

**Problem**: Logs not being parsed

**Solutions**:
1. Verify pattern syntax
2. Check for special characters (escape them)
3. Test with online Grok debugger
4. Review logs for actual format

### Missing Fields

**Problem**: Some fields are empty

**Solutions**:
1. Make fields optional with `(?:...)?`
2. Check field names in pattern
3. Verify log format consistency

### Performance Issues

**Problem**: Slow processing

**Solutions**:
1. Simplify Grok patterns
2. Increase parallelism
3. Optimize Iceberg table properties
4. Use appropriate partitioning

## Migration from Specific to Generic

### Step 1: Identify Fields
Map your specific log fields to generic pattern:

**Before (SLA-specific):**
```
vin, ecuId, eventId, timestamp, ...
```

**After (Generic):**
```
%{DATA:identifier} %{DATA:source} %{DATA:event} %{TIMESTAMP_ISO8601:timestamp} ...
```

### Step 2: Update Configuration
Replace specific configuration with generic patterns.

### Step 3: Test
Verify parsing with sample data before production deployment.

## Examples

See the following files for complete examples:
- `application-properties-generic.json` - Generic configuration
- `sample-apache-log.txt` - Apache log samples
- `sample-generic-log.txt` - Generic log samples
- `GenericLogRecordTest.java` - Unit tests

## Resources

- [Grok Pattern Documentation](https://github.com/thekrakken/java-grok)
- [Grok Debugger](https://grokdebug.herokuapp.com/)
- [Apache Flink Documentation](https://flink.apache.org)
- [Apache Iceberg Documentation](https://iceberg.apache.org)

## Support

For questions or issues:
1. Check this guide
2. Review sample files
3. Open an issue on GitHub
