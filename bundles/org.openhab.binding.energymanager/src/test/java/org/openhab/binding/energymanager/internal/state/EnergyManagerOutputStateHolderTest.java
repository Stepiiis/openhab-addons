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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.State;

/**
 * @author <Štěpán Beran> - Initial contribution
 */
public class EnergyManagerOutputStateHolderTest {

    private static final ChannelUID TEST_CHANNEL = new ChannelUID("binding:device:thing:channel");
    private final EnergyManagerOutputStateHolder holder = new EnergyManagerOutputStateHolder();

    @Test
    void saveStateOnWithUpdateLasts_UpdatesActivationTime() {
        Instant beforeSave = Instant.now();
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.ON, true);
        Instant afterSave = Instant.now();

        assertStateAndTimes(OnOffType.ON, beforeSave, afterSave, true, false);
    }

    @Test
    void saveStateOffWithUpdateLasts_UpdatesDeactivationTime() {
        Instant beforeSave = Instant.now();
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.OFF, true);
        Instant afterSave = Instant.now();

        assertStateAndTimes(OnOffType.OFF, beforeSave, afterSave, false, true);
    }

    @Test
    void saveStateSameState_DoesNotUpdateTimes() {
        // First save
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.ON, true);
        Instant firstActivation = holder.getLastActivationTime(TEST_CHANNEL);

        // Second save with same state
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.ON, true);

        assertEquals(firstActivation, holder.getLastActivationTime(TEST_CHANNEL));
    }

    @Test
    void saveStateDifferentState_UpdatesTimes() {
        // Initial OFF state
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.OFF, true);
        Instant firstDeactivation = holder.getLastDeactivationTime(TEST_CHANNEL);

        // Change to ON
        Instant beforeSave = Instant.now();
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.ON, true);
        Instant afterSave = Instant.now();

        assertTrue(holder.getLastActivationTime(TEST_CHANNEL).isAfter(beforeSave)
                && holder.getLastActivationTime(TEST_CHANNEL).isBefore(afterSave));
        assertEquals(firstDeactivation, holder.getLastDeactivationTime(TEST_CHANNEL));
    }

    @Test
    void saveNonOnOffState_DoesNotUpdateTimes() {
        holder.saveState(TEST_CHANNEL.getId(), new DecimalType(42), true);

        assertEquals(Instant.EPOCH, holder.getLastActivationTime(TEST_CHANNEL));
        assertEquals(Instant.EPOCH, holder.getLastDeactivationTime(TEST_CHANNEL));
    }

    @Test
    void saveStateWithoutUpdateLasts_DoesNotUpdateTimes() {
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.ON, false);

        assertEquals(Instant.EPOCH, holder.getLastActivationTime(TEST_CHANNEL));
    }

    @Test
    void clear_ResetsAllStateInformation() {
        // Set initial state
        holder.saveState(TEST_CHANNEL.getId(), OnOffType.ON, true);
        holder.clear();

        assertNull(holder.getState(TEST_CHANNEL));
        assertEquals(Instant.EPOCH, holder.getLastActivationTime(TEST_CHANNEL));
        assertEquals(Instant.EPOCH, holder.getLastDeactivationTime(TEST_CHANNEL));
    }

    private void assertStateAndTimes(State expectedState, Instant beforeSave, Instant afterSave,
            boolean expectActivation, boolean expectDeactivation) {
        // Verify state
        assertEquals(expectedState, holder.getState(TEST_CHANNEL));
        // Verify activation time
        if (expectActivation) {
            assertTrue(holder.getLastActivationTime(TEST_CHANNEL).isAfter(beforeSave)
                    && holder.getLastActivationTime(TEST_CHANNEL).isBefore(afterSave));
            assertEquals(Instant.EPOCH, holder.getLastDeactivationTime(TEST_CHANNEL));
        }

        // Verify deactivation time
        if (expectDeactivation) {
            assertTrue(holder.getLastDeactivationTime(TEST_CHANNEL).isAfter(beforeSave)
                    && holder.getLastDeactivationTime(TEST_CHANNEL).isBefore(afterSave));
            assertEquals(Instant.EPOCH, holder.getLastActivationTime(TEST_CHANNEL));
        }
    }
}
