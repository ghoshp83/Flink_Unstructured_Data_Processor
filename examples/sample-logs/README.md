# Sample Log Files

This folder contains various log format examples for testing the Flink Log Processor.

## Log Files

### 1. application.log
**Format**: Standard application logs with ISO8601 timestamp, log level, and message

**Example**:
```
2024-01-15T10:30:00.123Z INFO Application started successfully
```

**Grok Pattern**:
```
%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}
```

**Use Case**: Microservices, containerized applications, cloud-native apps

---

### 2. apache-access.log
**Format**: Apache/Nginx Combined Log Format

**Example**:
```
127.0.0.1 - - [15/Jan/2024:10:30:00 +0000] "GET /index.html HTTP/1.1" 200 2326
```

**Grok Pattern**:
```
%{COMBINEDAPACHELOG}
```

**Use Case**: Web server access logs, API gateway logs, load balancer logs

---

### 3. syslog.log
**Format**: Standard syslog format

**Example**:
```
Jan 15 10:30:00 server1 sshd[12345]: Accepted publickey for admin from 192.168.1.100
```

**Grok Pattern**:
```
%{SYSLOGBASE} %{GREEDYDATA:message}
```

**Use Case**: System logs, security logs, infrastructure monitoring

---

### 4. json-structured.log
**Format**: JSON structured logs (one JSON object per line)

**Example**:
```json
{"timestamp":"2024-01-15T10:30:00.123Z","level":"INFO","service":"api-gateway","message":"Request received"}
```

**Grok Pattern**:
```
%{GREEDYDATA:json}
```
*Note: Requires JSON parsing after Grok extraction*

**Use Case**: Modern microservices, cloud-native applications, structured logging

---

### 5. nginx-error.log
**Format**: Nginx error log format

**Example**:
```
2024/01/15 10:30:00 [error] 12345#12345: *1 connect() failed (111: Connection refused)
```

**Grok Pattern**:
```
%{YEAR}/%{MONTHNUM}/%{MONTHDAY} %{TIME} \[%{LOGLEVEL:level}\] %{GREEDYDATA:message}
```

**Use Case**: Nginx/web server error logs, debugging connection issues

---

### 6. custom-app.log
**Format**: Custom application log with structured fields

**Example**:
```
[2024-01-15 10:30:00.123] [INFO] [UserService] User login successful - userId=12345
```

**Grok Pattern**:
```
\[%{TIMESTAMP_ISO8601:timestamp}\] \[%{WORD:level}\] \[%{WORD:service}\] %{GREEDYDATA:message}
```

**Use Case**: Custom enterprise applications, legacy systems

---

## Testing Different Formats

### Quick Test with Application Logs
```bash
# Update config to use application.log pattern
{
  "log.type": "application",
  "log.pattern": "%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}"
}
```

### Quick Test with Apache Logs
```bash
# Update config to use apache pattern
{
  "log.type": "apache",
  "log.pattern": "%{COMBINEDAPACHELOG}"
}
```

### Quick Test with Syslog
```bash
# Update config to use syslog pattern
{
  "log.type": "syslog",
  "log.pattern": "%{SYSLOGBASE} %{GREEDYDATA:message}"
}
```

## Adding Your Own Logs

1. Create a new `.log` file in this folder
2. Add your log entries (one per line)
3. Define the appropriate Grok pattern
4. Update `init-localstack.sh` to upload your file:
   ```bash
   awslocal s3 cp /tmp/sample-logs/your-log.log s3://flink-logs/
   ```
5. Update configuration with your pattern
6. Restart the environment

## Log Volume Testing

Generate larger log files for performance testing:

```bash
# Generate 10,000 application log entries
for i in {1..10000}; do
  echo "2024-01-15T10:$((i/600)):$((i%60)).123Z INFO Processing record $i" >> large-app.log
done

# Generate 10,000 Apache log entries
for i in {1..10000}; do
  echo "192.168.1.$((i%256)) - - [15/Jan/2024:10:$((i/600)):$((i%60)) +0000] \"GET /api/data/$i HTTP/1.1\" 200 $((RANDOM%10000))" >> large-apache.log
done
```

## Grok Pattern Resources

- [Grok Debugger](https://grokdebugger.com/)
- [Logstash Grok Patterns](https://github.com/logstash-plugins/logstash-patterns-core/tree/main/patterns)
- [Java Grok Library](https://github.com/thekrakken/java-grok)

## Common Patterns

### Timestamp Patterns
- `%{TIMESTAMP_ISO8601}` - 2024-01-15T10:30:00.123Z
- `%{HTTPDATE}` - 15/Jan/2024:10:30:00 +0000
- `%{SYSLOGTIMESTAMP}` - Jan 15 10:30:00

### Log Level Patterns
- `%{LOGLEVEL}` - INFO, ERROR, WARN, DEBUG
- `%{WORD:level}` - Any word as level

### IP Address Patterns
- `%{IP}` - IPv4 or IPv6
- `%{IPV4}` - IPv4 only
- `%{IPV6}` - IPv6 only

### Number Patterns
- `%{INT}` - Integer
- `%{NUMBER}` - Integer or float
- `%{BASE10NUM}` - Base 10 number

## Tips

1. **Start Simple**: Begin with basic patterns and add complexity
2. **Test Incrementally**: Test each pattern component separately
3. **Use Named Captures**: Always name your captures (e.g., `%{WORD:level}`)
4. **Escape Special Characters**: Use `\\` to escape regex special chars
5. **Performance**: Simpler patterns parse faster

## Troubleshooting

### Pattern Not Matching
- Use [Grok Debugger](https://grokdebugger.com/) to test patterns
- Check for extra spaces or special characters
- Verify timestamp format matches exactly

### Slow Parsing
- Simplify complex patterns
- Avoid excessive use of `%{GREEDYDATA}`
- Use specific patterns instead of generic ones

### Missing Fields
- Ensure all fields are named in pattern
- Check for optional fields (use `(?:pattern)?`)
- Verify field names match schema
