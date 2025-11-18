package com.github.ghoshp83.flinklogprocessor.catalog;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.github.ghoshp83.flinklogprocessor.config.LogConf.PARTITION_DATE;
import static org.junit.jupiter.api.Assertions.*;

class IcebergSchemaManagerTest {

    @Test
    void testGetPartitionSpec() {
        // Given
        Schema schema = new Schema(
                Types.NestedField.required(1, "id", Types.LongType.get()),
                Types.NestedField.required(2, PARTITION_DATE, Types.StringType.get())
        );
        
        // When
        PartitionSpec partitionSpec = IcebergSchemaManager.getPartitionSpec(schema, "test-signal");
        
        // Then
        assertNotNull(partitionSpec);
        assertEquals(1, partitionSpec.fields().size());
        assertEquals(PARTITION_DATE, partitionSpec.fields().get(0).name());
        assertEquals("identity", partitionSpec.fields().get(0).transform().toString());
    }

    @ParameterizedTest
    @MethodSource("typeConversionTestCases")
    void testConvertToIcebergType(String typeName, Class<? extends Type> expectedType) {
        // When
        Type actualType = IcebergSchemaManager.convertToIcebergType(typeName);
        
        // Then
        assertNotNull(actualType);
        assertEquals(expectedType, actualType.getClass());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"string", "STRING", "String"})
    void testStringTypeConversionIsCaseInsensitive(String typeName) {
        // When
        Type actualType = IcebergSchemaManager.convertToIcebergType(typeName);
        
        // Then
        assertNotNull(actualType);
        assertEquals(Types.StringType.class, actualType.getClass());
    }
    
    @Test
    void testConvertToIcebergTypeWithInvalidType() {
        // When/Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            IcebergSchemaManager.convertToIcebergType("unsupported_type");
        });
        
        assertEquals("Unsupported type: unsupported_type", exception.getMessage());
    }
    
    private static Stream<Arguments> typeConversionTestCases() {
        return Stream.of(
                Arguments.of("string", Types.StringType.class),
                Arguments.of("int", Types.IntegerType.class),
                Arguments.of("long", Types.LongType.class),
                Arguments.of("boolean", Types.BooleanType.class),
                Arguments.of("double", Types.DoubleType.class)
        );
    }
}
