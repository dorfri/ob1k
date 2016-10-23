package com.outbrain.ob1k.http.utils;

import com.outbrain.ob1k.concurrent.ComposableFuture;
import com.outbrain.ob1k.concurrent.ComposableFutures;
import com.outbrain.ob1k.concurrent.Try;
import org.asynchttpclient.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class ComposableFutureAdapter {

  public interface Provider<T> {

    ListenableFuture<T> provide();
  }

  public static <T> ComposableFuture<T> fromListenableFuture(final Provider<T> provider) {

    return ComposableFutures.build(consumer -> {

      final ListenableFuture<T> source = provider.provide();
      source.addListener(() -> {
        try {
          final T result = source.get();
          consumer.consume(Try.fromValue(result));
        } catch (final InterruptedException e) {
          consumer.consume(Try.<T>fromError(e));
        } catch (final ExecutionException e) {
          final Throwable error = e.getCause() != null ? e.getCause() : e;
          consumer.consume(Try.<T>fromError(error));
        }
      }, ComposableFutures.getExecutor());
    });
  }
}