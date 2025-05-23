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
package org.openhab.binding.saicismart.internal.exceptions;

import org.eclipse.jdt.annotation.NonNullByDefault;

import net.heberling.ismart.asn1.v2_1.MP_DispatcherBody;

/**
 * @author Doug Culnane - Initial contribution
 */
@NonNullByDefault
public class VehicleStatusAPIException extends Exception {

    public VehicleStatusAPIException(MP_DispatcherBody body) {
        super("[" + body.getResult() + "] " + new String(body.getErrorMessage()));
    }
}
