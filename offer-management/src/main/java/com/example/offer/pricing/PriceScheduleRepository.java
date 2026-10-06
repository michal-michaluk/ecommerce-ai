package com.example.offer.pricing;

import java.util.Optional;

interface PriceScheduleRepository {

    Optional<PriceSchedule> get(String productId);

    void save(PriceSchedule schedule);
}
