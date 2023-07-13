package com.ttl.internal.vn.tool.builder.task;

import java.util.concurrent.Flow;

public abstract class DefaultSubscriber<T> implements Flow.Subscriber<T> {
    public void onSubscribe(Flow.Subscription subscription) {
    }

    public void onNext(T item) {
    }

    public void onError(Throwable throwable) {
    }

    public void onComplete() {
    }
}
