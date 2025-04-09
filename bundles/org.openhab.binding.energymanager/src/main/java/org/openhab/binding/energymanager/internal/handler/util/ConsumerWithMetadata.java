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
package org.openhab.binding.energymanager.internal.handler.util;

import java.util.function.BiConsumer;

import org.openhab.binding.energymanager.internal.enums.InputStateItem;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.thing.ThingUID;

/**
 * The {@link ConsumerWithMetadata} is a util class used to represent metadata for a specific thing handler callback.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
public record ConsumerWithMetadata(ThingUID thingUID, InputStateItem inputStateItem,
                                   BiConsumer<InputStateItem, ItemStateEvent> consumer) {
}
