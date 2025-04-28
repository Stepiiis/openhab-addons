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

    public OnOffType determineDesiredState(SurplusOutputParameters channelParameters, InputItemsState state,
            double availableSurplusW, OnOffType currentState, Instant lastActivation, Instant lastDeactivation,
            Instant now) {

        boolean hasSurplus;

        long switchingPower = channelParameters.loadPower();

        if (OnOffType.OFF.equals(currentState)) {
            hasSurplus = availableSurplusW >= switchingPower;
        } else {
            if (availableSurplusW < 0) {
                hasSurplus = false;
            } else {
                hasSurplus = availableSurplusW >= switchingPower;
            }
        }

        LOGGER.debug("There {} enough surplus for channel. availableSurplus={}, switchingPower={}",
                hasSurplus ? "is" : "is not", availableSurplusW, switchingPower);

        boolean isPriceOk = isPriceAcceptable(channelParameters, state);
        LOGGER.debug("Price {} acceptable for channel. electricityPrice={}, maxElectricityPrice={}",
                isPriceOk ? "is" : "is not", state.electricityPrice(), channelParameters.maxElectricityPrice());

        OnOffType desiredState = (hasSurplus && isPriceOk) ? OnOffType.ON : OnOffType.OFF;

        return applyCooldownAndRuntimeConstraints(channelParameters, currentState, desiredState, lastActivation,
                lastDeactivation, now);
    }

    private OnOffType applyCooldownAndRuntimeConstraints(SurplusOutputParameters config, OnOffType currentState,
            OnOffType desiredState, Instant lastActivation, Instant lastDeactivation, Instant now) {
        OnOffType result = desiredState;

        if (config.minCooldownMinutes() != 0 && currentState == OnOffType.OFF && desiredState == OnOffType.ON) {
            // for some reason, isAfter works for exact matches but isBefore does not
            boolean canBeSwitchedOn = !lastDeactivation
                    .isAfter(now.minus(config.minCooldownMinutes(), ChronoUnit.MINUTES));
            LOGGER.trace("Checking for minimal cooldown constraint = {}", canBeSwitchedOn);
            result = canBeSwitchedOn ? OnOffType.ON : OnOffType.OFF;
        }

        if (config.minRuntimeMinutes() != 0 && currentState == OnOffType.ON && desiredState == OnOffType.OFF) {
            boolean canBeSwitchedOff = !lastActivation
                    .isAfter(now.minus(config.minRuntimeMinutes(), ChronoUnit.MINUTES));
            LOGGER.trace("Checking for minimal runtime constraint = {}", canBeSwitchedOff);
            result = canBeSwitchedOff ? OnOffType.OFF : OnOffType.ON;
        }

        LOGGER.debug(
                "Resulting state based on cooldown and runtime constraint is {}. minCooldownMinutes={}, minRuntimeMinutes={}, now={}",
                result, config.minCooldownMinutes(), config.minRuntimeMinutes(), now);

        return result;
    }

    private boolean isPriceAcceptable(SurplusOutputParameters config, InputItemsState state) {
        Double maxPrice = config.maxElectricityPrice();

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

    public double getAvailableSurplusWattage(InputItemsState state, EnergyManagerConfiguration config,
            int currentlySwitchedOnWattage) {
        if (state.productionPower().doubleValue() <= 0) {
            return 0;
        }

        // All grid feed in is considered surplus energy, however any grid consumption should be avoided if possible
        int availableSurplusW = state.gridPower().intValue();

        // storagePower is negative - Using energy from the battery which should be avoided if not necessary
        // storagePower is positive + Any power going to ESS is a surplus because the minimal SOC of ESS has been
        // reached
        availableSurplusW += state.storagePower().intValue();

        // ignore operating power draws from ESS or grid if there are any
        if (availableSurplusW < 0 && -availableSurplusW <= config.toleratedPowerDraw().doubleValue()) {
            availableSurplusW = 0;
        }

        // any currently switched on appliances could be from lower priorities, therefore we count them as available
        // surplus (if they are not actually turned on, the surplus will not be there and the loads get turned off)
        // todo test
        availableSurplusW += currentlySwitchedOnWattage;

        if (config.enableInverterLimitingHeuristic()) {
            availableSurplusW += getPotentialSurplusDueToFullESSandInverterLimitation(availableSurplusW, state, config,
                    currentlySwitchedOnWattage);
        }

        // if there is a surplus, some of it should be available to other needs according to user
        if (availableSurplusW > 0) {
            availableSurplusW -= config.minAvailableSurplusEnergy().intValue();
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
    private int getPotentialSurplusDueToFullESSandInverterLimitation(int calculatedSurplus, InputItemsState state,
            EnergyManagerConfiguration config, int currentlySwitchedOnPower) {
        if (state.storageSoc().doubleValue() >= state.maxStorageSoc().doubleValue()
                // if the calculated surplus is less than the switched on power, there really is no surplus and no
                // heuristic can be applied
                // if there is some surplus going to the ESS, or to the grid, we allow it, but signaled loads have
                // higher priority here
                && calculatedSurplus >= currentlySwitchedOnPower
                && state.productionPower().longValue() < config.peakProductionPower().longValue()) {
            return config.peakProductionPower().intValue() - state.productionPower().intValue();
        }
        return 0;
    }
}
