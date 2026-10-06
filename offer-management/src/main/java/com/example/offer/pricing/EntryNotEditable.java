package com.example.offer.pricing;

public class EntryNotEditable extends RuntimeException {

    public EntryNotEditable(String entryId) {
        super("entry " + entryId + " is not editable");
    }
}
