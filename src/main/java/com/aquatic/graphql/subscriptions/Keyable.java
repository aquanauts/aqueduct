package com.aquatic.graphql.subscriptions;

/** An item with a stable identity key, used by {@link ChangeDetectionPublisher} to diff datasets. */
public interface Keyable {

    String getKey();
}
