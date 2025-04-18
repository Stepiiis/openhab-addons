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
package org.openhab.binding.energymanager.internal.logic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.core.library.types.OnOffType;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SurplusDecisionEngine} handles decisions about desired state of given channel.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.energymanager", service = SurplusDecisionEngine.class)
public class SurplusDecisionEngine {
    private final Logger LOGGER = LoggerFactory.getLogger(SurplusDecisionEngine.class);

    public OnOffType determineDesiredState(EnergyManagerConfiguration config, SurplusOutputParameters channelParameters,
            InputItemsState state, double availableSurplusW, OnOffType currentState, Instant lastActivation,
            Instant lastDeactivation, Instant now) {
        boolean hasSurplus = availableSurplusW >= (channelParameters.loadPowerWatt()
                + config.minSurplusThresholdWatt().longValue());
        LOGGER.debug(
                "There {} enough surplus for channel. availableSurplus={}, channelLoadPower+minSurplusThresholdWatt={}",
                hasSurplus ? "is" : "is not", availableSurplusW,
                channelParameters.loadPowerWatt() + config.minSurplusThresholdWatt().longValue());

        boolean isPriceOk = isPriceAcceptable(channelParameters, state);
        LOGGER.debug("Price {} acceptable for channel. electricityPrice={}, maxElectricityPrice={}",
                hasSurplus ? "is" : "is not", state.electricityPrice(), channelParameters.maxElectricityPrice());

        OnOffType desiredState = (hasSurplus && isPriceOk) ? OnOffType.ON : OnOffType.OFF;

        return applyCooldownAndRuntimeConstraints(channelParameters, currentState, desiredState, lastActivation,
                lastDeactivation, now);
    }

    private OnOffType applyCooldownAndRuntimeConstraints(SurplusOutputParameters config, OnOffType currentState,
            OnOffType desiredState, Instant lastActivation, Instant lastDeactivation, Instant now) {
        OnOffType result = desiredState;

        if (config.minCooldownMinutes() != null && currentState == OnOffType.OFF && desiredState == OnOffType.ON
                && lastDeactivation.isAfter(now.minus(config.minCooldownMinutes(), ChronoUnit.MINUTES))) {
            result = OnOffType.OFF;
        }

        if (config.minRuntimeMinutes() != null && currentState == OnOffType.ON && desiredState == OnOffType.OFF
                && lastActivation.isAfter(now.minus(config.minRuntimeMinutes(), ChronoUnit.MINUTES))) {
            result = OnOffType.ON;
        }

        LOGGER.debug(
                "Resulting state based on cooldown and runtime constraint is {}. minCooldownMinutes={}, minRuntimeMinutes={}, now={}",
                result, config.minCooldownMinutes(), config.minRuntimeMinutes(), now);

        return result;
    }

    private boolean isPriceAcceptable(SurplusOutputParameters config, InputItemsState state) {
        Integer maxPrice = config.maxElectricityPrice();

        if (maxPrice == null) {
            // No price constraint set, always acceptable
            return true;
        }

        if (state.electricityPrice() == null) {
            LOGGER.error("Input channel with electricity price contains null value. Cannot evaluate based on price.");
            // Return true as a fallback
            return true;
        }
        return state.electricityPrice().doubleValue() <= maxPrice;
    }
}
