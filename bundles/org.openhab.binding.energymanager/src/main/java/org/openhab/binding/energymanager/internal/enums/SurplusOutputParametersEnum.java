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

/**
 * The {@link SurplusOutputParametersEnum} represents the output channel properties for the surplus-output type of the
 * output channels.
 * important! important Id has to be the same as property names of
 * {@link org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants#CHANNEL_TYPE_SURPLUS_OUTPUT} inside
 * thing-types.xml
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public enum SurplusOutputParametersEnum {
    LOAD_POWER_WATT("loadPowerWatt"),
    PRIORITY("priority"),
    MIN_RUNTIME_MINUTES("minRuntimeMinutes"),
    MIN_COOLDOWN_MINUTES("minCooldownMinutes"),
    ELECTRICITY_PRICE("maxElectricityPrice"),;

    private final String propertyName;

    SurplusOutputParametersEnum(String id) {
        this.propertyName = id;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
