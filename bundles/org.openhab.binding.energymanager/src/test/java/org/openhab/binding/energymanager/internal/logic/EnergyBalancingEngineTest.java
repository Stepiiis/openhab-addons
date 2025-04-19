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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.model.InputItemsState;
import org.openhab.binding.energymanager.internal.model.SurplusOutputParameters;
import org.openhab.binding.energymanager.internal.state.EnergyManagerStateHolder;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;

/**
 * The {@link EnergyBalancingEngineTest} tests various sequences of evaluation cycles
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class EnergyBalancingEngineTest {

    @Spy
    @InjectMocks
    EnergyBalancingEngine energyBalancingEngine;

    @Mock
    ConfigUtilService configUtilService;

    @Spy
    SurplusDecisionEngine surplusDecisionEngine;

    @Mock
    EnergyManagerStateHolder stateHolder;

    @Mock
    BiConsumer<ChannelUID, OnOffType> updateStateFunction;

    @Mock
    Thing thing;

    private final String channeluidprefix = "energymanager:signalizator:test:";

    private final int MAX_PRODUCTION_POWER = 10_000;
    private final String MIN_STORAGE_SOC = "min_storage_soc";
    private final String MAX_STORAGE_SOC = "max_storage_soc";
    private final String PROD_PWR = "prod_pwr";
    private final String GRID_POWER = "grid_pwr";
    private final String STORAGE_SOC = "storage_soc";
    private final String STORAGE_POWER = "storage_pwr";
    private final String ELECTRICITY_PRICE = "electricity_price";

    EnergyManagerConfiguration config = new EnergyManagerConfiguration(new BigDecimal(30),
            new BigDecimal(MAX_PRODUCTION_POWER), MIN_STORAGE_SOC, MAX_STORAGE_SOC, PROD_PWR, GRID_POWER, STORAGE_SOC,
            STORAGE_POWER, ELECTRICITY_PRICE, new BigDecimal(0), new BigDecimal(0), true, true);

    @Test
    void testEvaluateEnergy() {
        // setup
        var stateBuilder = InputItemsState.builder().productionPower(new DecimalType(1000))
                .gridPower(new DecimalType(0)).storageSoc(new DecimalType(50)).storagePower(new DecimalType(-1000))
                .minStorageSoc(new DecimalType(50)).maxStorageSoc(new DecimalType(95))
                .electricityPrice(new DecimalType(0));
        doReturn(stateBuilder.build()).when(energyBalancingEngine).buildEnergyState(any());
        var outputChannels = new ArrayList<>(List.of(
                Map.entry(new ChannelUID(channeluidprefix + "prio1"),
                        SurplusOutputParameters.builder().loadPower(1000).switchingPower(300).priority(1).build()),
                Map.entry(new ChannelUID(channeluidprefix + "prio2"),
                        SurplusOutputParameters.builder().loadPower(2000).switchingPower(1000).priority(2).build())));
        doNothing().when(updateStateFunction).accept(any(), any());

        // invoke
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify only highest priority has been turned on
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));

        // setup subsequent call
        doReturn(stateBuilder.storagePower(new DecimalType(-2000)).build()).when(energyBalancingEngine)
                .buildEnergyState(any());
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio1")));

        // invoke
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify both loads are turned on
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));

        // setup subsequent call
        doReturn(stateBuilder.storagePower(new DecimalType(0)).build()).when(energyBalancingEngine)
                .buildEnergyState(any());
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio1")));
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio2")));

        // invoke
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify no update has happened now
        verify(updateStateFunction, times(3)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.OFF));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));

        // setup subsequent call
        doReturn(stateBuilder.gridPower(new DecimalType(1000)).storagePower(new DecimalType(0)).build())
                .when(energyBalancingEngine).buildEnergyState(any());
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio2")));

        // invoke
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify prio2 has been shed
        verify(updateStateFunction, times(3)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));

        // setup subsequent call
        doReturn(stateBuilder.gridPower(new DecimalType(0)).build()).when(energyBalancingEngine)
                .buildEnergyState(any());
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio1")));
        doReturn(OnOffType.OFF).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio2")));

        // invoke
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify prio2 has been shed and does not get turned on
        verify(updateStateFunction, times(4)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));

        // setup subsequent call
        doReturn(stateBuilder.gridPower(new DecimalType(700)).build()).when(energyBalancingEngine)
                .buildEnergyState(any());
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio1")));
        doReturn(OnOffType.OFF).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio2")));

        // invokes
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify prio1 has been shed as well and previously turned off prio2 is untouched
        verify(updateStateFunction, times(4)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.OFF));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));

        // setup subsequent call
        doReturn(stateBuilder.gridPower(new DecimalType(0)).build()).when(energyBalancingEngine)
                .buildEnergyState(any());
        doReturn(OnOffType.OFF).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio2")));
        doReturn(OnOffType.OFF).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio1")));

        // invokes
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify neither is turned on afterward
        verify(updateStateFunction, times(4)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.OFF));
        verify(updateStateFunction, times(2)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, times(3)).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));
    }

    @Test
    void testAvailablePowerHeuristicDueToInverterLimiting() {
        // setup
        var stateBuilder = InputItemsState.builder().productionPower(new DecimalType(3000))
                .gridPower(new DecimalType(0)).storageSoc(new DecimalType(95)).storagePower(new DecimalType(0))
                .minStorageSoc(new DecimalType(50)).maxStorageSoc(new DecimalType(95))
                .electricityPrice(new DecimalType(0));
        doReturn(stateBuilder.build()).when(energyBalancingEngine).buildEnergyState(any());
        var outputChannels = new ArrayList<>(List.of(
                Map.entry(new ChannelUID(channeluidprefix + "prio1"),
                        SurplusOutputParameters.builder().loadPower(5000).switchingPower(3000).priority(1).build()),
                Map.entry(new ChannelUID(channeluidprefix + "prio2"),
                        SurplusOutputParameters.builder().loadPower(2000).switchingPower(1000).priority(2).build())));
        doNothing().when(updateStateFunction).accept(any(), any());

        // invoke
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify that prio1 is turned on because the available power has been calculated based on maximum power
        // available
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.OFF));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));

        // setup subsequent call
        doReturn(stateBuilder.productionPower(new DecimalType(5000)).storagePower(new DecimalType(2000)).build())
                .when(energyBalancingEngine).buildEnergyState(any());
        doReturn(OnOffType.OFF).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio2")));
        doReturn(OnOffType.ON).when(stateHolder).getState(eq(new ChannelUID(channeluidprefix + "prio1")));

        // invokes
        energyBalancingEngine.evaluateEnergyBalance(config, thing, outputChannels, (__) -> true, updateStateFunction);

        // verify it is turned off in the next call due to overestimation and the draw from ESS equals its deactivation
        // power
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.ON));
        verify(updateStateFunction, times(1)).accept(eq(new ChannelUID(channeluidprefix + "prio1")), eq(OnOffType.OFF));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.ON));
        verify(updateStateFunction, never()).accept(eq(new ChannelUID(channeluidprefix + "prio2")), eq(OnOffType.OFF));
    }
}
