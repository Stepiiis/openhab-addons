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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.State;

/**
 * The {@link EnergyManagerStateHolder} is responsible for state retention.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public abstract class EnergyManagerStateHolder {
    protected final Map<String, State> states = new ConcurrentHashMap<>();

    public void clear() {
        this.states.clear();
    }

    public @Nullable State getState(String channelId) {
        return states.get(channelId);
    }

    public void saveState(String channelId, State state) {
        states.put(channelId, state);
    }
}
