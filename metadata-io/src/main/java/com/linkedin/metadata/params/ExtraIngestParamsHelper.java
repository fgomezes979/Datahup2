package com.linkedin.metadata.params;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ExtraIngestParamsHelper {

    // condUpdate String format: "urn1+AspectName1=createdOn1;urn2+AspectName2=createdOn2;...;urnN+AspectNameN=createdOnN"

    /**
     * createdOn is the UNIX epoch in milliseconds when the Urn+aspectName combination is edited for the last time.
     * For simplicity, we do not support the default If-Unmodified-Since date time format.
     * We only support the UNIX timestamp.
     */
    public static Map<String, Long> extractCondUpdate(String condUpdate) {
        Map<String, Long> createdOnMap = new HashMap<>();
        if (condUpdate != null) {
            Arrays.stream(condUpdate.split(";"))
                    .forEach(item -> {
                        String[] values = item.split("=");
                        if (values.length == 2) {
                            try {
                                createdOnMap.put(values[0], Long.parseLong(values[1]));
                            } catch (Exception e) {
                                log.warn("Invalid condition: " + item);
                            }
                        }
                    });
        }
        return createdOnMap;
    }

    private ExtraIngestParamsHelper() { }
}