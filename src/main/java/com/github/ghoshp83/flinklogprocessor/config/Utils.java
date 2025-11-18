/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.config;

import com.alibaba.fastjson2.JSONObject;
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.IOException;
import java.util.*;


@Slf4j
public class Utils {
  private Utils() {
  }

  private static final String ERROR_MESSAGE_TYPE = "Diagnostics-Decoder-Engine, error_type=app-error";

  public static StreamExecutionEnvironment configureEnvironment() {

    final long CHECKPOINT_INTERVAL = 5000L;
    final StreamExecutionEnvironment sEnv = StreamExecutionEnvironment.getExecutionEnvironment();
    sEnv.getConfig().enableObjectReuse();
    sEnv.enableCheckpointing(CHECKPOINT_INTERVAL); // overridden by KDA environment variables
    if (sEnv instanceof LocalStreamEnvironment) {
      sEnv.setParallelism(1);
    }
    return sEnv;
  }

  public static Map<String, Properties> initPropertiesMap(StreamExecutionEnvironment sEnv,
                                                          String propertiesFile) {

    Map<String, Properties> applicationPropertiesMap = null;
    try {
      if (sEnv instanceof LocalStreamEnvironment) {
        applicationPropertiesMap = KinesisAnalyticsRuntime
            .getApplicationProperties(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                    .getResource(propertiesFile))
                .getPath());
        log.info("Reading Properties from resource folder..");
      } else {
        applicationPropertiesMap = KinesisAnalyticsRuntime.getApplicationProperties();
        log.info("Reading Properties from KDA..");
      }
    } catch (IOException e) {
      log.error("{} error_message=PropertiesFile (not found/reading) exception: {}",
              ERROR_MESSAGE_TYPE, e.getMessage());
    }
    return applicationPropertiesMap;
  }

  public static Properties getKafkaSaslProperties(StreamExecutionEnvironment sEnv) {
    Properties saslProperties = new Properties();
    if (sEnv instanceof LocalStreamEnvironment) {
      return saslProperties;
    }
    saslProperties.put("security.protocol", "SASL_SSL");
    saslProperties.put("sasl.mechanism", "AWS_MSK_IAM");
    saslProperties.put("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
    saslProperties.put("sasl.client.callback.handler.class",
        "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
    return saslProperties;
  }

  public static String anonymizeString(String input, int startIndex) {
    if (input != null) {
      StringBuilder anonymized = new StringBuilder(input);
      for (int i = startIndex; i < input.length(); i++) {
        anonymized.setCharAt(i, '*');
      }
      return anonymized.toString();
    } else {
      return null;
    }
  }

  public static String anonymizePIIValueInString(String input) {
    if (input == null) {
      return null;
    }
    
    String vuidKey = "\"vuid\":";
    String vpidKey = "\"vp_id\":";
    final int START_INDEX = 3; // Changed from 8 to 3 to match test expectations
    final int START_AFTER_OPEN_QUOTES = 1;
    
    List<String> piiList = new ArrayList<>();
    piiList.add(vuidKey);
    piiList.add(vpidKey);
    
    String result = input;
    
    for (String pii : piiList) {
      int piiIndex = result.indexOf(pii);
      if (piiIndex != -1) {
        int valueStartIndex = piiIndex + pii.length() + START_AFTER_OPEN_QUOTES; // Start after the opening quote
        int valueEndIndex = result.indexOf("\"", valueStartIndex); // Find the closing quote
        if (valueEndIndex != -1) {
          String originalPii = result.substring(valueStartIndex, valueEndIndex);
          String anonymizedPii = anonymizeString(originalPii, START_INDEX);
          result = result.substring(0, valueStartIndex) + anonymizedPii + result.substring(valueEndIndex);
        }
      }
    }
    
    return result;
  }
  
  public static JSONObject removeEmptyValues(JSONObject jsonObject) {
    JSONObject copy = new JSONObject(jsonObject); // Create a copy for iteration
    for (String key : copy.keySet()) {
      Object value = copy.get(key);
      if (value == null || value.toString().trim().isEmpty()) {
        jsonObject.remove(key);
      }
    }
    return jsonObject;
  }
  
  public static Map<String, String> getTableProperties(Map<String, Properties> propertiesMap) {
    Properties icebergTableProperties = propertiesMap.get("iceberg.table.properties");
    Map<String, String> tableProperties = new HashMap<>();
    for (String key : icebergTableProperties.stringPropertyNames()) {
      tableProperties.put(key, icebergTableProperties.getProperty(key));
    }
    return tableProperties;
  }
}
