package com.roadresq.roadresq.rescueRequest;

import com.roadresq.roadresq.vendorOperation.ServiceType;

public enum RescueJobStage {
    COLLECTING_FUEL,
    EN_ROUTE_TO_CUSTOMER,
    ARRIVED_AT_CUSTOMER,
    JUMP_STARTING,
    TOWING_TO_DESTINATION,
    COMPLETED;

    public static RescueJobStage next(ServiceType type, RescueJobStage current) {
        if (current == null) {
            return type == ServiceType.FUEL_DELIVERY ? COLLECTING_FUEL : EN_ROUTE_TO_CUSTOMER;
        }
        return switch (current) {
            case COLLECTING_FUEL -> EN_ROUTE_TO_CUSTOMER;
            case EN_ROUTE_TO_CUSTOMER -> ARRIVED_AT_CUSTOMER;
            case ARRIVED_AT_CUSTOMER -> switch (type) {
                case FUEL_DELIVERY -> COMPLETED;
                case BATTERY_JUMP_START -> JUMP_STARTING;
                case TOWING -> TOWING_TO_DESTINATION;
            };
            case JUMP_STARTING, TOWING_TO_DESTINATION -> COMPLETED;
            case COMPLETED -> null;
        };
    }
}