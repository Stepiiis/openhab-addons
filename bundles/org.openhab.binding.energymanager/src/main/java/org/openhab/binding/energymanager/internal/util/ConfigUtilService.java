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

import static org.openhab.binding.energymanager.internal.enums.ThingParameterItemName.MIN_STORAGE_SOC;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.SurplusOutputParametersEnum;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerStateHolder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ConfigUtilService} provides utility methods for configuration related matters.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public class ConfigUtilService {

    private final EnergyManagerStateHolder stateHolder;

    public ConfigUtilService(EnergyManagerStateHolder stateHolder) {
        this.stateHolder = stateHolder;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUtilService.class);

    public @Nullable SurplusOutputParameters parseSurplusOutputParameters(Configuration configuration) {
        var builder = SurplusOutputParameters.builder();
        Map<String, Method> builderMethods = getBuilderMethods(builder.getClass());

        for (SurplusOutputParametersEnum property : SurplusOutputParametersEnum.values()) {
            String propertyName = property.getPropertyName();
            Object value = configuration.getProperties().get(propertyName);
            if (value == null) {
                if (!property.isRequired()) {
                    continue;
                }
                LOGGER.error("Missing required property '{}' in channel config", propertyName);
                return null;
            }
            Method method = builderMethods.get(propertyName);
            if (method != null) {
                try {
                    Object parsedValue = parseValue(method.getParameterTypes()[0], value);
                    if (parsedValue == null) {
                        LOGGER.error("Failed to parse parameter {} with value {}.", property, value);
                        return null;
                    }
                    method.invoke(builder, parsedValue);
                } catch (Exception e) {
                    LOGGER.error("Error invoking builder for {}: {}", propertyName, e.getMessage());
                    return null;
                }
            } else {
                LOGGER.error("Builder method for property '{}' not found", propertyName);
                return null;
            }
        }

        return builder.build();
    }

    public @Nullable EnergyManagerConfiguration getTypedConfig(Map<String, Object> properties) {
        Field[] fields = EnergyManagerConfiguration.class.getDeclaredFields();
        List<Object> values = new ArrayList<>();

        for (var field : fields) {
            Object prop = properties.get(field.getName());
            if (prop != null) {
                values.add(prop);
            } else {
                LOGGER.error("Failed to resolve field {} from the thing properties.", field.getName());
                return null;
            }
        }

        Constructor<?> constructor = EnergyManagerConfiguration.class.getConstructors()[0];
        try {
            return (EnergyManagerConfiguration) constructor.newInstance(values.toArray());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            LOGGER.error("Failed to create an instance of EnergyManagerConfiguration due to incorrect config.");
        }
        return null;
    }

    private static @Nullable Object parseValue(Class<?> type, Object value) {
        if (type == Integer.class || type == int.class) {
            return Integer.valueOf(value.toString());
        } else if (type == Double.class || type == double.class) {
            return Double.valueOf(value.toString());
        } else if (type == String.class) {
            return value.toString();
        }
        return null;
    }

    private static Map<String, Method> getBuilderMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods()).map(it -> Map.entry(it.getName(), it))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Nullable
    public DecimalType getStateInDecimal(ThingParameterItemName item) {
        Type state = stateHolder.getState(item.getChannelId());

        if (state == null) {
            return null;
        } else if (state instanceof QuantityType<?> quantityType) {
            // Assumes base unit (W, %, or unitless for price)
            return new DecimalType(quantityType.toBigDecimal());
        } else if (state instanceof PercentType percentType) {
            return new DecimalType(percentType.toBigDecimal());
        } else if (state instanceof DecimalType decimalType) {
            return decimalType;
        } else if (state instanceof OnOffType ignored) {
            LOGGER.error("Cannot convert OnOffType state directly to Decimal for item {}", item);
            return null;
        } else {
            // Try parsing from string representation as a fallback
            try {
                return DecimalType.valueOf(state.toString());
            } catch (NumberFormatException e) {
                LOGGER.warn("Cannot parse state '{}' from item {} to DecimalType", state, item);
                return null;
            }
        }
    }

    public DecimalType getMinSocOrDefault(EnergyManagerConfiguration config) {
        return getDecimalFromStringOrItemStateOrDefaultValue(config.minStorageSoc(),
                EnergyManagerBindingConstants.DEFAULT_MIN_STORAGE_SOC);
    }

    public DecimalType getMaxSocOrDefault(EnergyManagerConfiguration config) {
        return getDecimalFromStringOrItemStateOrDefaultValue(config.maxStorageSoc(),
                EnergyManagerBindingConstants.DEFAULT_MAX_STORAGE_SOC);
    }

    private DecimalType getDecimalFromStringOrItemStateOrDefaultValue(String configValue, DecimalType defaultValue) {
        try {
            Integer fromConfig = Integer.valueOf(configValue);
            return new DecimalType(fromConfig);
        } catch (NumberFormatException e) {
            // do nothing
        }

        DecimalType fromChannel = this.getStateInDecimal(MIN_STORAGE_SOC);
        LOGGER.debug("Converted value from config is {}", fromChannel != null ? fromChannel : defaultValue);
        if (fromChannel != null) {
            return fromChannel;
        }

        return defaultValue;
    }
}
