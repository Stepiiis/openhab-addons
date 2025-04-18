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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.logic.EnergyBalancingEngine;
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
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openhab.binding.energymanager.internal.enums.ThingParameterItemName.*;
import static org.openhab.core.types.RefreshType.REFRESH;

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

    private volatile boolean wasNotReady = false;

    private final EnergyManagerEventSubscriber eventSubscriber;
    private final ConfigUtilService configUtilService;
    private final EnergyBalancingEngine energyBalancingEngine;

    EnergyManagerHandler(Thing thing, EnergyManagerEventSubscriber eventSubscriber,
                         ConfigUtilService configUtilService, EnergyManagerStateHolder stateHolder, EnergyBalancingEngine energyBalancingEngine) {
        super(thing);
        this.LOGGER = LoggerFactory.getLogger(EnergyManagerHandler.class);

        this.stateHolder = stateHolder;
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

            this.evaluationJob = scheduler.scheduleWithFixedDelay(() ->
                            energyBalancingEngine.evaluateEnergyBalance(config,
                                    getThing(),
                                    getOutputChannels(),
                                    (inputitemsState) -> verifyStateValueDuringEvaluation(inputitemsState, config),
                                    this::updateOutputState),
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


    List<Map.Entry<ChannelUID, SurplusOutputParameters>> getOutputChannels() {
        return getThing().getChannels().stream()
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
                }).collect(Collectors.toCollection(ArrayList::new));
    }

    private void updateOutputState(ChannelUID channelUID, OnOffType newState) {
        updateOutputState(channelUID, newState, false);
    }

    private void updateOutputState(ChannelUID channelUID, OnOffType newState, boolean forceUpdate) {
        Type savedState = stateHolder.getState(channelUID);
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

    private boolean verifyStateValueDuringEvaluation(@Nullable InputItemsState state, EnergyManagerConfiguration config) {
        if (state == null) {
            LOGGER.warn("Cannot build ManagerState: One or more required input state is null.");
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

        if (state.storageSoc().doubleValue() < state.minStorageSoc().doubleValue()) {
            LOGGER.info("Battery SOC {}% is below minimum {}%. Signaling OFF to all loads.", state.storageSoc().doubleValue(),
                    state.minStorageSoc().doubleValue());
            updateAllOutputChannels(OnOffType.OFF, false);
            return false;
        }

        return true;
    }

}
