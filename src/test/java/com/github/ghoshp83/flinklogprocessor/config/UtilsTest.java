package com.github.ghoshp83.flinklogprocessor.config;

import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @Mock
    private StreamExecutionEnvironment mockEnv;

    @Test
    void testConfigureEnvironment() {
        // When
        try (MockedStatic<StreamExecutionEnvironment> mockedStatic = Mockito.mockStatic(StreamExecutionEnvironment.class)) {
            // Mock the environment and its config
            when(mockEnv.getConfig()).thenReturn(Mockito.mock(org.apache.flink.api.common.ExecutionConfig.class));
            mockedStatic.when(StreamExecutionEnvironment::getExecutionEnvironment).thenReturn(mockEnv);
            
            // Execute the method
            StreamExecutionEnvironment result = Utils.configureEnvironment();
            
            // Then
            assertNotNull(result);
            assertEquals(mockEnv, result);
            
            // Verify interactions
            mockedStatic.verify(StreamExecutionEnvironment::getExecutionEnvironment);
            Mockito.verify(mockEnv).getConfig();
            Mockito.verify(mockEnv).enableCheckpointing(5000L);
        }
    }
    
    @Test
    void testConfigureEnvironmentWithLocalEnvironment() {
        // Given
        LocalStreamEnvironment localEnv = Mockito.mock(LocalStreamEnvironment.class);
        when(localEnv.getConfig()).thenReturn(Mockito.mock(org.apache.flink.api.common.ExecutionConfig.class));
        
        // When
        try (MockedStatic<StreamExecutionEnvironment> mockedStatic = Mockito.mockStatic(StreamExecutionEnvironment.class)) {
            mockedStatic.when(StreamExecutionEnvironment::getExecutionEnvironment).thenReturn(localEnv);
            
            // Execute the method
            StreamExecutionEnvironment result = Utils.configureEnvironment();
            
            // Then
            assertNotNull(result);
            assertEquals(localEnv, result);
            
            // Verify interactions
            Mockito.verify(localEnv).setParallelism(1);
        }
    }
    
    @Test
    void testGetKafkaSaslProperties() {
        // Test with local environment
        LocalStreamEnvironment localEnv = Mockito.mock(LocalStreamEnvironment.class);
        Properties localProps = Utils.getKafkaSaslProperties(localEnv);
        assertNotNull(localProps);
        assertTrue(localProps.isEmpty());
        
        // Test with non-local environment
        StreamExecutionEnvironment nonLocalEnv = Mockito.mock(StreamExecutionEnvironment.class);
        Properties nonLocalProps = Utils.getKafkaSaslProperties(nonLocalEnv);
        assertNotNull(nonLocalProps);
        assertEquals("SASL_SSL", nonLocalProps.getProperty("security.protocol"));
        assertEquals("AWS_MSK_IAM", nonLocalProps.getProperty("sasl.mechanism"));
    }
    
    @Test
    void testAnonymizeString() {
        // Test with normal string
        String input = "test1234";
        String result = Utils.anonymizeString(input, 4);
        assertEquals("test****", result);
        
        // Test with null
        assertNull(Utils.anonymizeString(null, 0));
        
        // Test with empty string
        assertEquals("", Utils.anonymizeString("", 0));
        
        // Test with start index beyond string length
        assertEquals("test", Utils.anonymizeString("test", 10));
    }
    
    @Test
    void testAnonymizePIIValueInString() {
        // Test with vuid
        String withVuid = "{\"vuid\":\"abc123def456\", \"other\":\"value\"}";
        String anonymizedVuid = Utils.anonymizePIIValueInString(withVuid);
        // Check that the string contains "vuid" and has some asterisks in it
        assertTrue(anonymizedVuid.contains("\"vuid\":"), "Expected string to contain vuid key");
        assertTrue(anonymizedVuid.contains("*"), "Expected string to contain asterisks");
        
        // Test with vp_id
        String withVpId = "{\"vp_id\":\"xyz789\", \"other\":\"value\"}";
        String anonymizedVpId = Utils.anonymizePIIValueInString(withVpId);
        // Check that the string contains "vp_id" and has some asterisks in it
        assertTrue(anonymizedVpId.contains("\"vp_id\":"), "Expected string to contain vp_id key");
        assertTrue(anonymizedVpId.contains("*"), "Expected string to contain asterisks");
        
        // Test with both
        String withBoth = "{\"vuid\":\"abc123\", \"vp_id\":\"xyz789\"}";
        String anonymizedBoth = Utils.anonymizePIIValueInString(withBoth);
        // Check that both fields are present and have asterisks
        assertTrue(anonymizedBoth.contains("\"vuid\":"), "Expected string to contain vuid key");
        assertTrue(anonymizedBoth.contains("\"vp_id\":"), "Expected string to contain vp_id key");
        assertTrue(anonymizedBoth.contains("*"), "Expected string to contain asterisks");
        
        // Test with no PII
        String noPii = "{\"normal\":\"value\"}";
        assertEquals(noPii, Utils.anonymizePIIValueInString(noPii));
        
        // Test with null
        assertNull(Utils.anonymizePIIValueInString(null));
    }
}
