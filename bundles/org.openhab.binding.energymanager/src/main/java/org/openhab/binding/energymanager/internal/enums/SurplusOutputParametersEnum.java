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
package org.openhab.binding.energymanager.internal.enums;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;

/**
 * The {@link SurplusOutputParametersEnum} represents the output channel properties for the surplus-output type of the
 * output channels.
 *
 * important! propertyName has to be the same as property names of *
 * {@link EnergyManagerBindingConstants#CHANNEL_TYPE_SURPLUS_OUTPUT}
 * inside thing-types.xml
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public enum SurplusOutputParametersEnum {
    PRIORITY("priority", true),
    LOAD_POWER("loadPower", true),
    MIN_RUNTIME_MINUTES("minRuntimeMinutes", false),
    MIN_COOLDOWN_MINUTES("minCooldownMinutes", false),
    ELECTRICITY_PRICE("maxElectricityPrice", false);

    private final String propertyName;
    private final boolean required;

    SurplusOutputParametersEnum(String propertyName, boolean required) {
        this.propertyName = propertyName;
        this.required = required;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public boolean isRequired() {
        return required;
    }
}
