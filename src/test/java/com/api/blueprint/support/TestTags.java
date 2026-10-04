package com.api.blueprint.support;

/**
 * JUnit 5 tag names, one per test layer. Maven profiles of the same name select them
 * ({@code mvn test -Psmoke}); a plain {@code mvn test} runs every layer except
 * {@link #KNOWN_DEFECT}.
 */
public final class TestTags {

    /** Fast checks of the critical paths; run on every pull request. */
    public static final String SMOKE = "smoke";

    /** The full functional suite against the live API and the offline unit tests. */
    public static final String REGRESSION = "regression";

    /** Pact consumer contracts, verified against a local Pact mock server. */
    public static final String CONTRACT = "contract";

    /** Spec requirements the live API does not meet yet; see KNOWN_DEFECTS.md. */
    public static final String KNOWN_DEFECT = "known-defect";

    private TestTags() {
    }
}
