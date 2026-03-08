package com.example.springintegration.domain;

/**
 * Carries the raw response from the remote microservice between step 1 and
 * step 2 of the Type1 processing flow.
 *
 * <p>This is the <em>natural</em> intermediate type for the Type1 pipeline.
 * Step 1 produces it on the happy path; step 2 consumes it and converts it to
 * a final {@link ProcessingResult}. If step 1 fails, the error-handling advice
 * produces a {@link ProcessingResult#failure} instead, so step 2 must accept
 * {@code Object} and guard with an {@code instanceof} check before casting.</p>
 */
public class IntermediateResult {

    private final Object rawData;

    public IntermediateResult(Object rawData) {
        this.rawData = rawData;
    }

    public Object getRawData() {
        return rawData;
    }

    @Override
    public String toString() {
        return "IntermediateResult{rawData=" + rawData + "}";
    }
}
