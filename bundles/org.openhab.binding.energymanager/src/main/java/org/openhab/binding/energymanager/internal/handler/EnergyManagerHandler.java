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
package org.openhab.binding.energymanager.internal.handler;

import static org.openhab.binding.energymanager.internal.enums.ThingParameterItemName.*;
import static org.openhab.core.types.RefreshType.REFRESH;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.logic.SurplusDecisionEngine;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerStateHolder;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnergyManagerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public class EnergyManagerHandler extends BaseThingHandler {
    private final Logger LOGGER;

    private final EnergyManagerStateHolder stateHolder;
    private @Nullable ScheduledFuture<?> evaluationJob;

    private volatile boolean notReady = false;

    private final EnergyManagerEventSubscriber eventSubscriber;
    private final SurplusDecisionEngine surplusDecisionEngine;
    private final ConfigUtilService configUtilService;

    public EnergyManagerHandler(Thing thing, EnergyManagerEventSubscriber eventSubscriber,
            SurplusDecisionEngine surplusDecisionEngine, ConfigUtilService configUtilService) {
        super(thing);
        this.LOGGER = LoggerFactory.getLogger(EnergyManagerHandler.class);

        this.stateHolder = new EnergyManagerStateHolder();

        this.eventSubscriber = eventSubscriber;
        this.surplusDecisionEngine = surplusDecisionEngine;
        this.configUtilService = configUtilService;
    }

    @Override
    public void initialize() {
        if (!reinitialize()) {
            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    protected boolean reinitialize() {
        eventSubscriber.unregisterEventsFor(thing.getUID());

        EnergyManagerConfiguration config = loadAndValidateConfig();
        if (config == null) {
            return false;
        }

        startEvaluationJob(config);

        registerEvents(config);

        return true;
    }

    protected void registerEvents(EnergyManagerConfiguration config) {
        Map<String, ThingParameterItemName> itemMapping = new HashMap<>();
        for (ThingParameterItemName inputStateName : values()) {
            switch (inputStateName) {
                case PRODUCTION_POWER -> itemMapping.put(config.productionPower(), inputStateName);
                case GRID_POWER -> itemMapping.put(config.gridPower(), inputStateName);
                case STORAGE_SOC -> itemMapping.put(config.storageSoc(), inputStateName);
                case STORAGE_POWER -> itemMapping.put(config.storagePower(), inputStateName);
                case ELECTRICITY_PRICE -> itemMapping.put(config.electricityPrice(), inputStateName);
                case MIN_STORAGE_SOC, MAX_STORAGE_SOC -> {
                    var configVal = MIN_STORAGE_SOC.equals(inputStateName) ? config.minStorageSoc()
                            : config.maxStorageSoc();
                    try {
                        Integer.parseInt(configVal);
                    } catch (NumberFormatException e) {
                        itemMapping.put(configVal, inputStateName);
                    }
                }
            }
        }
        eventSubscriber.registerEventsFor(thing.getUID(), itemMapping, this::handleItemUpdate);
    }

    protected @Nullable EnergyManagerConfiguration loadAndValidateConfig() {
        EnergyManagerConfiguration config = configUtilService.getTypedConfig(getConfig().getProperties());

        if (config == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Converted configuration is null " + getConfig());
            return null;
        }

        if (config.refreshInterval().longValue() < EnergyManagerBindingConstants.MIN_REFRESH_INTERVAL.longValue()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid refresh interval.");
            return null;
        }
        return config;
    }

    @Override
    public void dispose() {
        LOGGER.debug("Disposing Energy Manager Handler for {}", getThing().getUID());
        stopEvaluationJob();

        stateHolder.clear();
        updateStatus(ThingStatus.OFFLINE);
        super.dispose();
    }

    @Override
    public synchronized void handleCommand(ChannelUID channelUID, Command command) {
        if (getThing().getChannels().stream()
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .noneMatch(it -> channelUID.equals(it.getUID()))) {
            LOGGER.warn("Received command '{}' for unexpected channel '{}'. Only {} types are suppoted", command,
                    channelUID, EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT);
            return;
        }
        if (REFRESH.equals(command)) {
            reinitialize();
        }
    }

    private void handleItemUpdate(ThingParameterItemName item, ItemStateEvent itemEvent) {
        // todo test save and retrieve
        LOGGER.trace("Received value '{}' of internal type '{}' from user item '{}'", itemEvent.getItemState(),
                item.getChannelId(), itemEvent.getItemName());
        // todo validate the types of received value
        stateHolder.saveState(item.getChannelId(), itemEvent.getItemState());
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
        stateHolder.saveState(channelUID.getId(), state);
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        LOGGER.debug("Handling configuration update for {}", getThing().getUID());
        updateStatus(ThingStatus.INITIALIZING);

        stopEvaluationJob();

        super.handleConfigurationUpdate(configurationParameters);

        if (reinitialize()) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration.");
            LOGGER.warn("Energy Balancer Handler for {} disabled due invalid configuration.", getThing().getUID());
        }
    }

    private void startEvaluationJob(EnergyManagerConfiguration config) {
        updateAllOutputChannels(OnOffType.OFF, true);
        if (evaluationJob == null || evaluationJob.isCancelled()) {
            if (config.refreshInterval().longValue() <= 0) {
                LOGGER.warn("Cannot start evaluation job: refresh interval is invalid ({}).", config.refreshInterval());
                return;
            }

            this.evaluationJob = scheduler.scheduleWithFixedDelay(() -> this.evaluateEnergyBalance(config),
                    config.initialDelay().longValue(), config.refreshInterval().longValue(), TimeUnit.SECONDS);
            LOGGER.info("Scheduling periodic evaluation job to start in {} seconds for {} running every {} seconds",
                    config.initialDelay(), getThing().getUID(), config.refreshInterval());
        } else {
            LOGGER.debug("Evaluation job already running.");
        }
    }

    private void stopEvaluationJob() {
        if (evaluationJob != null && !evaluationJob.isCancelled()) {
            evaluationJob.cancel(true);
            evaluationJob = null;
            LOGGER.info("Stopped energy balance evaluation job for {}", getThing().getUID());
        }
    }

    public boolean isEvaluationJobRunning() {
        return evaluationJob != null && Future.State.RUNNING.equals(evaluationJob.state());
    }

    private synchronized void evaluateEnergyBalance(EnergyManagerConfiguration config) {
        LOGGER.debug("Evaluating energy balance for {} (Periodic)", getThing().getUID());

        InputItemsState state = buildManagerState(config);
        if (state == null) {
            LOGGER.warn("Cannot build ManagerState: One or more required input state is null.");
            if (!notReady) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NOT_YET_READY,
                        "One or more required input state is still null. It has either not yet received an update or is invalid");
                notReady = true;
            }
            return;
        }

        if (notReady) {
            updateStatus(ThingStatus.ONLINE);
            notReady = false;
        }

        LOGGER.debug("Inputs: state={}", state);

        double availableSurplusW = getAvailableSurplusWattage(state, config);

        if (config.toggleOnNegativePrice() && state.electricityPrice() != null
                && state.electricityPrice().doubleValue() < 0) {
            updateAllOutputChannels(OnOffType.ON, false);
            return;
        }

        if (state.storageSoc().doubleValue() < state.minStorageSoc().doubleValue()) {
            LOGGER.info("Battery SOC {}% is below minimum {}%. Disabling all loads.", state.storageSoc().doubleValue(),
                    state.minStorageSoc().doubleValue());
            updateAllOutputChannels(OnOffType.OFF, false);
            return;
        }

        // Get channels with typed parameters and sort by priority
        List<Map.Entry<ChannelUID, SurplusOutputParameters>> outputChannels = getThing().getChannels().stream()
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .flatMap(ch -> {
                    var opt = configUtilService.parseSurplusOutputParameters(ch.getConfiguration());
                    if (opt != null) {
                        @NonNull
                        SurplusOutputParameters value = opt;
                        return Stream.of(Map.entry(ch.getUID(), value));
                    } else {
                        LOGGER.error("Could not read channel {} parameters, ignoring it.", ch.getUID());
                        return Stream.empty();
                    }
                }).sorted(Comparator.comparingLong(params -> params.getValue().priority())).toList();

        if (outputChannels.isEmpty()) {
            LOGGER.debug("No output channels of type '{}' configured.",
                    EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT);
            return;
        }

        LOGGER.debug("Evaluating {} output channels by priority...", outputChannels.size());
        Instant now = Instant.now();

        for (Map.Entry<ChannelUID, SurplusOutputParameters> channel : outputChannels) {
            ChannelUID channelUID = channel.getKey();
            SurplusOutputParameters channelParameters = channel.getValue();

            // Default state of channels is OFF
            OnOffType currentState = (OnOffType) Objects.requireNonNullElse(stateHolder.getState(channelUID.getId()),
                    OnOffType.OFF);

            Instant lastActivationTime = stateHolder.getLastActivationTime(channelUID);
            Instant lastDeactivationTime = stateHolder.getLastDeactivationTime(channelUID);
            LOGGER.debug("Last activation of channel {} was {}", channelUID, lastActivationTime);
            LOGGER.debug("Last deactivation of channel {} was {}", channelUID, lastDeactivationTime);
            OnOffType desiredState = surplusDecisionEngine.determineDesiredState(config, channelParameters, state,
                    availableSurplusW, currentState, now, lastActivationTime, lastDeactivationTime);

            updateOutputState(channelUID, desiredState, false);
            if (currentState != desiredState && desiredState == OnOffType.ON) {
                break;
            }
        }
        LOGGER.debug("Finished periodic evaluating of energy surplus.");
    }

    private void updateOutputState(ChannelUID channelUID, OnOffType newState, boolean forceUpdate) {
        Type savedState = stateHolder.getState(channelUID.getId());
        if (savedState != null && !(savedState instanceof OnOffType)) {
            // This should never happen, throwing exception because if this happens, there must be much bigger problems
            // to solve
            throw new IllegalStateException(
                    "Channel " + channelUID.getId() + " has invalid state type: " + savedState.getClass().getName());
        }
        OnOffType previousState = Objects.requireNonNullElse((OnOffType) savedState, OnOffType.OFF);

        if (forceUpdate || newState != previousState) {
            updateState(channelUID, newState);
            LOGGER.debug("Updated status of channel {} to {}", channelUID.getId(), newState);
        } else {
            LOGGER.debug("Channel {} state ({}) unchanged.", channelUID.getId(), newState);
        }
    }

    protected void updateAllOutputChannels(OnOffType state, boolean forceUpdate) {
        getThing().getChannels().stream().filter(ch -> ch.getChannelTypeUID() != null)
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .forEach(ch -> updateOutputState(ch.getUID(), state, forceUpdate));
        LOGGER.debug("Set all output signals to {} (Forced: {})", state, forceUpdate);
    }

    private @Nullable DecimalType getStateInDecimal(ThingParameterItemName item) {
        Type state = stateHolder.getState(item.getChannelId());

        if (state == null) {
            return null;
        } else if (state instanceof QuantityType<?> quantityType) {
            // Assumes base unit (W, %, or unitless for price)
            return new DecimalType(quantityType.toBigDecimal());
        } else if (state instanceof PercentType percentType) {
            return new DecimalType(percentType.toBigDecimal());
        } else if (state instanceof DecimalType decimalType) {
            return decimalType;
        } else if (state instanceof OnOffType ignored) {
            LOGGER.error("Cannot convert OnOffType state directly to Decimal for item {}", item);
            return null;
        } else {
            // Try parsing from string representation as a fallback
            try {
                return DecimalType.valueOf(state.toString());
            } catch (NumberFormatException e) {
                LOGGER.warn("Cannot parse state '{}' from item {} to DecimalType", state, item);
                return null;
            }
        }
    }

    private DecimalType getMinSocOrDefault(EnergyManagerConfiguration config) {
        return getDecimalFromStringOrItemStateOrDefaultValue(config.minStorageSoc(),
                EnergyManagerBindingConstants.DEFAULT_MIN_STORAGE_SOC);
    }

    private DecimalType getMaxSocOrDefault(EnergyManagerConfiguration config) {
        return getDecimalFromStringOrItemStateOrDefaultValue(config.maxStorageSoc(),
                EnergyManagerBindingConstants.DEFAULT_MAX_STORAGE_SOC);
    }

    private DecimalType getDecimalFromStringOrItemStateOrDefaultValue(String configValue, DecimalType defaultValue) {
        try {
            Integer fromConfig = Integer.valueOf(configValue);
            return new DecimalType(fromConfig);
        } catch (NumberFormatException e) {
            // do nothing
        }

        DecimalType fromChannel = this.getStateInDecimal(MIN_STORAGE_SOC);
        LOGGER.debug("Converted value from config is {}", fromChannel != null ? fromChannel : defaultValue);
        if (fromChannel != null) {
            return fromChannel;
        }

        return defaultValue;
    }

    private @Nullable InputItemsState buildManagerState(EnergyManagerConfiguration config) {
        var builder = InputItemsState.builder();

        DecimalType production = this.getStateInDecimal(PRODUCTION_POWER);
        DecimalType gridPower = this.getStateInDecimal(GRID_POWER);
        DecimalType storageSoc = this.getStateInDecimal(STORAGE_SOC);
        DecimalType storagePower = this.getStateInDecimal(STORAGE_POWER);

        if (production == null || gridPower == null || storageSoc == null || storagePower == null) {
            LOGGER.error("production={} gridPower={} storageSoc={} storagePower={}", production, gridPower, storageSoc,
                    storagePower);
            return null;
        }

        // Required values
        builder.productionPower(production).gridPower(gridPower).storageSoc(storageSoc).storagePower(storagePower)
                // optional values with default values or null if not needed
                .electricityPrice(this.getStateInDecimal(ELECTRICITY_PRICE)).minStorageSoc(getMinSocOrDefault(config))
                .maxStorageSoc(getMaxSocOrDefault(config));

        return builder.build();
    }

    private double getAvailableSurplusWattage(InputItemsState state, EnergyManagerConfiguration config) {
        if (state.productionPower().doubleValue() <= 0) {
            return 0;
        }

        // All grid feed in is considered surplus energy
        double availableSurplusW = (state.gridPower().doubleValue() < 0) ? -state.gridPower().doubleValue() : 0;
        // storagePower is negative - Any power going to ESS is a surplus because the minimal SOC is reached
        // storagePower is positive + If we are using energy from the battery it likely means that we are consuming more
        // than is available or feeding in from the battery via separate system.
        availableSurplusW -= state.storagePower().doubleValue();

        // If the ESS is at its maximal user defined capacity and there is some production we are likely in the state of
        // inverter limiting the available power from the electricity production plant.
        // We are optimistically assuming that the maximum energy can be generated. If the energy is not actually
        // available,
        // the output channel will be switched off in eventually in the next cycles, because it will either consume
        // from the grid or the battery which leads to lower available surplus energy in the next cycle.
        if (state.storageSoc().doubleValue() >= state.maxStorageSoc().doubleValue()) {
            if (state.productionPower().longValue() < config.maxProductionPower().longValue()) {
                availableSurplusW += config.maxProductionPower().longValue() - state.productionPower().longValue();
            }
        }

        LOGGER.debug("Calculated Surplus: {}W", availableSurplusW);
        return availableSurplusW;
    }
}
