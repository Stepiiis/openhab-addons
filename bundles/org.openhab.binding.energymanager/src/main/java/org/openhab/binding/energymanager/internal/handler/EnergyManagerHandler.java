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

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.logic.EnergyBalancingEngine;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerInputStateHolder;
import org.openhab.binding.energymanager.internal.state.EnergyManagerOutputStateHolder;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
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

    private final EnergyManagerOutputStateHolder outputStateHolder;
    private final EnergyManagerInputStateHolder inputStateHolder;
    private @Nullable ScheduledFuture<?> evaluationJob;

    private volatile boolean wasNotReady = false;

    private final EnergyManagerEventSubscriber eventSubscriber;
    private final ConfigUtilService configUtilService;
    private final EnergyBalancingEngine energyBalancingEngine;

    EnergyManagerHandler(Thing thing, EnergyManagerEventSubscriber eventSubscriber, ConfigUtilService configUtilService,
            EnergyManagerOutputStateHolder outputStateHolder, EnergyManagerInputStateHolder inputStateHolder,
            EnergyBalancingEngine energyBalancingEngine) {
        super(thing);
        this.LOGGER = LoggerFactory.getLogger(EnergyManagerHandler.class);

        this.inputStateHolder = inputStateHolder;
        this.outputStateHolder = outputStateHolder;
        this.configUtilService = configUtilService;
        this.eventSubscriber = eventSubscriber;
        this.energyBalancingEngine = energyBalancingEngine;
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

        inputStateHolder.clear();
        outputStateHolder.clear();

        stopEvaluationJob();

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
                case STORAGE_SOC -> {
                    if (config.storageSoc() != null) {
                        var storage = (@NonNull String) config.storageSoc();
                        itemMapping.put(storage, inputStateName);
                    }
                }
                case STORAGE_POWER -> {
                    if (config.storagePower() != null) {
                        var storage = (@NonNull String) config.storagePower();
                        itemMapping.put(storage, inputStateName);
                    }
                }
                case ELECTRICITY_PRICE -> itemMapping.put(config.electricityPrice(), inputStateName);
                case MIN_STORAGE_SOC, MAX_STORAGE_SOC -> {
                    var configVal = MIN_STORAGE_SOC.equals(inputStateName) ? config.minStorageSoc()
                            : config.maxStorageSoc();
                    if (configVal != null) {
                        try {
                            Integer.parseInt(configVal);
                        } catch (NumberFormatException e) {
                            itemMapping.put(configVal, inputStateName);
                        }
                    }
                }
            }
        }
        LOGGER.info("Thing {} is registering event subscription for {}", thing.getUID(), itemMapping);
        eventSubscriber.registerEventsFor(thing.getUID(), itemMapping, this::handleItemStateUpdate);
    }

    // only because of stubbed tests
    protected Map<String, Object> getConfigProperties() {
        var config = getConfig();
        if (config != null) {
            return config.getProperties();
        }
        return null;
    }

    protected @Nullable EnergyManagerConfiguration loadAndValidateConfig() {
        EnergyManagerConfiguration config = configUtilService.getTypedConfig(getConfigProperties());

        if (config == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Converted configuration is null " + getConfig());
            return null;
        }

        @Nullable
        String[] array = { config.storageSoc(), config.maxStorageSoc(), config.minStorageSoc(), config.storagePower() };
        boolean allNull = Arrays.stream(array).allMatch(Objects::isNull);
        boolean someNull = Arrays.stream(array).anyMatch(Objects::isNull);
        if (someNull && !allNull) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Either all ESS related parameters have to be filled out or none.");
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
        LOGGER.info("Disposing Energy Manager Handler for {}", getThing().getUID());
        stopEvaluationJob();

        updateAllOutputChannels(OnOffType.OFF, true);

        outputStateHolder.clear();
        inputStateHolder.clear();
        updateStatus(ThingStatus.OFFLINE);
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (getThing().getChannels().stream()
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .noneMatch(it -> channelUID.equals(it.getUID()))) {
            LOGGER.warn("Received command '{}' for unexpected channel '{}'. Only {} types are suppoted", command,
                    channelUID, EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT);
        }
    }

    void handleItemStateUpdate(ThingParameterItemName item, ItemStateEvent itemEvent) {
        // todo test save and retrieve
        LOGGER.trace("Received value '{}' of internal type '{}' from user item '{}'", itemEvent.getItemState(),
                item.getChannelId(), itemEvent.getItemName());
        // todo validate the types of received value
        inputStateHolder.saveState(item.getChannelId(), itemEvent.getItemState());
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        LOGGER.debug("Handling configuration update for {}", getThing().getUID());
        updateStatus(ThingStatus.INITIALIZING);

        super.handleConfigurationUpdate(configurationParameters);

        if (reinitialize()) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration.");
            LOGGER.warn("Energy Balancer Handler for {} disabled due invalid configuration.", getThing().getUID());
        }
    }

    void startEvaluationJob(EnergyManagerConfiguration config) {
        updateAllOutputChannels(OnOffType.OFF, true);
        if (evaluationJob == null || evaluationJob.isCancelled()) {
            if (config.refreshInterval().longValue() <= 0) {
                LOGGER.warn("Cannot start evaluation job: refresh interval is invalid ({}).", config.refreshInterval());
                return;
            }

            this.evaluationJob = scheduler.scheduleWithFixedDelay(
                    () -> energyBalancingEngine.evaluateEnergyBalance(config, getThing(), getOutputChannels(),
                            (inputitemsState) -> verifyStateValueDuringEvaluation(inputitemsState, config),
                            this::updateOutputState),
                    config.initialDelay().longValue(), config.refreshInterval().longValue(), TimeUnit.SECONDS);
            LOGGER.info("Scheduling periodic evaluation job to start in {} seconds for {} running every {} seconds",
                    config.initialDelay(), getThing().getUID(), config.refreshInterval());
        } else {
            LOGGER.debug("Evaluation job already running.");
        }
    }

    void stopEvaluationJob() {
        if (evaluationJob != null && !evaluationJob.isCancelled()) {
            evaluationJob.cancel(true);
            evaluationJob = null;
            LOGGER.info("Stopped energy balance evaluation job for {}", getThing().getUID());
        }
    }

    public boolean isEvaluationJobRunning() {
        return evaluationJob != null && Future.State.RUNNING.equals(evaluationJob.state());
    }

    List<Map.Entry<ChannelUID, SurplusOutputParameters>> getOutputChannels() {
        return getThing().getChannels().stream()
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .flatMap(ch -> {
                    var opt = configUtilService.parseSurplusOutputParameters(ch.getConfiguration());
                    if (opt != null) {
                        LOGGER.trace("Read channel {} with parameters {}", ch.getUID(), opt);
                        return Stream.of(Map.entry(ch.getUID(), opt));
                    } else {
                        LOGGER.error("Could not read channel {} parameters, ignoring it.", ch.getUID());
                        return Stream.empty();
                    }
                }).collect(Collectors.toCollection(ArrayList::new));
    }

    private void updateOutputState(ChannelUID channelUID, OnOffType newState) {
        final boolean forceUpdate;
        if (newState == OnOffType.OFF) {
            forceUpdate = true;
        } else {
            forceUpdate = false;
        }
        updateOutputState(channelUID, newState, forceUpdate);
    }

    private void updateOutputState(ChannelUID channelUID, OnOffType newState, boolean forceUpdate) {
        Type savedState = outputStateHolder.getState(channelUID);
        if (savedState != null && !(savedState instanceof OnOffType)) {
            // This should never happen, throwing exception because if this happens, there must be much bigger problems
            // to solve
            throw new IllegalStateException(
                    "Channel " + channelUID.getId() + " has invalid state type: " + savedState.getClass().getName());
        }
        OnOffType previousState = Objects.requireNonNullElse((OnOffType) savedState, OnOffType.OFF);

        outputStateHolder.saveState(channelUID.getId(), newState, newState != previousState);
        if (forceUpdate || newState != previousState) {
            updateState(channelUID, newState);
            LOGGER.debug("Changed status of channel {} to {}", channelUID.getId(), newState);
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

    private boolean verifyStateValueDuringEvaluation(@Nullable InputItemsState state,
            EnergyManagerConfiguration config) {
        if (state == null) {
            LOGGER.debug("InputItemsState could not be verified: One or more required input state is null.");
            if (!wasNotReady) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NOT_YET_READY,
                        "One or more required input state is still null. It has either not yet received an update or is invalid");
                wasNotReady = true;
            }
            return false;
        }

        if (wasNotReady) {
            updateStatus(ThingStatus.ONLINE);
            wasNotReady = false;
        }

        if (config.toggleOnNegativePrice() && state.electricityPrice() != null
                && state.electricityPrice().doubleValue() < 0) {
            LOGGER.info("Price is negative (={}). Signaling ON to all loads.", state.electricityPrice());
            updateAllOutputChannels(OnOffType.ON, false);
            return false;
        }

        if (state.storageSoc() != null && state.minStorageSoc() != null
                && state.storageSoc().doubleValue() < state.minStorageSoc().doubleValue()) {
            LOGGER.info("Battery SOC {}% is below minimum {}%. Signaling OFF to all loads.",
                    state.storageSoc().doubleValue(), state.minStorageSoc().doubleValue());
            updateAllOutputChannels(OnOffType.OFF, false);
            return false;
        }

        return true;
    }
}
