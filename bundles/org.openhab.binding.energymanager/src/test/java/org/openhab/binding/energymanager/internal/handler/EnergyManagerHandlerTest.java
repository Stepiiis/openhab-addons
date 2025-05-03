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
import org.openhab.binding.energymanager.internal.logic.EnergyBalancingEngine;
import org.openhab.binding.energymanager.internal.logic.SurplusDecisionEngine;
import org.openhab.binding.energymanager.internal.state.EnergyManagerInputStateHolder;
import org.openhab.binding.energymanager.internal.state.EnergyManagerOutputStateHolder;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Type;

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

    @Spy
    EnergyManagerInputStateHolder inputStateHolder;

    @Spy
    EnergyManagerOutputStateHolder outputStateHolder;

    @Mock
    EnergyManagerEventSubscriber eventSubscriber;

    @Spy
    SurplusDecisionEngine surplusDecisionEngine;

    @Mock
    EnergyBalancingEngine energyBalancingEngine;

    @Mock
    ConfigUtilService configUtilService;

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
            PROD_PWR, GRID_POWER, ELECTRICITY_PRICE, MIN_STORAGE_SOC, MAX_STORAGE_SOC, STORAGE_SOC, STORAGE_POWER,
            new BigDecimal(0), new BigDecimal(0), true, true, new BigDecimal(50));

    @BeforeEach
    void init() {
        reset(managerHandler);
        assertFalse(managerHandler.isEvaluationJobRunning());
    }

    @AfterEach
    void dispose() {
        assertFalse(managerHandler.isEvaluationJobRunning());
    }

    @Test
    void testReinitialization() {
        // setup
        doReturn(config).when(managerHandler).loadAndValidateConfig();
        doReturn(thing).when(managerHandler).getThing();

        // invoke
        managerHandler.reinitialize();

        // verify
        verify(inputStateHolder, times(1)).clear();
        verify(outputStateHolder, times(1)).clear();
        verify(eventSubscriber, times(1)).unregisterEventsFor(any());
        assertTrue(managerHandler.isEvaluationJobRunning());
        verify(managerHandler, times(1)).startEvaluationJob(eq(config));
        // verify(managerHandler, times(1)).updateAllOutputChannels(eq(OnOffType.OFF), eq(true));
        verify(managerHandler, times(1)).registerEvents(eq(config));

        // cleanup
        managerHandler.stopEvaluationJob();
    }

    @Test
    void testRegisterEventsStandard() {
        // setup
        Map<String, ThingParameterItemName> itemMapping = new HashMap<>();
        itemMapping.put(MIN_STORAGE_SOC, ThingParameterItemName.MIN_STORAGE_SOC);
        itemMapping.put(MAX_STORAGE_SOC, ThingParameterItemName.MAX_STORAGE_SOC);
        itemMapping.put(PROD_PWR, ThingParameterItemName.PRODUCTION_POWER);
        itemMapping.put(GRID_POWER, ThingParameterItemName.GRID_POWER);
        itemMapping.put(STORAGE_SOC, ThingParameterItemName.STORAGE_SOC);
        itemMapping.put(STORAGE_POWER, ThingParameterItemName.STORAGE_POWER);
        itemMapping.put(ELECTRICITY_PRICE, ThingParameterItemName.ELECTRICITY_PRICE);

        // invoke
        managerHandler.registerEvents(config);

        // verify
        verify(eventSubscriber).registerEventsFor(any(), eq(itemMapping), any());
    }

    @Test
    void testRegisterEventsWithConstants() {
        // setup
        Map<String, ThingParameterItemName> itemMapping = new HashMap<>();
        itemMapping.put(PROD_PWR, ThingParameterItemName.PRODUCTION_POWER);
        itemMapping.put(GRID_POWER, ThingParameterItemName.GRID_POWER);
        itemMapping.put(STORAGE_SOC, ThingParameterItemName.STORAGE_SOC);
        itemMapping.put(STORAGE_POWER, ThingParameterItemName.STORAGE_POWER);
        itemMapping.put(ELECTRICITY_PRICE, ThingParameterItemName.ELECTRICITY_PRICE);
        var newConfig = new EnergyManagerConfiguration(config.refreshInterval(), config.peakProductionPower(),
                config.productionPower(), config.gridPower(), config.electricityPrice(), "30", "30",
                config.storageSoc(), config.storagePower(), config.minAvailableSurplusEnergy(), config.initialDelay(),
                config.toggleOnNegativePrice(), config.enableInverterLimitingHeuristic(), config.toleratedPowerDraw());

        // invoke
        managerHandler.registerEvents(newConfig);

        // verify
        verify(eventSubscriber).registerEventsFor(any(), eq(itemMapping), any());
    }

    @Test
    void testHandleItemStateUpdate() {
        // setup
        ItemStateEvent itemEvent = ItemEventFactory.createStateEvent("storagePower", OnOffType.ON,
                this.getClass().getSimpleName());
        ThingParameterItemName item = ThingParameterItemName.STORAGE_POWER;

        // invoke
        managerHandler.handleItemStateUpdate(item, itemEvent);

        // verify
        verify(inputStateHolder, times(1)).saveState(eq(item.getChannelId()), eq(itemEvent.getItemState()));
        Type state = inputStateHolder.getState(item.getChannelId());
        assertEquals(OnOffType.ON, state);
    }

    @Test
    void testHandleConfigUpdate() {
        // setup
        doReturn(config).when(managerHandler).loadAndValidateConfig();
        doReturn(thing).when(managerHandler).getThing();
        Map<String, Object> configurationParameters = new HashMap<>();

        // invoke
        managerHandler.handleConfigurationUpdate(configurationParameters);

        // verify
        verify(inputStateHolder, atLeastOnce()).clear();
        verify(outputStateHolder, atLeastOnce()).clear();

        // cleanup
        managerHandler.stopEvaluationJob();
    }

    @Test
    void testConfigValidation() {
        Map<String, Object> propertiesMap = new HashMap<>();
        propertiesMap.put("toggleOnNegativePrice", true);
        propertiesMap.put("gridPower", "gridPower"); // gridPower is required
        propertiesMap.put("storagePower", null);
        propertiesMap.put("electricityPrice", "electricityPrice");
        propertiesMap.put("minAvailableSurplusEnergy", new BigDecimal(1));
        propertiesMap.put("refreshInterval", new BigDecimal(30));
        propertiesMap.put("storageSoc", null);
        propertiesMap.put("minStorageSoc", "I SHOULD NOT BE FILLED"); // validated later
        propertiesMap.put("maxStorageSoc", null);
        propertiesMap.put("productionPower", "prodPower");
        propertiesMap.put("peakProductionPower", new BigDecimal(60));
        propertiesMap.put("initialDelay", new BigDecimal(60));
        propertiesMap.put("enableInverterLimitingHeuristic", true);
        propertiesMap.put("toleratedPowerDraw", new BigDecimal(60));

        EnergyManagerConfiguration config = new EnergyManagerConfiguration(propertiesMap);

        doReturn(config).when(configUtilService).getTypedConfig(any());

        EnergyManagerConfiguration result = managerHandler.loadAndValidateConfig();

        assertNull(result);
    }
}
