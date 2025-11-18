FROM flink:1.18.1-scala_2.12-java11

# Set environment variables for pralayData
ENV ORGANIZATION_PREFIX=pralaydata
ENV ENVIRONMENT=dev

# Copy the application JAR
COPY target/flink-log-processor-0.1.jar /opt/flink/usrlib/

# Copy configuration files
COPY src/main/resources/*.json /opt/flink/conf/

# Set working directory
WORKDIR /opt/flink