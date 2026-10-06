package com.example.offer.pricing;

import java.time.LocalDate;

public class InvalidDateRange extends RuntimeException {

    public InvalidDateRange(LocalDate from, LocalDate to) {
        super("validTo " + to + " is before validFrom " + from);
    }
}
