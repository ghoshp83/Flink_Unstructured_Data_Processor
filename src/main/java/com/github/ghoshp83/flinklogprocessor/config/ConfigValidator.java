/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.config;

import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Slf4j
public class ConfigValidator {
    
    public static ValidationResult validate(Map<String, Properties> propertiesMap) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (propertiesMap == null || propertiesMap.isEmpty()) {
            errors.add("Properties map is null or empty");
            return new ValidationResult(false, errors, warnings);
        }
        
        // Validate job config
        validateJobConfig(propertiesMap, errors, warnings);
        
        // Validate iceberg config
        validateIcebergConfig(propertiesMap, errors, warnings);
        
        // Validate log patterns
        validateLogPatterns(propertiesMap, errors, warnings);
        
        boolean isValid = errors.isEmpty();
        if (isValid) {
            log.info("Configuration validation passed with {} warnings", warnings.size());
        } else {
            log.error("Configuration validation failed with {} errors", errors.size());
        }
        
        return new ValidationResult(isValid, errors, warnings);
    }
    
    private static void validateJobConfig(Map<String, Properties> propertiesMap, 
                                          List<String> errors, List<String> warnings) {
        Properties jobConfig = propertiesMap.get("job.config");
        if (jobConfig == null) {
            errors.add("job.config is missing");
            return;
        }
        
        String s3Path = jobConfig.getProperty("source.s3path");
        if (s3Path == null || s3Path.trim().isEmpty()) {
            errors.add("source.s3path is required");
        } else if (!s3Path.startsWith("s3://") && !s3Path.startsWith("s3a://")) {
            errors.add("source.s3path must start with s3:// or s3a://");
        }
        
        String window = jobConfig.getProperty("source.window");
        if (window == null) {
            warnings.add("source.window not set, using default");
        } else {
            try {
                long windowValue = Long.parseLong(window);
                if (windowValue <= 0) {
                    errors.add("source.window must be positive");
                }
            } catch (NumberFormatException e) {
                errors.add("source.window must be a valid number");
            }
        }
    }
    
    private static void validateIcebergConfig(Map<String, Properties> propertiesMap,
                                             List<String> errors, List<String> warnings) {
        Properties icebergConfig = propertiesMap.get("iceberg.config");
        if (icebergConfig == null) {
            errors.add("iceberg.config is missing");
            return;
        }
        
        String warehouse = icebergConfig.getProperty("iceberg.warehouse");
        if (warehouse == null || warehouse.trim().isEmpty()) {
            errors.add("iceberg.warehouse is required");
        }
        
        String database = icebergConfig.getProperty("iceberg.database.name");
        if (database == null || database.trim().isEmpty()) {
            errors.add("iceberg.database.name is required");
        }
        
        String parallelism = icebergConfig.getProperty("iceberg.write.parallelism");
        if (parallelism != null) {
            try {
                int p = Integer.parseInt(parallelism);
                if (p <= 0 || p > 100) {
                    warnings.add("iceberg.write.parallelism should be between 1 and 100");
                }
            } catch (NumberFormatException e) {
                errors.add("iceberg.write.parallelism must be a valid number");
            }
        }
    }
    
    private static void validateLogPatterns(Map<String, Properties> propertiesMap,
                                           List<String> errors, List<String> warnings) {
        Properties jobConfig = propertiesMap.get("job.config");
        if (jobConfig == null) return;
        
        String logType = jobConfig.getProperty("log.type");
        String logPattern = jobConfig.getProperty("log.pattern");
        
        if (logPattern == null || logPattern.trim().isEmpty()) {
            warnings.add("log.pattern not set, using default");
        } else if (!logPattern.contains("%{")) {
            warnings.add("log.pattern doesn't appear to be a valid Grok pattern");
        }
        
        if (logType != null && !logType.matches("^[a-zA-Z0-9_-]+$")) {
            errors.add("log.type contains invalid characters");
        }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation Result: ").append(valid ? "PASSED" : "FAILED").append("\n");
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
            }
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
            }
            return sb.toString();
        }
    }
}
