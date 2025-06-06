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
package org.openhab.binding.remoteopenhab.internal.data;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Response to the API GET /rest/items
 * Also payload from ItemAddedEvent / ItemRemovedEvent / ItemUpdatedEvent events received through the SSE connection.
 *
 * @author Laurent Garnier - Initial contribution
 */
@NonNullByDefault
public class RemoteopenhabItem {

    public String name = "";
    public String type = "";
    public String state = "";
    public String groupType = "";
    public @Nullable RemoteopenhabStateDescription stateDescription;
    public @Nullable RemoteopenhabCommandDescription commandDescription;
}
