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
package org.openhab.binding.energymanager.internal.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;

/**
 * The {@link SurplusDecisionEngineTest} tests correct function and edge cases of the SurplusDecisionEngine class
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class SurplusDecisionEngineTest {

    SurplusDecisionEngine surplusDecisionEngine = new SurplusDecisionEngine();

    private InputItemsState buildInputState(double productionPower, double gridPower, double storageSoc,
            double storagePower, Double electricityPrice) {
        InputItemsState.InputItemsStateBuilder builder = InputItemsState.builder()
                .productionPower(new DecimalType(productionPower)).gridPower(new DecimalType(gridPower))
                .storageSoc(new DecimalType(storageSoc)).storagePower(new DecimalType(storagePower));

        if (electricityPrice != null) {
            builder.electricityPrice(new DecimalType(electricityPrice));
        }

        // Defaults for non-critical fields
        builder.minStorageSoc(new DecimalType(0));
        builder.maxStorageSoc(new DecimalType(100));

        return builder.build();
    }

    // Helper method to create SurplusOutputParameters
    private SurplusOutputParameters buildOutputParameters(int loadPower, int switchingPower,
            @Nullable Integer maxElectricityPrice, @Nullable Integer minCooldownMinutes,
            @Nullable Integer minRuntimeMinutes) {
        SurplusOutputParameters.SurplusOutputParametersBuilder builder = SurplusOutputParameters.builder()
                .loadPower(loadPower).switchingPower(switchingPower);

        if (maxElectricityPrice != null)
            builder.maxElectricityPrice(maxElectricityPrice);
        if (minCooldownMinutes != null)
            builder.minCooldownMinutes(minCooldownMinutes);
        if (minRuntimeMinutes != null)
            builder.minRuntimeMinutes(minRuntimeMinutes);

        return builder.build();
    }

    // Helper method to create EnergyManagerConfiguration
    private EnergyManagerConfiguration buildConfig(boolean enableHeuristic, @Nullable Integer maxProductionPower,
            @Nullable Integer minAvailableSurplusEnergy) {
        return new EnergyManagerConfiguration(BigDecimal.valueOf(30), // refreshInterval (30 seconds)
                // if null, it is not needed for the tet
                maxProductionPower != null ? new BigDecimal(maxProductionPower) : new BigDecimal(0), "30", // minStorageSoc
                // (30%)
                "30", // maxStorageSoc (30%)
                "prodPower", // productionPower item name
                "gridPower", // gridPower item name
                "storageSoc", // storageSoc item name
                "storagePower", // storagePower item name
                "electricityPrice", // electricityPrice item name
                minAvailableSurplusEnergy != null ? new BigDecimal(minAvailableSurplusEnergy) : BigDecimal.ZERO, // minAvailableSurplusEnergy
                BigDecimal.valueOf(30), // initialDelay (30 seconds)
                false, // toggleOnNegativePrice (hardcoded for tests)
                enableHeuristic // enableInverterLimitingHeuristic
        );
    }

    @Test
    void testDetermineDesiredState_TurnOff_SwitchingPowerNullSufficientNegativeSurplus() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 0.1);
        SurplusOutputParameters params = SurplusOutputParameters.builder().loadPower(300).maxElectricityPrice(10)
                .build(); // switchingPower is null
        double availableSurplusW = -300;
        OnOffType currentState = OnOffType.ON;
        Instant now = Instant.now();
        Instant lastActivation = now.minus(20, ChronoUnit.MINUTES);
        Instant lastDeactivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.OFF, desiredState);
    }

    @Test
    void testDetermineDesiredState_TurnOn_NegativeElectricityPrice() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, -5.0);
        SurplusOutputParameters params = buildOutputParameters(1000, 1000, 10, null, null);
        double availableSurplusW = 1500;
        OnOffType currentState = OnOffType.OFF;
        Instant now = Instant.now();
        Instant lastDeactivation = now.minusSeconds(60);
        Instant lastActivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.ON, desiredState);
    }

    @Test
    void testDetermineDesiredState_TurnOn_CooldownExactlyMet() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 0.1);
        SurplusOutputParameters params = buildOutputParameters(1000, 1000, 10, 10, null);
        double availableSurplusW = 1500;
        OnOffType currentState = OnOffType.OFF;
        Instant now = Instant.now();
        Instant lastDeactivation = now.minus(10, ChronoUnit.MINUTES);
        Instant lastActivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.ON, desiredState);
    }

    @Test
    void testDetermineDesiredState_TurnOff_RuntimeExactlyMet() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 0.1);
        SurplusOutputParameters params = buildOutputParameters(300, 100, 10, null, 10);
        double availableSurplusW = -500;
        OnOffType currentState = OnOffType.ON;
        Instant now = Instant.now();
        Instant lastActivation = now.minus(10, ChronoUnit.MINUTES);
        Instant lastDeactivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.OFF, desiredState);
    }

    @Test
    void testDetermineDesiredState_StayOff_MinAvailableSurplusExceedsSurplus() {
        // setup
        InputItemsState state = buildInputState(2000, -1000, 50, 0, 0.1);
        double availableSurplusW = -500; // Result from getAvailableSurplusWattage
        SurplusOutputParameters params = buildOutputParameters(1000, 1000, 10, null, null);
        OnOffType currentState = OnOffType.OFF;
        Instant now = Instant.now();
        Instant lastDeactivation = now.minus(60, ChronoUnit.MINUTES);
        Instant lastActivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.OFF, desiredState);
    }

    @Test
    void testDetermineDesiredState_TurnOff_ExactNegativeSurplusThreshold() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 0.1);
        SurplusOutputParameters params = buildOutputParameters(300, 100, 10, null, null);
        double availableSurplusW = -200;
        OnOffType currentState = OnOffType.ON;
        Instant now = Instant.now();
        Instant lastActivation = now.minus(20, ChronoUnit.MINUTES);
        Instant lastDeactivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.OFF, desiredState);
    }

    @Test
    void testDetermineDesiredState_TurnOn_ExactSurplusThreshold() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 0.1);
        SurplusOutputParameters params = buildOutputParameters(1000, 1000, 10, null, null);
        double availableSurplusW = 1000;
        OnOffType currentState = OnOffType.OFF;
        Instant now = Instant.now();
        Instant lastDeactivation = now.minusSeconds(60);
        Instant lastActivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.ON, desiredState);
    }

    @Test
    void testDetermineDesiredState_StayOn_PositiveSurplusWhileOn() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 0.1);
        SurplusOutputParameters params = buildOutputParameters(1000, 1000, 10, null, null);
        double availableSurplusW = 500;
        OnOffType currentState = OnOffType.ON;
        Instant now = Instant.now();
        Instant lastActivation = now.minus(20, ChronoUnit.MINUTES);
        Instant lastDeactivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.ON, desiredState);
    }

    @Test
    void testDetermineDesiredState_StayOn_PriceExceededButDeviceOn() {
        // setup
        InputItemsState state = buildInputState(0, 0, 0, 0, 15.0);
        SurplusOutputParameters params = buildOutputParameters(1000, 1000, 10, null, null);
        double availableSurplusW = 1500;
        OnOffType currentState = OnOffType.ON;
        Instant now = Instant.now();
        Instant lastActivation = now.minus(20, ChronoUnit.MINUTES);
        Instant lastDeactivation = Instant.EPOCH;

        // invoke
        OnOffType desiredState = surplusDecisionEngine.determineDesiredState(params, state, availableSurplusW,
                currentState, lastActivation, lastDeactivation, now);

        // verifiy
        assertEquals(OnOffType.ON, desiredState);
    }

    // --- getAvailableSurplusWattage Tests ---

    @Test
    void testGetAvailableSurplusWattage_HeuristicTriggered_StorageSocEqualsMax() {
        // setup
        InputItemsState state = buildInputState(1500, 0, 95, -10, null);
        state = InputItemsState.builder().from(state).minStorageSoc(new DecimalType(0))
                .maxStorageSoc(new DecimalType(95)).build();
        EnergyManagerConfiguration config = buildConfig(true, 2000, 0);

        // invoke
        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(state, config);

        // verifiy
        assertEquals(510.0, availableSurplusW, 0.001);
    }

    @Test
    void testGetAvailableSurplusWattage_ProductionNegative_ReturnsZero() {
        // setup
        InputItemsState state = buildInputState(-500, 100, 50, 0, null);
        EnergyManagerConfiguration config = buildConfig(false, null, 0);

        // invoke
        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(state, config);

        // verifiy
        assertEquals(0.0, availableSurplusW, 0.001);
    }

    @Test
    void testGetAvailableSurplusWattage_HeuristicTriggered_StoragePowerZero() {
        // setup
        InputItemsState state = buildInputState(1500, 0, 100, 0, null);
        state = InputItemsState.builder().from(state).minStorageSoc(new DecimalType(0))
                .maxStorageSoc(new DecimalType(95)).build();
        EnergyManagerConfiguration config = buildConfig(true, 2000, 0);

        // invoke
        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(state, config);

        // verifiy
        assertEquals(500.0, availableSurplusW, 0.001);
    }

    @Test
    void testGetAvailableSurplusWattage_HeuristicNotTriggered_GridNonZeroStoragePowerZero() {
        // setup
        InputItemsState state = buildInputState(1500, 100, 100, 0, null);
        state = InputItemsState.builder().from(state).minStorageSoc(new DecimalType(0))
                .maxStorageSoc(new DecimalType(95)).build();
        EnergyManagerConfiguration config = buildConfig(true, 2000, 0);

        // invoke
        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(state, config);

        // verifiy
        assertEquals(-100.0, availableSurplusW, 0.001);
    }

    @Test
    void testGetAvailableSurplusWattage_WithMinAvailableSurplus_PositiveSurplusButMinLarger() {
        // setup
        InputItemsState state = buildInputState(2000, -1000, 50, 0, null);
        EnergyManagerConfiguration config = buildConfig(false, null, 1500);

        // invoke
        double availableSurplusW = surplusDecisionEngine.getAvailableSurplusWattage(state, config);

        // verifiy
        assertEquals(-500.0, availableSurplusW, 0.001);
    }
}
