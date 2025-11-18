#!/bin/bash

set -e

echo "🚀 Flink Log Processor - Quick Start"
echo "===================================="
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    echo "❌ Docker not found. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose not found. Please install Docker Compose first."
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven first."
    exit 1
fi

echo "✅ All prerequisites met"
echo ""

# Build application
echo "🔨 Building application..."
cd ..
mvn clean package -DskipTests
echo "✅ Build complete"
echo ""

# Start services
echo "🐳 Starting Docker services..."
docker-compose -f docker-compose-local.yml up -d
echo "✅ Services started"
echo ""

# Wait for services
echo "⏳ Waiting for services to be ready..."
sleep 15

# Check LocalStack
echo "🔍 Verifying LocalStack..."
docker exec flink-localstack awslocal s3 ls > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ LocalStack is ready"
else
    echo "❌ LocalStack not ready. Check logs: docker logs flink-localstack"
    exit 1
fi

# Check Flink
echo "🔍 Verifying Flink..."
curl -s http://localhost:8081/overview > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Flink is ready"
else
    echo "❌ Flink not ready. Check logs: docker logs flink-jobmanager"
    exit 1
fi

echo ""
echo "✨ Environment is ready!"
echo ""
echo "📊 Access Points:"
echo "   - Flink UI: http://localhost:8081"
echo "   - LocalStack: http://localhost:4566"
echo ""
echo "📝 Next steps:"
echo "   1. View Flink UI: open http://localhost:8081"
echo "   2. Submit job: docker exec flink-jobmanager flink run -c com.github.ghoshp83.flinklogprocessor.UnstructuredDataProcessor /opt/flink/usrlib/flink-log-processor-0.1.jar"
echo "   3. Check logs: docker logs flink-jobmanager"
echo ""
echo "🛑 To stop: docker-compose -f docker-compose-local.yml down"
echo ""
