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
package org.openhab.binding.sonnen.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link SonnenConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Christian Feininger - Initial contribution
 */
@NonNullByDefault
public class SonnenConfiguration {

    public String hostIP = "";
    public int refreshInterval = 30;
    public String authToken = "";
    public int chargingPower = -1;
}
