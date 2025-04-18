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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.energymanager.internal.EnergyManagerConfiguration;
import org.openhab.binding.energymanager.internal.enums.ThingParameterItemName;
import org.openhab.binding.energymanager.internal.state.EnergyManagerStateHolder;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Thing;

/**
 * The {@link EnergyManagerHandlerTest} tests the proper functioning of the EnergyManager handler.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class EnergyManagerHandlerTest {

    @Spy
    @InjectMocks
    EnergyManagerHandler managerHandler;

    @Mock
    EnergyManagerStateHolder stateHolder;

    @Mock
    EnergyManagerEventSubscriber eventSubscriber;

    @Mock
    Thing thing;

    private final String MIN_STORAGE_SOC = "min_storage_soc";
    private final String MAX_STORAGE_SOC = "max_storage_soc";
    private final String PROD_PWR = "prod_pwr";
    private final String GRID_POWER = "grid_pwr";
    private final String STORAGE_SOC = "storage_soc";
    private final String STORAGE_POWER = "storage_pwr";
    private final String ELECTRICITY_PRICE = "electricity_price";

    EnergyManagerConfiguration config = new EnergyManagerConfiguration(new BigDecimal(30), new BigDecimal(10_000),
            MIN_STORAGE_SOC, MAX_STORAGE_SOC, PROD_PWR, GRID_POWER, STORAGE_SOC, STORAGE_POWER, ELECTRICITY_PRICE,
            new BigDecimal(0), new BigDecimal(0), true);

    @BeforeEach()
    void init() {
        reset(managerHandler);
        assertFalse(managerHandler.isEvaluationJobRunning());
    }

    @AfterEach()
    void dispose() {
        managerHandler.dispose();
        assertFalse(managerHandler.isEvaluationJobRunning());
    }

    @Test
    void testReinitialization() {
        doAnswer(invocation -> config).when(managerHandler).loadAndValidateConfig();
        managerHandler.reinitialize();

        verify(eventSubscriber, times(1)).unregisterEventsFor(any());
        verify(managerHandler, times(1)).updateAllOutputChannels(eq(OnOffType.OFF), eq(true));
        assertTrue(managerHandler.isEvaluationJobRunning());
        verify(managerHandler, times(1)).registerEvents(eq(config));
    }

    @Test
    void testRegisterEventsStandard() {
        Map<String, ThingParameterItemName> itemMapping = new HashMap<>();
        itemMapping.put(MIN_STORAGE_SOC, ThingParameterItemName.MIN_STORAGE_SOC);
        itemMapping.put(MAX_STORAGE_SOC, ThingParameterItemName.MAX_STORAGE_SOC);
        itemMapping.put(PROD_PWR, ThingParameterItemName.PRODUCTION_POWER);
        itemMapping.put(GRID_POWER, ThingParameterItemName.GRID_POWER);
        itemMapping.put(STORAGE_SOC, ThingParameterItemName.STORAGE_SOC);
        itemMapping.put(STORAGE_POWER, ThingParameterItemName.STORAGE_POWER);
        itemMapping.put(ELECTRICITY_PRICE, ThingParameterItemName.ELECTRICITY_PRICE);
        managerHandler.registerEvents(config);
        verify(eventSubscriber).registerEventsFor(any(), eq(itemMapping), any());
    }

    @Test
    void testRegisterEventsWithConstants() {
        Map<String, ThingParameterItemName> itemMapping = new HashMap<>();
        itemMapping.put(PROD_PWR, ThingParameterItemName.PRODUCTION_POWER);
        itemMapping.put(GRID_POWER, ThingParameterItemName.GRID_POWER);
        itemMapping.put(STORAGE_SOC, ThingParameterItemName.STORAGE_SOC);
        itemMapping.put(STORAGE_POWER, ThingParameterItemName.STORAGE_POWER);
        itemMapping.put(ELECTRICITY_PRICE, ThingParameterItemName.ELECTRICITY_PRICE);
        var newConfig = new EnergyManagerConfiguration(config.refreshInterval(), config.maxProductionPower(), "30",
                "30", config.productionPower(), config.gridPower(), config.storageSoc(), config.storagePower(),
                config.electricityPrice(), config.minSurplusThresholdWatt(), config.initialDelay(),
                config.toggleOnNegativePrice());
        managerHandler.registerEvents(newConfig);
        verify(eventSubscriber).registerEventsFor(any(), eq(itemMapping), any());
    }
}
