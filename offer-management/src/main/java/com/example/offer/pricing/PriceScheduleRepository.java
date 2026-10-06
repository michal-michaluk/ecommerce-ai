package com.example.offer.pricing;

import java.util.List;
import java.util.Optional;

interface PriceScheduleRepository {

    Optional<PriceSchedule> get(String productId);

    List<PriceSchedule> all();

    void save(PriceSchedule schedule);
}
