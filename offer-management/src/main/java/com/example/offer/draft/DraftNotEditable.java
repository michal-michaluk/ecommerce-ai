package com.example.offer.draft;

public class DraftNotEditable extends RuntimeException {
    public DraftNotEditable(String productId, DraftState state) {
        super("draft for product " + productId + " is not editable in state " + state);
    }
}
