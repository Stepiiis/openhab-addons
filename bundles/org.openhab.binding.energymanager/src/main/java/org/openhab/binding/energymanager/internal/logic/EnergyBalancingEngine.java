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

import static org.openhab.binding.energymanager.internal.enums.ThingParameterItemName.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerOutputStateHolder;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnergyBalancingEngine} handles the evaluation cycle of all output channels.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public class EnergyBalancingEngine {
    private final Logger LOGGER;

    private final SurplusDecisionEngine surplusDecisionEngine;
    private final EnergyManagerOutputStateHolder outputStateHolder;
    private final ConfigUtilService configUtilService;

    public EnergyBalancingEngine(SurplusDecisionEngine surplusDecisionEngine, ConfigUtilService configUtilService,
            EnergyManagerOutputStateHolder outputStateHolder) {
        this.LOGGER = LoggerFactory.getLogger(EnergyBalancingEngine.class);
        this.surplusDecisionEngine = surplusDecisionEngine;
        this.configUtilService = configUtilService;
        this.outputStateHolder = outputStateHolder;
    }

    /**
     * @param stateVerificationFunction function which verifies if the built state is correct and handles thing status
     *            updates
     * @param updateDesiredStateConsumer consumer which handles channel state updates can optimize and not actually send
     *            no updates
     */
    public synchronized void evaluateEnergyBalance(EnergyManagerConfiguration config, Thing thing,
            List<Map.Entry<ChannelUID, SurplusOutputParameters>> outputChannels,
            Function<@Nullable InputItemsState, Boolean> stateVerificationFunction,
            BiConsumer<ChannelUID, OnOffType> updateDesiredStateConsumer) {
        LOGGER.debug("Evaluating energy balance for {} (Periodic)", thing.getUID());

        InputItemsState state = buildEnergyState(config);
        if (!stateVerificationFunction.apply(state)) {
            return;
        }

        LOGGER.trace("Inputs: state={}", state);

        int currentlySwitchedOnWattageFromSurplus = outputChannels.stream()
                .filter(it -> OnOffType.ON.equals(outputStateHolder.getState(it.getKey())))
                .mapToInt(it -> it.getValue().loadPower()).sum();

        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(
                Objects.<@NonNull InputItemsState> requireNonNull(state), config,
                currentlySwitchedOnWattageFromSurplus);

        if (outputChannels.isEmpty()) {
            LOGGER.info(
                    "No output channels of type '{}' configured. Nothing to evaluate. Please configure your channels inside the ",
                    EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT);
            return;
        }

        Instant now = Instant.now();

        boolean loadShedding;

        if (availableSurplusW < 0) {
            outputChannels.sort(Comparator
                    .comparingLong(
                            (Map.Entry<ChannelUID, SurplusOutputParameters> params) -> params.getValue().priority())
                    .reversed());
            loadShedding = true;
        } else {
            outputChannels.sort(Comparator.comparingLong((params) -> params.getValue().priority()));
            loadShedding = false;
        }

        for (Map.Entry<ChannelUID, SurplusOutputParameters> channel : outputChannels) {
            ChannelUID channelUID = channel.getKey();
            SurplusOutputParameters channelParameters = channel.getValue();

            LOGGER.debug("Evaluating channel {}...", channelUID);

            // With default state of channels if it was never saved being OFF
            OnOffType currentState = (OnOffType) Objects.requireNonNullElse(outputStateHolder.getState(channelUID),
                    OnOffType.OFF);
            if (loadShedding && OnOffType.OFF.equals(currentState)) {
                LOGGER.trace("Not evaluating turned off channel {} in load shedding mode.", channelUID);
                updateDesiredStateConsumer.accept(channelUID, OnOffType.OFF);
                continue;
            }

            Instant lastActivationTime = outputStateHolder.getLastActivationTime(channelUID);
            Instant lastDeactivationTime = outputStateHolder.getLastDeactivationTime(channelUID);
            LOGGER.trace("Last activation of channel {} was {}", channelUID, lastActivationTime);
            LOGGER.trace("Last deactivation of channel {} was {}", channelUID, lastDeactivationTime);
            OnOffType desiredState = surplusDecisionEngine.determineDesiredState(channelParameters, state,
                    availableSurplusW, currentState, lastActivationTime, lastDeactivationTime, now);

            updateDesiredStateConsumer.accept(channelUID, desiredState);

            // We always change state of only one channel during one cycle
            if (currentState != desiredState) {
                break;
            }
            if (desiredState == OnOffType.ON) {
                availableSurplusW -= channelParameters.loadPower();
            }
        }
        LOGGER.debug("Finished periodic evaluating of energy surplus.");
    }

    @Nullable
    InputItemsState buildEnergyState(EnergyManagerConfiguration config) {
        var builder = InputItemsState.builder();

        DecimalType production = configUtilService.getInputStateInDecimal(PRODUCTION_POWER);
        DecimalType gridPower = configUtilService.getInputStateInDecimal(GRID_POWER);

        if (production == null || gridPower == null) {
            LOGGER.error("One or more required states is null {}={} {}={}", PRODUCTION_POWER, production, GRID_POWER,
                    gridPower);
            return null;
        }

        // Required values
        builder.productionPower(production).gridPower(gridPower)
                // optional values with default values or null if not needed
                .electricityPrice(configUtilService.getInputStateInDecimal(ELECTRICITY_PRICE))
                .storageSoc(configUtilService.getInputStateInDecimal(STORAGE_SOC))
                .storagePower(configUtilService.getInputStateInDecimal(STORAGE_POWER))
                .minStorageSoc(configUtilService.getMinSocOrDefault(config))
                .maxStorageSoc(configUtilService.getMaxSocOrDefault(config));
        return builder.build();
    }
}
