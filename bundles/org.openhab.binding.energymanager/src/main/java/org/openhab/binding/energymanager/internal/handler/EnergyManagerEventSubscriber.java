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
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.enums.InputStateItem;
import org.openhab.binding.energymanager.internal.handler.util.ConsumerMetadata;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventFilter;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.events.AbstractItemEventSubscriber;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link EnergyManagerEventSubscriber} handles the changes in items of all EnergyManager thing instances
 * and calls their appropriate callback functions.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
@Component(service = { EnergyManagerEventSubscriber.class, EventSubscriber.class })
public class EnergyManagerEventSubscriber extends AbstractItemEventSubscriber {

    Map<String, Set<ConsumerMetadata>> eventConsumers = new ConcurrentHashMap<>();
    final Object lock = new Object();

    @Override
    protected void receiveUpdate(ItemStateEvent updateEvent) {
        if (eventConsumers.containsKey(updateEvent.getItemName())) {
            eventConsumers.get(updateEvent.getItemName())
                    .forEach(it -> it.consumer().accept(it.inputStateItem(), updateEvent));
        }
    }

    public void registerEventsFor(ThingUID thingUID, Map<String, InputStateItem> itemMapping,
            BiConsumer<InputStateItem, ItemStateEvent> consumer) {
        synchronized (lock) {
            itemMapping.keySet()
                    .forEach(itemName -> eventConsumers.computeIfAbsent(itemName, k -> ConcurrentHashMap.newKeySet())
                            .add(new ConsumerMetadata(thingUID, itemMapping.get(itemName), consumer)));
        }
    }

    public void unregisterEventsFor(ThingUID thingUID) {
        synchronized (lock) {
            // assuming there will not be many instances, therefore linear complexity is sufficient
            eventConsumers.forEach((key, value) -> value.removeIf(v -> v.thingUID().equals(thingUID)));
        }
    }
}
