package com.haulwise.solver.model;

/**
 * Delivery priority as seen by the solver, declared from most to least important, so the natural
 * enum order ({@code URGENT < HIGH < NORMAL}) sorts the most important first. Mirrors the domain enum;
 * the service layer maps between them so the solver stays free of domain imports.
 */
public enum Priority {
    URGENT,
    HIGH,
    NORMAL
}
