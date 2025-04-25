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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.handler.util.ConsumerWithMetadata;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventFilter;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.events.*;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnergyManagerEventSubscriber} handles the changes in items of all EnergyManager thing instances
 * and calls their appropriate callback functions in separate threads.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
@Component(service = { EnergyManagerEventSubscriber.class, EventSubscriber.class })
public class EnergyManagerEventSubscriber implements EventSubscriber {
    private final Logger LOGGER = LoggerFactory.getLogger(EnergyManagerEventSubscriber.class);
    private final String SIMPLE_NAME = EnergyManagerEventSubscriber.class.getSimpleName();

    private final Set<String> managerSubscribedEventTypes;
    private final Map<String, Set<ConsumerWithMetadata>> eventConsumers;
    private final ExecutorService executorService;

    @Activate
    public EnergyManagerEventSubscriber() {
        super();
        eventConsumers = new ConcurrentHashMap<>();
        managerSubscribedEventTypes = Set.of(ItemStateEvent.TYPE, ItemCommandEvent.TYPE, ItemTimeSeriesEvent.TYPE,
                ItemStateChangedEvent.TYPE, GroupStateUpdatedEvent.TYPE);
        Runtime.Version version = Runtime.version();
        if (version.feature() >= 21) {
            // using virtual threads if available because of their lightweight nature
            executorService = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            executorService = Executors.newCachedThreadPool();
        }
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return this.managerSubscribedEventTypes;
    }

    @Override
    public @Nullable EventFilter getEventFilter() {
        return event -> {
            if (event instanceof ItemStateEvent)
                return true;
            else if (event instanceof ItemStateUpdatedEvent)
                return true;
            else
                return false;
        };
    }

    @Override
    public void receive(Event event) {
        if (event instanceof ItemStateEvent itemStateEvent) {
            this.receiveUpdate(itemStateEvent);
        } else if (event instanceof ItemStateUpdatedEvent stateUpdatedEvent) {
            this.receiveUpdate(ItemEventFactory.createStateEvent(stateUpdatedEvent.getItemName(),
                    stateUpdatedEvent.getItemState(), SIMPLE_NAME));
        }
    }

    /**
     * Executes consumers of relevant incoming items in background threads so that we don't block the openHAB event bus.
     * Any exceptions are handled gracefully with no propagation to the calling thread
     */
    private void receiveUpdate(ItemStateEvent stateEvent) {
        Optional.ofNullable(eventConsumers.get(stateEvent.getItemName()))
                .ifPresent(consumers -> consumers.forEach((consumer) -> dispatchForProcessing(consumer, stateEvent)));
    }

    private void dispatchForProcessing(ConsumerWithMetadata metadata, ItemStateEvent stateEvent) {
        try {
            executorService.submit(() -> {
                try {
                    metadata.consumer().accept(metadata.thingParameterItemName(), stateEvent);
                } catch (Exception ex) {
                    LOGGER.error("Failed execution of value update consumer of {} for item {} with error: [{}]",
                            metadata.thingUID(), metadata.thingParameterItemName(), ex.toString());
                }
            });
        } catch (RejectedExecutionException rejectionException) {
            LOGGER.error("Handler already disposed, cannot submit task.");
        } catch (Exception ex) {
            LOGGER.error(
                    "Failed to submit task for processing of value update consumer of {} for item {} with error: [{}]",
                    metadata.thingUID(), metadata.thingParameterItemName(), ex.toString());
        }
    }

    public synchronized void registerEventsFor(ThingUID thingUID, Map<String, ThingParameterItemName> itemMapping,
            BiConsumer<ThingParameterItemName, ItemStateEvent> consumer) {
        itemMapping.keySet().forEach(itemName -> {
            eventConsumers.computeIfAbsent(itemName, k -> ConcurrentHashMap.newKeySet())
                    .add(new ConsumerWithMetadata(thingUID, itemMapping.get(itemName), consumer));
        });
        LOGGER.trace("Monitored items: {}", eventConsumers.keySet());
    }

    public synchronized void unregisterEventsFor(ThingUID thingUID) {
        // assuming there will not be many instances, therefore linear complexity is sufficient
        eventConsumers.forEach((key, value) -> value.removeIf(v -> v.thingUID().equals(thingUID)));
    }

    @Deactivate
    public void deactivate() {
        executorService.shutdown();
        LOGGER.info("Event subscriber disposed");
    }
}
