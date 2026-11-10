package com.example.alerting.event;

/**
 * What a simulator run did.
 *
 * @param requested how many events were generated
 * @param created how many of them were new
 * @param duplicates how many had been accepted before
 */
public record SimulationResult(int requested, int created, int duplicates) {
}
