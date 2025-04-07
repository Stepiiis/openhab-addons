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
package org.openhab.binding.energymanager.internal.state;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.validation.constraints.NotNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnergyManagerStateHolder} is responsible for state retention of all I/O channels.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public class EnergyManagerStateHolder {
    private final Logger logger = LoggerFactory.getLogger(EnergyManagerStateHolder.class);

    private final Map<String, Type> states = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivationTime = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastDeactivationTime = new ConcurrentHashMap<>();

    public void clear() {
        this.states.clear();
        this.lastActivationTime.clear();
        this.lastDeactivationTime.clear();
    }

    public void saveState(String key, @NotNull Type state) {
        updateLasts(key, state);
        states.put(key, state);
    }

    public @Nullable Type getState(String key) {
        return states.get(key);
    }

    private void updateLasts(String channelId, Type state) {
        if (state instanceof OnOffType onOffType) {
            Instant now = Instant.now();
            switch (onOffType) {
                case ON -> lastActivationTime.put(channelId, now);
                case OFF -> lastDeactivationTime.put(channelId, now);
            }
            logger.debug("Updated channel {} at {}", channelId, now);
        }
    }

    /**
     * @return Instant representing value of last activation or Instant.EPOCH if there is no record in memory
     */
    public Instant getLastActivationTime(ChannelUID channel) {
        return lastActivationTime.getOrDefault(channel.getId(), Instant.EPOCH);
    }

    /**
     * @return Instant representing value of last deactivation or Instant.EPOCH if there is no record in memory
     */
    public Instant getLastDeactivationTime(ChannelUID channel) {
        return lastDeactivationTime.getOrDefault(channel.getId(), Instant.EPOCH);
    }
}
