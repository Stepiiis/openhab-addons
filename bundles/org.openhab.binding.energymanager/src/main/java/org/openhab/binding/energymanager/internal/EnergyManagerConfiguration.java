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
package org.openhab.binding.energymanager.internal;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link EnergyManagerConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public record EnergyManagerConfiguration(
        // required settings
        BigDecimal refreshInterval, BigDecimal peakProductionPower,
        // required names of associated items
        String productionPower, String gridPower, String electricityPrice,
        // non required settings or item names
        @Nullable String minStorageSoc, @Nullable String maxStorageSoc,
        // non required item names
        @Nullable String storageSoc, @Nullable String storagePower,
        // optional with default values
        BigDecimal minAvailableSurplusEnergy, BigDecimal initialDelay, Boolean toggleOnNegativePrice,
        Boolean enableInverterLimitingHeuristic, BigDecimal toleratedPowerDraw) {
    public EnergyManagerConfiguration(Map<String, Object> pars) {
        this((@NonNull BigDecimal) pars.get("refreshInterval"), (@NonNull BigDecimal) pars.get("peakProductionPower"),
                (@NonNull String) pars.get("productionPower"), (@NonNull String) pars.get("gridPower"),
                (@NonNull String) pars.get("electricityPrice"), (String) pars.get("minStorageSoc"),
                (String) pars.get("maxStorageSoc"), (String) pars.get("storageSoc"), (String) pars.get("storagePower"),
                (@NonNull BigDecimal) pars.get("minAvailableSurplusEnergy"),
                (@NonNull BigDecimal) pars.get("initialDelay"), (@NonNull Boolean) pars.get("toggleOnNegativePrice"),
                (@NonNull Boolean) pars.get("enableInverterLimitingHeuristic"),
                (@NonNull BigDecimal) pars.get("toleratedPowerDraw"));
    }
}
