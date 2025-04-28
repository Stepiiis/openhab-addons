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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link EnergyManagerConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public record EnergyManagerConfiguration(
        // required settings
        BigDecimal refreshInterval, BigDecimal peakProductionPower,
        // required settings or item names
        String minStorageSoc, String maxStorageSoc,
        // required names of associated items
        String productionPower, String gridPower, String storageSoc, String storagePower, String electricityPrice,
        // optional with default values
        BigDecimal minAvailableSurplusEnergy, BigDecimal initialDelay, Boolean toggleOnNegativePrice,
        Boolean enableInverterLimitingHeuristic, BigDecimal toleratedPowerDraw) {
}
