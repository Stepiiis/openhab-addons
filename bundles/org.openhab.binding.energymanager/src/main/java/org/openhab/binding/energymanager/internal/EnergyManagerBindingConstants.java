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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link EnergyManagerBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public class EnergyManagerBindingConstants {

    public static final String BINDING_ID = "energymanager";

    public static final ThingTypeUID THING_TYPE_MANAGER = new ThingTypeUID(BINDING_ID, "manager");

    public static final ChannelTypeUID CHANNEL_TYPE_SURPLUS_OUTPUT = new ChannelTypeUID(BINDING_ID, "surplus-output");

    public static final DecimalType DEFAULT_MIN_STORAGE_SOC = new DecimalType(30);
    public static final DecimalType DEFAULT_MAX_STORAGE_SOC = new DecimalType(100);

    public static final int MIN_REFRESH_INTERVAL = 10;

    // important! Input output channel definitions are in the enums package

    // public static final ChannelGroupUID CH_GROUP_STATE_INPUT = new ChannelGroupUID("energyStateInputs");
    // public static final ChannelGroupUID CH_GROUP_STATE_OUTPUT = new ChannelGroupUID("stateOutputs");
    // public static final ChannelGroupUID CH_GROUP_SETTINGS_INPUTS = new ChannelGroupUID("settingsInputs");

    // public static final ChannelGroupTypeUID CH_GROUP_TYPE_STATE_INPUTS = new
    // ChannelGroupTypeUID(BINDING_ID,"energyStateInputs");
    // public static final ChannelGroupTypeUID CH_GROUP_TYPE_STATE_OUTPUT = new
    // ChannelGroupTypeUID(BINDING_ID,"stateOutputs");
    // public static final ChannelGroupTypeUID CH_GROUP_TYPE_SETTINGS_INPUTS = new
    // ChannelGroupTypeUID(BINDING_ID,"settingsInputs");
}
