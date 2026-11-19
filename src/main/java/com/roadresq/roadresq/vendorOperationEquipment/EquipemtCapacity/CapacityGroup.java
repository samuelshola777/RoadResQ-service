package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import java.util.EnumSet;
import java.util.Set;

public enum CapacityGroup {
    A, B, C, D, E, F, G,
    H, I, J, K, L, M, N,
    O, P, Q, R, S, T, U,
    V, W, X, Y, Z;

    public static Set<VehicleType> vehicleTypesFor(Set<CapacityGroup> groups) {
        Set<VehicleType> vehicleTypes = EnumSet.noneOf(VehicleType.class);
        if (groups == null) {
            return vehicleTypes;
        }
        for (CapacityGroup group : groups) {
            switch (group) {
                case A -> vehicleTypes.add(VehicleType.CAR);
                case B -> vehicleTypes.addAll(Set.of(VehicleType.SUV, VehicleType.MINIBUS));
                case C -> vehicleTypes.addAll(Set.of(VehicleType.PICKUP, VehicleType.VAN));
                case D -> vehicleTypes.addAll(Set.of(VehicleType.FULL_SIZE_BUS, VehicleType.HEAVY_TRUCK));
                case E -> vehicleTypes.add(VehicleType.MOTORCYCLE);
                default -> { }
            }
        }
        return vehicleTypes;
    }
}