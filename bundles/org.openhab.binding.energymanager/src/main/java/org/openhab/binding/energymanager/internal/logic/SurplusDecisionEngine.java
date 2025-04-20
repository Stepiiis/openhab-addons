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
import org.openhab.core.library.types.DecimalType;
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

    public OnOffType determineDesiredState(SurplusOutputParameters channelParameters, InputItemsState state,
            double availableSurplusW, OnOffType currentState, Instant lastActivation, Instant lastDeactivation,
            Instant now) {

        // Not really necessary, because negative price is handled elsewhere, but used for completeness
        if (state.electricityPrice() != null && state.electricityPrice().longValue() < 0) {
            return OnOffType.ON;
        }

        boolean hasSurplus;

        long switchingPower = channelParameters.switchingPower() == null ? channelParameters.loadPower()
                : channelParameters.switchingPower();
        if (OnOffType.OFF.equals(currentState)) {
            hasSurplus = availableSurplusW >= switchingPower;
        } else {
            if (channelParameters.loadPower() < switchingPower) {
                LOGGER.error(
                        "Load power of given channel is lower than it's switching power. This is undefined behaviour.");
            }
            if (availableSurplusW < 0 && -availableSurplusW >= channelParameters.loadPower() - switchingPower) {
                hasSurplus = false;
            } else {
                return currentState;
            }
        }

        LOGGER.debug("There {} enough surplus for channel. availableSurplus={}, loadPower={}, switchingPower={}",
                hasSurplus ? "is" : "is not", availableSurplusW, channelParameters.loadPower(),
                channelParameters.switchingPower());

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

    public double getAvailableSurplusWattage(InputItemsState state, EnergyManagerConfiguration config) {
        if (state.productionPower().doubleValue() <= 0) {
            return 0;
        }

        // All grid feed in is considered surplus energy, however any grid consumption should be avoided if possible
        double availableSurplusW = -state.gridPower().doubleValue();

        // storagePower is negative - Any power going to ESS is a surplus because the minimal SOC of ESS is reached now
        // storagePower is positive + If we are using energy from the battery which should be avoided if not necessary
        availableSurplusW -= state.storagePower().doubleValue();

        if (config.enableInverterLimitingHeuristic()) {
            availableSurplusW += getPotentialSurplusDueToFullESSandInverterLimitation(state, config);
        }

        // if there is any surplus, some of it should be available to other needs according to user
        if (availableSurplusW > 0) {
            availableSurplusW -= config.minAvailableSurplusEnergy().longValue();
        }

        LOGGER.debug("Calculated Surplus: {}W", availableSurplusW);
        return availableSurplusW;
    }

    /**
     * If the ESS is at its maximal user defined SOC and there is no consumption from the grid
     * and there still is some production we are likely in the state of inverter limiting the available power.
     * We are optimistically assuming that the maximum energy can be generated. If the energy is not actually
     * available, the output channel will be switched off eventually in the next cycles, because it will either
     * consume from the grid or the battery which leads to lower available surplus energy in the next cycle.
     */
    private double getPotentialSurplusDueToFullESSandInverterLimitation(InputItemsState state,
            EnergyManagerConfiguration config) {
        if (state.storageSoc().doubleValue() >= state.maxStorageSoc().doubleValue()) {
            if (DecimalType.ZERO.equals(state.gridPower())
                    // some power going to the ESS is allowed, but none from it
                    && state.storagePower().longValue() <= 0
                    && state.productionPower().longValue() < config.maxProductionPower().longValue()) {
                return config.maxProductionPower().longValue() - state.productionPower().longValue();
            }
        }
        return 0;
    }
}
