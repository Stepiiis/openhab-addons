package org.openhab.binding.energymanager.internal.logic;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.handler.EnergyManagerHandler;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerStateHolder;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.openhab.binding.energymanager.internal.enums.ThingParameterItemName.*;

public class EnergyBalancingEngine {
    private final Logger LOGGER;

    private final SurplusDecisionEngine surplusDecisionEngine;
    private final EnergyManagerStateHolder stateHolder;
    private final ConfigUtilService configUtilService;

    public EnergyBalancingEngine(SurplusDecisionEngine surplusDecisionEngine,
                                 ConfigUtilService configUtilService,
                                 EnergyManagerStateHolder stateHolder) {
        this.LOGGER = LoggerFactory.getLogger(EnergyManagerHandler.class);
        this.surplusDecisionEngine = surplusDecisionEngine;
        this.configUtilService = configUtilService;
        this.stateHolder = stateHolder;
    }

    /**
     * @param stateVerificationFunction function which verifies if the built state is correct and handles thing status updates
     * @param updateDesiredStateConsumer consumer which handles channel state updates can optimize and not actually send no updates
     */
    public synchronized void evaluateEnergyBalance(EnergyManagerConfiguration config,
                                                   Thing thing,
                                                   List<Map.Entry<ChannelUID, SurplusOutputParameters>> outputChannels,
                                                   Function<InputItemsState, Boolean> stateVerificationFunction,
                                                   BiConsumer<ChannelUID, OnOffType> updateDesiredStateConsumer) {
        LOGGER.debug("Evaluating energy balance for {} (Periodic)", thing.getUID());

        InputItemsState state = buildEnergyState(config);
        if (!stateVerificationFunction.apply(state)) {
            return;
        }

        LOGGER.debug("Inputs: state={}", state);

        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(Objects.<@NonNull InputItemsState>requireNonNull(state), config);

        if (outputChannels.isEmpty()) {
            LOGGER.debug("No output channels of type '{}' configured. Nothing to evaluate. Please configure your channels inside the ",
                    EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT);
            return;
        }

        LOGGER.debug("Evaluating {} output channels by priority...", outputChannels.size());
        Instant now = Instant.now();

        boolean loadShedding;

        if(availableSurplusW < 0) {
            outputChannels.sort(Comparator.comparingLong((Map.Entry<ChannelUID, SurplusOutputParameters> params) -> params.getValue().priority()).reversed());
            loadShedding = true;
        } else {
            outputChannels.sort(Comparator.comparingLong((params) -> params.getValue().priority()));
            loadShedding = false;
        }

        for (Map.Entry<ChannelUID, SurplusOutputParameters> channel : outputChannels) {
            ChannelUID channelUID = channel.getKey();
            SurplusOutputParameters channelParameters = channel.getValue();

            // With default state of channels if it was never saved being OFF
            OnOffType currentState = (OnOffType) Objects.requireNonNullElse(stateHolder.getState(channelUID),
                    OnOffType.OFF);
            if(loadShedding && OnOffType.OFF.equals(currentState)){
                continue;
            }

            Instant lastActivationTime = stateHolder.getLastActivationTime(channelUID);
            Instant lastDeactivationTime = stateHolder.getLastDeactivationTime(channelUID);
            LOGGER.debug("Last activation of channel {} was {}", channelUID, lastActivationTime);
            LOGGER.debug("Last deactivation of channel {} was {}", channelUID, lastDeactivationTime);
            OnOffType desiredState = surplusDecisionEngine.determineDesiredState(channelParameters, state,
                    availableSurplusW, currentState, now, lastActivationTime, lastDeactivationTime);

            updateDesiredStateConsumer.accept(channelUID, desiredState);

            // We always change state of only one channel during one cycle
            if (currentState != desiredState) {
                break;
            }
        }
        LOGGER.debug("Finished periodic evaluating of energy surplus.");
    }


    @Nullable
    InputItemsState buildEnergyState(EnergyManagerConfiguration config) {
        var builder = InputItemsState.builder();

        DecimalType production = configUtilService.getStateInDecimal(PRODUCTION_POWER);
        DecimalType gridPower = configUtilService.getStateInDecimal(GRID_POWER);
        DecimalType storageSoc = configUtilService.getStateInDecimal(STORAGE_SOC);
        DecimalType storagePower = configUtilService.getStateInDecimal(STORAGE_POWER);

        if (production == null || gridPower == null || storageSoc == null || storagePower == null) {
            LOGGER.error("production={} gridPower={} storageSoc={} storagePower={}", production, gridPower, storageSoc,
                    storagePower);
            return null;
        }

        // Required values
        builder.productionPower(production).gridPower(gridPower).storageSoc(storageSoc).storagePower(storagePower)
                // optional values with default values or null if not needed
                .electricityPrice(configUtilService.getStateInDecimal(ELECTRICITY_PRICE))
                .minStorageSoc(configUtilService.getMinSocOrDefault(config))
                .maxStorageSoc(configUtilService.getMaxSocOrDefault(config));

        return builder.build();
    }
}
