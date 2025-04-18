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
import org.openhab.core.library.types.DecimalType;

/**
 * The {@link InputItemsState} record represents the state of the .
 * important! Field names have to be the same as channel IDs inside thing-types.xml and InputChannelEnum
 *
 * @author <Štěpán Beran> - Initial contribution
 */
@NonNullByDefault
public record InputItemsState(DecimalType productionPower, DecimalType gridPower, DecimalType storageSoc,
        DecimalType storagePower, DecimalType minStorageSoc, DecimalType maxStorageSoc,
        @Nullable DecimalType electricityPrice) {

    public static InputItemsStateBuilder builder() {
        return new InputItemsStateBuilder();
    }

    public static class InputItemsStateBuilder {
        private DecimalType productionPower = DecimalType.ZERO;
        private DecimalType gridPower = DecimalType.ZERO;
        private DecimalType storageSoc = DecimalType.ZERO;
        private DecimalType storagePower = DecimalType.ZERO;
        private DecimalType minStorageSoc = DecimalType.ZERO;
        private DecimalType maxStorageSoc = DecimalType.ZERO;
        private @Nullable DecimalType electricityPrice;

        public InputItemsStateBuilder() {
            // Private constructor to prevent direct instantiation
        }

        public InputItemsStateBuilder productionPower(DecimalType productionPower) {
            this.productionPower = productionPower;
            return this;
        }

        public InputItemsStateBuilder gridPower(DecimalType gridPower) {
            this.gridPower = gridPower;
            return this;
        }

        public InputItemsStateBuilder storageSoc(DecimalType storageSoc) {
            this.storageSoc = storageSoc;
            return this;
        }

        public InputItemsStateBuilder storagePower(DecimalType storagePower) {
            this.storagePower = storagePower;
            return this;
        }

        public InputItemsStateBuilder minStorageSoc(DecimalType minStorageSoc) {
            this.minStorageSoc = minStorageSoc;
            return this;
        }

        public InputItemsStateBuilder maxStorageSoc(DecimalType maxStorageSoc) {
            this.maxStorageSoc = maxStorageSoc;
            return this;
        }

        public InputItemsStateBuilder electricityPrice(@Nullable DecimalType electricityPrice) {
            this.electricityPrice = electricityPrice;
            return this;
        }

        /**
         * Validated by default setting to {@link DecimalType#ZERO} singleton and then comparing the references using
         * ==.
         * 
         * @return validated instance of ManagerState
         * @throws IllegalArgumentException if @NotNull values have not been initialized
         */
        public InputItemsState build() {
            if (productionPower == DecimalType.ZERO || gridPower == DecimalType.ZERO || storageSoc == DecimalType.ZERO
                    || storagePower == DecimalType.ZERO || minStorageSoc == DecimalType.ZERO
                    || maxStorageSoc == DecimalType.ZERO) {
                throw new IllegalArgumentException();
            }
            return new InputItemsState(productionPower, gridPower, storageSoc, storagePower, minStorageSoc,
                    maxStorageSoc, electricityPrice);
        }
    }
}
