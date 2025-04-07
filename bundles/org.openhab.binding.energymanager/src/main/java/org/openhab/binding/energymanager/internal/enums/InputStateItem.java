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
 * The {@link InputStateItem} enum represents the input channels of the binding.
 * important! Enums should always reflect the specification in thing-types.xml
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public enum InputStateItem {
    PRODUCTION_POWER("productionPower"),
    GRID_POWER("gridPower"),
    MIN_STORAGE_SOC("minStorageSoc"),
    MAX_STORAGE_SOC("maxStorageSoc"),
    STORAGE_SOC("storageSoc"),
    STORAGE_POWER("storagePower"),
    ELECTRICITY_PRICE("electricityPrice"),;

    private final String channelId;

    InputStateItem(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelId() {
        return channelId;
    }
}
