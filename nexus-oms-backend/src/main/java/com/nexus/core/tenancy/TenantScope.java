package com.nexus.core.tenancy;

import java.util.List;
import java.util.function.Supplier;

/**
 * Which company's database the core is working in (doc 29, phase B). Each company has its own core database; the
 * shared database ({@link #SHARED}) serves companies that have none yet, and the tests.
 * <p>
 * The core only sees this interface. The application that hosts the core implements it, because it knows who is
 * signed in; the core must never depend on that application (see CoreBoundaryTest).
 */
public interface TenantScope {

    /** The key of the shared database. */
    String SHARED = "shared";

    /** The key of the database the current thread works in: a company id, or {@link #SHARED}. */
    String currentDatabaseKey();

    /** The companies that have a database of their own. */
    List<String> companiesWithOwnDatabase();

    /** Runs the work as the company (in its database), then restores whatever was current before. */
    <T> T callAs(String companyId, Supplier<T> work);

    /**
     * Runs the work once in every core database: the shared one first, then each company's. Background jobs use it, as
     * they run with no one signed in.
     */
    default void forEachDatabase(Runnable work) {
        work.run();
        for (String companyId : companiesWithOwnDatabase()) {
            callAs(companyId, () -> {
                work.run();
                return null;
            });
        }
    }
}
