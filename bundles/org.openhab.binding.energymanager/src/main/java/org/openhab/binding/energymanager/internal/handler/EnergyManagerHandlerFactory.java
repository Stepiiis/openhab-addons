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

import static org.openhab.binding.energymanager.internal.EnergyManagerBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.energymanager.internal.logic.SurplusDecisionEngine;
import org.openhab.binding.energymanager.internal.util.ConfigUtilService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link EnergyManagerHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.energymanager", service = ThingHandlerFactory.class)
public class EnergyManagerHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_MANAGER);

    private final EnergyManagerEventSubscriber eventsSubscriber;
    private final SurplusDecisionEngine surplusDecisionEngine;
    private final ConfigUtilService configUtilService;

    @Activate
    public EnergyManagerHandlerFactory(@Reference EnergyManagerEventSubscriber eventsSubscriber,
            @Reference SurplusDecisionEngine surplusDecisionEngine, @Reference ConfigUtilService configutilService) {
        this.eventsSubscriber = eventsSubscriber;
        this.surplusDecisionEngine = surplusDecisionEngine;
        this.configUtilService = configutilService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_MANAGER.equals(thingTypeUID)) {
            return new EnergyManagerHandler(thing, eventsSubscriber, surplusDecisionEngine, configUtilService);
        }

        return null;
    }
}
