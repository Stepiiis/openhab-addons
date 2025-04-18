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
package org.openhab.binding.energymanager.internal.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link SurplusOutputParameters} record represents user specified parameters of one surplusOutput channel.
 * important - Field names have to be the same as output channel property names inside thing-types.xml
 *
 * @author <Štěpán Beran> - Initial contribution
 *
 */
@NonNullByDefault
public record SurplusOutputParameters(int loadPowerWatt, int priority, @Nullable Integer minRuntimeMinutes,
        @Nullable Integer minCooldownMinutes, @Nullable Integer maxElectricityPrice) {

    public static SurplusOutputParametersBuilder builder() {
        return new SurplusOutputParametersBuilder();
    }

    public static class SurplusOutputParametersBuilder {
        private int loadPowerWatt;
        private int priority;
        private @Nullable Integer minRuntimeMinutes;
        private @Nullable Integer minCooldownMinutes;
        private @Nullable Integer maxElectricityPrice;

        private SurplusOutputParametersBuilder() {
            // Private constructor to prevent direct instantiation
        }

        public static SurplusOutputParametersBuilder create() {
            return new SurplusOutputParametersBuilder();
        }

        public SurplusOutputParametersBuilder loadPowerWatt(int loadPowerWatt) {
            this.loadPowerWatt = loadPowerWatt;
            return this;
        }

        public SurplusOutputParametersBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public SurplusOutputParametersBuilder minRuntimeMinutes(@Nullable Integer minRuntimeMinutes) {
            this.minRuntimeMinutes = minRuntimeMinutes;
            return this;
        }

        public SurplusOutputParametersBuilder minCooldownMinutes(@Nullable Integer minCooldownMinutes) {
            this.minCooldownMinutes = minCooldownMinutes;
            return this;
        }

        public SurplusOutputParametersBuilder maxElectricityPrice(@Nullable Integer maxElectricityPrice) {
            this.maxElectricityPrice = maxElectricityPrice;
            return this;
        }

        public SurplusOutputParameters build() {
            return new SurplusOutputParameters(loadPowerWatt, priority, minRuntimeMinutes, minCooldownMinutes,
                    maxElectricityPrice);
        }
    }
}
