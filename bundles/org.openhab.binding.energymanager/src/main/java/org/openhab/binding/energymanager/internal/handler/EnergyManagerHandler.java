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

import static org.openhab.binding.energymanager.internal.enums.InputStateItem.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.InputStateItem;
import org.openhab.binding.energymanager.internal.enums.SurplusOutputParametersEnum;
import org.openhab.binding.energymanager.internal.model.ManagerState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerStateHolder;
import org.openhab.core.config.core.Configuration;
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

    private final Logger logger = LoggerFactory.getLogger(EnergyManagerHandler.class);

    private final EnergyManagerStateHolder stateHolder = new EnergyManagerStateHolder();

    private final EnergyManagerEventSubscriber eventsHandler;

    private @Nullable ScheduledFuture<?> evaluationJob;

    private volatile boolean notReady = false;

    public EnergyManagerHandler(Thing thing, EnergyManagerEventSubscriber eventsHandler) {
        super(thing);
        this.eventsHandler = eventsHandler;
    }

    @Override
    public void initialize() {
        if (!reinitialize()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration.");
            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    private static void tryParsingAsNumberElseAddToMap(String config, Map<String, InputStateItem> itemMapping,
            InputStateItem inputStateName) {
        try {
            Integer.parseInt(config);
        } catch (NumberFormatException e) {
            itemMapping.put(config, inputStateName);
        }
    }

    private boolean reinitialize() {
        eventsHandler.unregisterEventsFor(thing.getUID());

        EnergyManagerConfiguration config = loadAndValidateConfig();
        if (config == null) {
            return false;
        }

        startEvaluationJob(config);

        registerEvents(config);

        return true;
    }

    private void registerEvents(EnergyManagerConfiguration config) {
        Map<String, InputStateItem> itemMapping = new HashMap<>();
        for (InputStateItem inputStateName : values()) {
            switch (inputStateName) {
                case PRODUCTION_POWER -> itemMapping.put(config.productionPower(), inputStateName);
                case GRID_POWER -> itemMapping.put(config.gridPower(), inputStateName);
                case STORAGE_SOC -> itemMapping.put(config.storageSoc(), inputStateName);
                case STORAGE_POWER -> itemMapping.put(config.storagePower(), inputStateName);
                case ELECTRICITY_PRICE -> itemMapping.put(config.electricityPrice(), inputStateName);
                case MIN_STORAGE_SOC ->
                    tryParsingAsNumberElseAddToMap(config.minStorageSoc(), itemMapping, inputStateName);
                case MAX_STORAGE_SOC ->
                    tryParsingAsNumberElseAddToMap(config.maxStorageSoc(), itemMapping, inputStateName);
            }
        }
        eventsHandler.registerEventsFor(thing.getUID(), itemMapping, this::handleItemUpdate);
    }

    private @Nullable EnergyManagerConfiguration loadAndValidateConfig() {
        EnergyManagerConfiguration config = getConfigAs(EnergyManagerConfiguration.class);

        if (config.refreshInterval() < EnergyManagerBindingConstants.MIN_REFRESH_INTERVAL) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid refresh interval.");
            return null;
        }
        return config;
    }

    @Override
    public void dispose() {
        logger.debug("Disposing Energy Manager Handler for {}", getThing().getUID());
        stopEvaluationJob();

        stateHolder.clear();
        updateStatus(ThingStatus.OFFLINE);
        super.dispose();
    }

    @Override
    public synchronized void handleCommand(ChannelUID channelUID, Command command) {
        logger.warn("Binding does not have any input channels. Received command '{}' for unknown channel '{}'. ",
                command, channelUID);
    }

    private void handleItemUpdate(InputStateItem item, ItemStateEvent itemEvent) {
        logger.trace("Received value '{}' for item '{}'", itemEvent.getPayload(), item);
        stateHolder.saveState(item.toString(), new DecimalType(itemEvent.getPayload()));
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
        stateHolder.saveState(channelUID.getId(), state);
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("Handling configuration update for {}", getThing().getUID());
        updateStatus(ThingStatus.INITIALIZING);

        stopEvaluationJob();

        super.handleConfigurationUpdate(configurationParameters);

        if (reinitialize()) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration.");
            logger.warn("Energy Balancer Handler for {} disabled due invalid configuration.", getThing().getUID());
        }
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
        } else {
            logger.trace("Channel {} state ({}) unchanged.", channelUID.getId(), newState);
        }
    }

    private void updateAllOutputChannels(OnOffType state, boolean forceUpdate) {
        getThing().getChannels().stream().filter(ch -> ch.getChannelTypeUID() != null)
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .forEach(ch -> updateOutputState(ch.getUID(), state, forceUpdate));
        logger.debug("Set all output signals to {} (Forced: {})", state, forceUpdate);
    }

    private @Nullable DecimalType getStateInDecimal(InputStateItem item) {
        Type state = stateHolder.getState(item.getChannelId());
        switch (state) {
            case null -> {
                return null;
            }
            case QuantityType<?> quantityType -> {
                // Assumes base unit (W, %, or unitless for price)
                return new DecimalType(quantityType.toBigDecimal());
            }
            case PercentType percentType -> {
                return new DecimalType(percentType.toBigDecimal());
            }
            case DecimalType decimalType -> {
                return decimalType;
            }
            case OnOffType ignored -> {
                logger.trace("Cannot convert OnOffType state directly to Decimal for item {}", item);
                return null;
            }
            default -> {
                // Try parsing from string representation as a fallback
                try {
                    return DecimalType.valueOf(state.toString());
                } catch (NumberFormatException e) {
                    logger.warn("Cannot parse state '{}' from item {} to DecimalType", state, item);
                    return null;
                }
            }
        }
    }

    private void startEvaluationJob(EnergyManagerConfiguration config) {
        updateAllOutputChannels(OnOffType.OFF, true);
        if (evaluationJob == null || evaluationJob.isCancelled()) {
            if (config.refreshInterval() <= 0) {
                logger.warn("Cannot start evaluation job: refresh interval is invalid ({}).", config.refreshInterval());
                return;
            }

            evaluationJob = scheduler.scheduleWithFixedDelay(() -> this.evaluateEnergyBalance(config),
                    config.initialDelay(), config.refreshInterval(), TimeUnit.SECONDS);
            logger.info("Scheduling periodic evaluation job to start in {} seconds for {} running every {} seconds",
                    config.initialDelay(), getThing().getUID(), config.refreshInterval());
        } else {
            logger.trace("Evaluation job already running.");
        }
    }

    private void stopEvaluationJob() {
        if (evaluationJob != null && !evaluationJob.isCancelled()) {
            evaluationJob.cancel(true);
            evaluationJob = null;
            logger.info("Stopped energy balance evaluation job for {}", getThing().getUID());
        }
    }

    private synchronized void evaluateEnergyBalance(EnergyManagerConfiguration config) {
        logger.debug("Evaluating energy balance for {} (Periodic)", getThing().getUID());

        ManagerState state = buildManagerState(config);
        if (state == null) {
            logger.warn("Cannot build ManagerState: One or more required input state is null.");
            if (!notReady) {
                updateStatus(ThingStatus.INITIALIZING, ThingStatusDetail.NOT_YET_READY,
                        "One or more required input state is still null. It has either not yet received an update or is invalid");
                notReady = true;
            }
            return;
        }

        if (notReady) {
            updateStatus(ThingStatus.ONLINE);
            notReady = false;
        }

        logger.debug("Inputs: state={}", state);

        double availableSurplusW = getAvailableSurplusWattage(state, config);

        if (config.toggleOnNegativePrice() && state.electricityPrice() != null
                && state.electricityPrice().doubleValue() < 0) {
            updateAllOutputChannels(OnOffType.ON, false);
            return;
        }

        if (state.storageSoc().doubleValue() < state.minStorageSoc().doubleValue()) {
            logger.info("Battery SOC {}% is below minimum {}%. Disabling all loads.", state.storageSoc().doubleValue(),
                    state.minStorageSoc().doubleValue());
            updateAllOutputChannels(OnOffType.OFF, false);
            return;
        }

        List<Channel> outputChannels = getThing().getChannels().stream()
                .filter(ch -> EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT.equals(ch.getChannelTypeUID()))
                .sorted(Comparator.comparingInt(ch -> (int) ch.getConfiguration().getProperties()
                        .getOrDefault(SurplusOutputParametersEnum.PRIORITY.getPropertyName(), Integer.MAX_VALUE)))
                .toList();

        if (outputChannels.isEmpty()) {
            logger.debug("No output channels of type '{}' configured.",
                    EnergyManagerBindingConstants.CHANNEL_TYPE_SURPLUS_OUTPUT);
            return;
        }

        logger.debug("Evaluating {} output channels by priority...", outputChannels.size());
        Instant now = Instant.now();

        for (Channel channel : outputChannels) {
            ChannelUID channelUID = channel.getUID();
            Configuration channelConfig = channel.getConfiguration();
            // Default state of channels is OFF
            OnOffType currentState = (OnOffType) Objects.requireNonNullElse(stateHolder.getState(channelUID.getId()),
                    OnOffType.OFF);

            SurplusOutputParameters outConfig = getOutputChannelConfig(channelConfig);

            if (outConfig.loadPowerWatt() <= 0) {
                logger.error("Channel {} has invalid load power (<= 0).", channelUID.getId());
            }

            OnOffType desiredState = getDesiredState(config, availableSurplusW, outConfig, state);
            OnOffType finalState = getFinalState(channel, outConfig, currentState, desiredState, now);

            updateOutputState(channelUID, finalState, false);
            logger.debug("Updated status of channel {} to {}", channelUID.getId(), finalState);
            if (finalState == OnOffType.ON) {
                break;
            }
        }
        logger.debug("Finished periodic evaluating of energy surplus.");
    }

    private DecimalType getMinSocOrDefault(EnergyManagerConfiguration config) {
        try {
            Integer fromConfig = Integer.valueOf(config.minStorageSoc());
            return new DecimalType(fromConfig);
        } catch (NumberFormatException e) {
            // do nothing
        }

        DecimalType fromChannel = this.getStateInDecimal(MIN_STORAGE_SOC);
        if (fromChannel != null) {
            return fromChannel;
        }

        return EnergyManagerBindingConstants.DEFAULT_MIN_STORAGE_SOC;
    }

    private DecimalType getMaxSocOrDefault(EnergyManagerConfiguration config) {
        try {
            Integer fromConfig = Integer.valueOf(config.maxStorageSoc());
            return new DecimalType(fromConfig);
        } catch (NumberFormatException e) {
            // do nothing
        }

        DecimalType fromChannel = this.getStateInDecimal(MAX_STORAGE_SOC);
        if (fromChannel != null) {
            return fromChannel;
        }

        return EnergyManagerBindingConstants.DEFAULT_MAX_STORAGE_SOC;
    }

    private OnOffType getDesiredState(EnergyManagerConfiguration config, double availableSurplusW,
            SurplusOutputParameters outConfig, ManagerState state) {
        boolean hasSufficientSurplus = availableSurplusW >= (outConfig.loadPowerWatt()
                + config.minSurplusThresholdWatt());
        boolean isPriceAcceptable = isIsPriceAcceptable(outConfig, state);

        return (hasSufficientSurplus && isPriceAcceptable) ? OnOffType.ON : OnOffType.OFF;
    }

    private OnOffType getFinalState(Channel channel, SurplusOutputParameters config, OnOffType currentState,
            OnOffType desiredState, Instant now) {
        OnOffType finalState = desiredState;

        if (config.minCooldownMinutes() != null && currentState == OnOffType.OFF && desiredState == OnOffType.ON
                && stateHolder.getLastDeactivationTime(channel.getUID())
                        .isAfter(now.minus(config.minCooldownMinutes(), ChronoUnit.MINUTES))) {
            finalState = OnOffType.OFF;
        }
        if (config.minRuntimeMinutes() != null && currentState == OnOffType.ON && desiredState == OnOffType.OFF
                && stateHolder.getLastActivationTime(channel.getUID())
                        .isBefore(now.minus(config.minRuntimeMinutes(), ChronoUnit.MINUTES))) {
            finalState = OnOffType.ON;
        }
        return finalState;
    }

    private boolean isIsPriceAcceptable(SurplusOutputParameters config, ManagerState state) {
        Integer configPrice = config.electricityPrice();
        DecimalType statePrice = state.electricityPrice();

        if (configPrice == null) {
            return true; // No price constraint, always acceptable
        }

        if (statePrice == null) {
            logger.error("Input channel with electricity price contains null value. Cannot evaluate based on price.");
            // Return true as a fallback to avoid blocking operation
            return true;
        }

        return statePrice.doubleValue() <= configPrice;
    }

    private @Nullable ManagerState buildManagerState(EnergyManagerConfiguration config) {
        var builder = ManagerState.builder();

        DecimalType production = this.getStateInDecimal(PRODUCTION_POWER);
        DecimalType gridPower = this.getStateInDecimal(GRID_POWER);
        DecimalType storageSoc = this.getStateInDecimal(STORAGE_SOC);
        DecimalType storagePower = this.getStateInDecimal(STORAGE_POWER);

        if (production == null || gridPower == null || storageSoc == null || storagePower == null) {
            return null;
        }

        // Required values
        builder.productionPower(production).gridPower(gridPower).storageSoc(storageSoc).storagePower(storagePower)
                // optional values with default values or null if not needed
                .electricityPrice(this.getStateInDecimal(ELECTRICITY_PRICE)).minStorageSoc(getMinSocOrDefault(config))
                .maxStorageSoc(getMaxSocOrDefault(config));

        return builder.build();
    }

    private SurplusOutputParameters getOutputChannelConfig(Configuration configuration) {
        var builder = SurplusOutputParameters.builder();
        Map<String, Method> builderMethods = getBuilderMethods();
        for (SurplusOutputParametersEnum property : SurplusOutputParametersEnum.values()) {
            try {
                Object prop = configuration.getProperties().get(property.name());
                if (prop == null) {
                    logger.error(
                            "Failed reading channel input property {} from configuration. Property not found in thing configuration.",
                            property.name());
                    continue;
                }
                builderMethods.get(property.name()).invoke(builder, prop);
            } catch (Exception e) {
                logger.error("Failed reading channel input property {} from configuration. {}", property.name(),
                        e.getMessage());
            }
        }
        return builder.build();
    }

    private static Map<String, Method> getBuilderMethods() {
        return Arrays.stream(SurplusOutputParameters.builder().getClass().getDeclaredMethods())
                .map(it -> Map.entry(it.getName(), it))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private double getAvailableSurplusWattage(ManagerState state, EnergyManagerConfiguration config) {
        if (state.productionPower().doubleValue() <= 0) {
            return 0;
        }
        Thing thing = getThing();

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
            if (state.productionPower().doubleValue() < config.maxProductionPower()) {
                availableSurplusW += config.maxProductionPower() - state.productionPower().doubleValue();
            }
        }

        logger.debug("Calculated Surplus (Grid Feed-in): {}W", availableSurplusW);
        return availableSurplusW;
    }
}
