package com.aquatic.graphql.schema;

import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.metadata.Resolver;
import io.leangen.graphql.metadata.TypedElement;
import io.leangen.graphql.metadata.execution.Executable;
import io.leangen.graphql.metadata.execution.MethodInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrefixedAnnotatedResolverBuilderTest {

    private PrefixedAnnotatedResolverBuilder resolverBuilder;
    private static final String TEST_PREFIX = "TEST_";

    @BeforeEach
    void setUp() {
        resolverBuilder = new PrefixedAnnotatedResolverBuilder(TEST_PREFIX);
    }

    @Test
    void testTopLevelVsNestedResolvers() throws Exception {
        // Create resolvers representing the scenario:
        // query { orderChain { account, broker } }
        Resolver topLevelResolver = createTestResolver("orderChain");
        Resolver nestedFieldResolver1 = createTestResolver("account");
        Resolver nestedFieldResolver2 = createTestResolver("broker");

        Collection<Resolver> topLevelResult = resolverBuilder.addPrefixToTopLevelResolvers(
                List.of(topLevelResolver), true // isTopLevel = true, so should get prefixed
                );

        assertEquals(1, topLevelResult.size());
        String topLevelName = getResolverOperationName(topLevelResult.iterator().next());
        assertEquals(
                TEST_PREFIX + "orderChain",
                topLevelName,
                "Top-level resolver 'orderChain' should be prefixed to 'TEST_orderChain'");

        Collection<Resolver> nestedResult = resolverBuilder.addPrefixToTopLevelResolvers(
                List.of(nestedFieldResolver1, nestedFieldResolver2),
                false // isTopLevel = false, so should NOT get prefixed
                );

        assertEquals(2, nestedResult.size());
        List<Resolver> nestedList = nestedResult.stream().toList();

        String accountName = getResolverOperationName(nestedList.get(0));
        String brokerName = getResolverOperationName(nestedList.get(1));

        assertEquals("account", accountName, "Nested field resolver 'account' should remain unchanged");
        assertEquals("broker", brokerName, "Nested field resolver 'broker' should remain unchanged");

        assertFalse(accountName.startsWith(TEST_PREFIX), "Nested field 'account' should not be prefixed");
        assertFalse(brokerName.startsWith(TEST_PREFIX), "Nested field 'broker' should not be prefixed");
    }

    @Test
    void testCreatePrefixedResolverDirectly() throws Exception {
        Resolver originalResolver = createTestResolver("orderChain");

        Resolver result = resolverBuilder.createPrefixedResolver(originalResolver);

        String modifiedName = getResolverOperationName(result);
        assertEquals(TEST_PREFIX + "orderChain", modifiedName);
    }

    @Test
    void testNoDuplicatePrefixing() throws Exception {
        Resolver alreadyPrefixed = createTestResolver(TEST_PREFIX + "orderChain");

        Resolver result = resolverBuilder.createPrefixedResolver(alreadyPrefixed);

        String finalName = getResolverOperationName(result);
        assertEquals(TEST_PREFIX + "orderChain", finalName);
    }

    private Resolver createTestResolver(String operationName) throws Exception {
        Method testMethod = TestGraphQLService.class.getDeclaredMethod("getOrderChain");

        AnnotatedType enclosingType = new AnnotatedType() {
            @Override
            public java.lang.reflect.Type getType() {
                return TestGraphQLService.class;
            }

            @Override
            public <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotationClass) {
                return TestGraphQLService.class.getAnnotation(annotationClass);
            }

            @Override
            public java.lang.annotation.Annotation[] getAnnotations() {
                return TestGraphQLService.class.getAnnotations();
            }

            @Override
            public java.lang.annotation.Annotation[] getDeclaredAnnotations() {
                return TestGraphQLService.class.getDeclaredAnnotations();
            }
        };

        Executable<?> executable = new MethodInvoker(testMethod, enclosingType);
        TypedElement typedElement = new TypedElement(testMethod.getAnnotatedReturnType(), testMethod);

        return new Resolver(
                operationName, // operationName
                null, // operationDescription
                null, // operationDeprecationReason
                false, // batched
                executable, // executable
                typedElement, // typedElement
                Collections.emptyList(), // arguments
                null // complexityExpression
                );
    }

    private String getResolverOperationName(Resolver resolver) throws Exception {
        java.lang.reflect.Field operationNameField = resolver.getClass().getDeclaredField("operationName");
        operationNameField.setAccessible(true);
        return (String) operationNameField.get(resolver);
    }

    static class TestGraphQLService {
        @GraphQLQuery(name = "orderChain")
        public OrderData getOrderChain() {
            return new OrderData("ACC123", "BROKER1");
        }
    }

    static class OrderData {
        private final String account;
        private final String broker;

        public OrderData(String account, String broker) {
            this.account = account;
            this.broker = broker;
        }

        @GraphQLQuery(name = "account")
        public String getAccount() {
            return account;
        }

        @GraphQLQuery(name = "broker")
        public String getBroker() {
            return broker;
        }
    }
}
