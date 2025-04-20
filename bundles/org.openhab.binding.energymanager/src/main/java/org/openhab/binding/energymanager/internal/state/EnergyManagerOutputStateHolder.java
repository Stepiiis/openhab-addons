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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnergyManagerOutputStateHolder} is responsible for state retention of output channels.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public class EnergyManagerOutputStateHolder extends EnergyManagerStateHolder {
    protected final Logger LOGGER = LoggerFactory.getLogger(EnergyManagerOutputStateHolder.class);

    protected final Map<String, Instant> lastActivationTime = new ConcurrentHashMap<>();
    protected final Map<String, Instant> lastDeactivationTime = new ConcurrentHashMap<>();

    @Override
    public void clear() {
        super.clear();
        this.lastActivationTime.clear();
        this.lastDeactivationTime.clear();
    }

    public @Nullable State getState(ChannelUID channelUID) {
        return this.states.get(channelUID.getId());
    }

    @Override
    public void saveState(String channelId, State state) {
        saveState(channelId, state, false);
    }

    public void saveState(String channelId, State state, boolean updateLasts) {
        if (updateLasts) {
            updateLasts(channelId, state);
        }
        super.saveState(channelId, state);
    }

    private void updateLasts(String channelId, State state) {
        if (state instanceof OnOffType onOffType) {
            Instant now = Instant.now();
            switch (onOffType) {
                case ON -> lastActivationTime.put(channelId, now);
                case OFF -> lastDeactivationTime.put(channelId, now);
            }
            LOGGER.debug("Updated channel {} at {}", channelId, now);
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
