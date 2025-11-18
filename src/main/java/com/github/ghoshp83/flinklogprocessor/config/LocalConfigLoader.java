/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class LocalConfigLoader {
    
    public static Map<String, Properties> loadLocalConfig(String configFile) {
        Map<String, Properties> propertiesMap = new HashMap<>();
        
        try {
            InputStream inputStream = LocalConfigLoader.class.getClassLoader()
                .getResourceAsStream(configFile);
            
            if (inputStream == null) {
                log.error("Config file not found: {}", configFile);
                return propertiesMap;
            }
            
            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONArray jsonArray = JSON.parseArray(jsonContent);
            
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject group = jsonArray.getJSONObject(i);
                String groupId = group.getString("PropertyGroupId");
                JSONObject propertyMap = group.getJSONObject("PropertyMap");
                
                Properties properties = new Properties();
                for (String key : propertyMap.keySet()) {
                    properties.setProperty(key, propertyMap.getString(key));
                }
                
                propertiesMap.put(groupId, properties);
                log.info("Loaded property group: {} with {} properties", groupId, properties.size());
            }
            
        } catch (Exception e) {
            log.error("Error loading local config", e);
        }
        
        return propertiesMap;
    }
}
