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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.enums.InputStateItem;
import org.openhab.binding.energymanager.internal.handler.util.ConsumerWithMetadata;
import org.openhab.core.events.EventFilter;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.events.AbstractItemEventSubscriber;
import org.openhab.core.items.events.ItemStateEvent;
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
@Component(service = {EnergyManagerEventSubscriber.class, EventSubscriber.class})
public class EnergyManagerEventSubscriber extends AbstractItemEventSubscriber {
    private static final Logger log = LoggerFactory.getLogger(EnergyManagerEventSubscriber.class);


    private final Map<String, Set<ConsumerWithMetadata>> eventConsumers;
    private final ExecutorService executorService;
    final Object lock = new Object();


    @Activate
    public EnergyManagerEventSubscriber() {
        eventConsumers = new ConcurrentHashMap<>();
        // using virtual threads because of their lightweight nature
        executorService = Executors.newVirtualThreadPerTaskExecutor();
        log.trace("Event manager Initialized");
    }

    @Override
    public @Nullable EventFilter getEventFilter() {
        return event -> event instanceof ItemStateEvent;
    }

    @Override
    protected void receiveUpdate(ItemStateEvent updateEvent) {
        var consumers = eventConsumers.get(updateEvent.getItemName());
        if (consumers != null) {
            consumers.forEach(consumerMetadata -> {
                // Execution of consumers in background threads so that we don't block the event bus
                executorService.submit(() -> {
                    try {
                        consumerMetadata.consumer().accept(consumerMetadata.inputStateItem(), updateEvent);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
            });
        }
    }

    public void registerEventsFor(ThingUID thingUID, Map<String, InputStateItem> itemMapping,
                                  BiConsumer<InputStateItem, ItemStateEvent> consumer) {
        synchronized (lock) {
            itemMapping.keySet()
                    .forEach(itemName -> eventConsumers.computeIfAbsent(itemName, k -> ConcurrentHashMap.newKeySet())
                            .add(new ConsumerWithMetadata(thingUID, itemMapping.get(itemName), consumer)));
        }
    }

    public void unregisterEventsFor(ThingUID thingUID) {
        synchronized (lock) {
            // assuming there will not be many instances, therefore linear complexity is sufficient
            eventConsumers.forEach((key, value) -> value.removeIf(v -> v.thingUID().equals(thingUID)));
        }
    }

    @Deactivate
    public void deactivate(){
        executorService.shutdown();
        log.trace("Event manager disposed");
    }
}
