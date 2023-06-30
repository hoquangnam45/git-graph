package com.ttl.internal.vn.tool.builder.task;

import java.util.concurrent.Flow;

public interface DefaultSubscriber<T> extends Flow.Subscriber<T> {
    default void onSubscribe(Flow.Subscription subscription) {}
    default void onNext(T item) {}
    default void onError(Throwable throwable) {}
    default void onComplete() {}
}
