package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.vendorOperation.ServiceType;
import java.math.BigDecimal;
import java.util.Set;

// Road ResQ catalogue bands. The vendor must still verify the actual recovery specification.
final class TowingCatalogue {
    private TowingCatalogue() {}

    static Set<CapacityGroup> groupsFor(BigDecimal ratingKg) {
        if (ratingKg == null || ratingKg.compareTo(BigDecimal.valueOf(2000)) < 0) return Set.of();
        if (ratingKg.compareTo(BigDecimal.valueOf(5000)) < 0) return Set.of(CapacityGroup.A);
        if (ratingKg.compareTo(BigDecimal.valueOf(10000)) < 0) return Set.of(CapacityGroup.B);
        return Set.of(CapacityGroup.C);
    }

    static Set<VehicleType> vehicleTypesFor(ServiceType service, Set<CapacityGroup> groups) {
        if (service != ServiceType.TOWING) return CapacityGroup.vehicleTypesFor(groups);
        if (groups.equals(Set.of(CapacityGroup.A))) return Set.of(VehicleType.CAR, VehicleType.SUV, VehicleType.MINIBUS);
        if (groups.equals(Set.of(CapacityGroup.B))) return Set.of(VehicleType.PICKUP, VehicleType.VAN);
        if (groups.equals(Set.of(CapacityGroup.C))) return Set.of(VehicleType.FULL_SIZE_BUS, VehicleType.HEAVY_TRUCK);
        return Set.of();
    }
}