package com.aquatic.graphql.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose SPQR-annotated methods form part of the GraphQL API, so schema tooling
 * (e.g. {@code SchemaExport}) can discover it by classpath scan instead of central enumeration.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphQLApi {}
