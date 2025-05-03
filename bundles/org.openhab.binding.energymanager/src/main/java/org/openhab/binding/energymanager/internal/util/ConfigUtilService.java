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

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.SurplusOutputParametersEnum;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerInputStateHolder;
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

    private final EnergyManagerInputStateHolder inputStateHolder;

    public ConfigUtilService(EnergyManagerInputStateHolder inputStateHolder) {
        this.inputStateHolder = inputStateHolder;
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
        return new EnergyManagerConfiguration(properties);
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
    public DecimalType getInputStateInDecimal(ThingParameterItemName item) {
        Type state = inputStateHolder.getState(item.getChannelId());

        // not using pattern matching for backwards compatibility with OH < 5.0.0
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

    public @Nullable DecimalType getMinSocOrDefault(EnergyManagerConfiguration config) {
        return getDecimalFromStringOrItemStateOrNull(config.minStorageSoc());
    }

    public @Nullable DecimalType getMaxSocOrDefault(EnergyManagerConfiguration config) {
        return getDecimalFromStringOrItemStateOrNull(config.maxStorageSoc());
    }

    private @Nullable DecimalType getDecimalFromStringOrItemStateOrNull(@Nullable String configValue) {
        if (configValue == null) {
            return null;
        }

        try {
            Integer fromConfig = Integer.valueOf(configValue);
            return new DecimalType(fromConfig);
        } catch (NumberFormatException e) {
            // do nothing
        }

        DecimalType fromChannel = this.getInputStateInDecimal(MIN_STORAGE_SOC);
        LOGGER.debug("Converted value from config is {}", fromChannel);
        return fromChannel;
    }
}
