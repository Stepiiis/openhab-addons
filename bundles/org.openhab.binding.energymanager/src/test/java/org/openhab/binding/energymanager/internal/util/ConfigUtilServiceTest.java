/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.energymanager.internal.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.SurplusOutputParametersEnum;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.core.config.core.Configuration;

/**
 * The {@link ConfigUtilServiceTest} tests prerequisites for the service to function and also the proper functioning of
 * the service itself.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
public class ConfigUtilServiceTest {

    ConfigUtilService configUtilService = new ConfigUtilService();

    // Enum propertyNames match record fields
    @Test
    void eachEnumPropertyMatchesRecordField() {
        for (SurplusOutputParametersEnum enumValue : SurplusOutputParametersEnum.values()) {
            String propertyName = enumValue.getPropertyName();
            try {
                SurplusOutputParameters.class.getDeclaredField(propertyName);
            } catch (NoSuchFieldException e) {
                fail("Enum " + enumValue.name() + " has propertyName " + propertyName
                        + " which does not match any field in SurplusOutputParameters");
            }
        }
    }

    // Enum propertyNames have corresponding builder methods
    @Test
    void eachEnumPropertyHasBuilderMethod() {
        for (SurplusOutputParametersEnum enumValue : SurplusOutputParametersEnum.values()) {
            String propertyName = enumValue.getPropertyName();
            try {
                // Get the record field type
                Field recordField = SurplusOutputParameters.class.getDeclaredField(propertyName);
                Class<?> fieldType = recordField.getType();

                // Adjust for primitive types
                Class<?> paramType = fieldType.isPrimitive() ? fieldType : fieldType;

                // Check builder method exists with the correct parameter type
                Method method = SurplusOutputParameters.SurplusOutputParametersBuilder.class.getMethod(propertyName,
                        paramType);
                assertNotNull(method, "Builder method " + propertyName + " should exist");
            } catch (NoSuchFieldException | NoSuchMethodException e) {
                fail("Builder method " + propertyName + " not found for enum " + enumValue.name());
            }
        }
    }

    // All record fields are covered by an enum
    @Test
    void allRecordFieldsHaveEnumProperty() {
        var recordFields = Arrays.stream(SurplusOutputParameters.class.getDeclaredFields()).map(Field::getName)
                .collect(Collectors.toList());

        for (String fieldName : recordFields) {
            boolean found = Arrays.stream(SurplusOutputParametersEnum.values())
                    .anyMatch(enumValue -> enumValue.getPropertyName().equals(fieldName));
            assertTrue(found, "Record field " + fieldName + " has no corresponding enum property");
        }
    }

    // Parsing with all required properties (current code will fail)
    @Test
    void parseSurplusOutputParameters_ValidConfig_ReturnsParameters() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("loadPowerWatt", 1000);
        properties.put("priority", 1);
        properties.put("minRuntimeMinutes", 30);
        properties.put("minCooldownMinutes", 60);
        properties.put("maxElectricityPrice", 150);

        Configuration config = new Configuration(properties);

        SurplusOutputParameters result = configUtilService.parseSurplusOutputParameters(config);
        assertNotNull(result, "Parsing should succeed with all required properties");

        // Verify values (adjust based on your actual expected values)
        assertEquals(1000, result.loadPowerWatt());
        assertEquals(1, result.priority());
        assertEquals(30, result.minRuntimeMinutes());
        assertEquals(60, result.minCooldownMinutes());
        assertEquals(150, result.maxElectricityPrice());
    }

    @Test
    void testGetTypedConfig() {
        Map<String, Object> propertiesMap = new HashMap<>();
        propertiesMap.put("toggleOnNegativePrice", true);
        propertiesMap.put("gridPower", "gridPower");
        propertiesMap.put("storagePower", "storagePower");
        propertiesMap.put("electricityPrice", "electricityPrice");
        propertiesMap.put("minSurplusThresholdWatt", new BigDecimal(1));
        propertiesMap.put("refreshInterval", new BigDecimal(30));
        propertiesMap.put("storageSoc", "storageSoc");
        propertiesMap.put("minStorageSoc", "50");
        propertiesMap.put("maxStorageSoc", "95");
        propertiesMap.put("productionPower", "prodPower");
        propertiesMap.put("maxProductionPower", new BigDecimal(60));
        propertiesMap.put("initialDelay", new BigDecimal(60));

        EnergyManagerConfiguration result = configUtilService.getTypedConfig(propertiesMap);

        assertNotNull(result);
        assertEquals(true, result.toggleOnNegativePrice());
        assertEquals("gridPower", result.gridPower());
        assertEquals("storagePower", result.storagePower());
        assertEquals("electricityPrice", result.electricityPrice());
        assertEquals(new BigDecimal(1), result.minSurplusThresholdWatt());
        assertEquals(new BigDecimal(30), result.refreshInterval());
        assertEquals("storageSoc", result.storageSoc());
        assertEquals("50", result.minStorageSoc());
        assertEquals("95", result.maxStorageSoc());
        assertEquals("prodPower", result.productionPower());
        assertEquals(new BigDecimal(60), result.maxProductionPower());
        assertEquals(new BigDecimal(60), result.initialDelay());
    }
}
