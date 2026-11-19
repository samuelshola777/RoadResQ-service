package com.roadresq.roadresq.rescueRequest.order;

import com.roadresq.roadresq.rescueRequest.RescueLocation;

public record TowingOrder(String capacityId, RescueLocation destination) {}