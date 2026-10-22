package com.nexus.core;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A transaction on the core database: the database of the signed-in company (doc 29, phase B). Use it instead of a plain
 * {@code @Transactional}, which would start a transaction on the old model's database and leave core writes outside it.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(transactionManager = CoreTransactional.MANAGER)
public @interface CoreTransactional {

    /** The bean name of the core transaction manager. */
    String MANAGER = "coreTransactionManager";
    /** The bean name of the JdbcTemplate over the core database. */
    String JDBC = "coreJdbcTemplate";
    /** The bean name of a TransactionTemplate on the core transaction manager. */
    String TEMPLATE = "coreTransactionTemplate";
    /** The bean name of the core entity manager factory. */
    String ENTITY_MANAGER_FACTORY = "coreEntityManagerFactory";

    @AliasFor(annotation = Transactional.class)
    boolean readOnly() default false;

    @AliasFor(annotation = Transactional.class)
    Propagation propagation() default Propagation.REQUIRED;
}
