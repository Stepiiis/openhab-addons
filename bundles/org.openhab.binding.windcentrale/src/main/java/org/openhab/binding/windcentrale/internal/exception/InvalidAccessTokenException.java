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
package org.openhab.binding.windcentrale.internal.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The Cognito access token used with the Windcentrale API is invalid and could not be refreshed.
 *
 * @author Wouter Born - Initial contribution
 */
@NonNullByDefault
public class InvalidAccessTokenException extends Exception {

    private static final long serialVersionUID = 9066624337663085233L;

    public InvalidAccessTokenException(Exception cause) {
        super(cause);
    }

    public InvalidAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidAccessTokenException(String message) {
        super(message);
    }
}
