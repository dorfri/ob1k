package com.outbrain.ob1k.concurrent;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents a computation that may either result in an exception, or return a successfully computed value.
 * Similar to Scala's Try.
 *
 * @param <T> the type returned by the computation.
 * @author marenzon, aronen
 */
public interface Try<T> {

  /**
   * Creates new {@link Success} try from given value.
   *
   * @param value computation result
   * @param <T>   computation type
   * @return new {@link Success} Try
   */
  static <T> Try<T> fromValue(final T value) {
    return Success.of(value);
  }

  /**
   * Creates new {@link Failure} Try from given exception.
   *
   * @param error computation error
   * @param <T>   computation type
   * @return new {@link Failure} Try
   */
  static <T> Try<T> fromError(final Throwable error) {
    return Failure.of(error);
  }

  /**
   * Creates new either {@link Success} or {@link Failure} Try by given supplier result.
   * In case supplier throws exception, {@link Failure} Try will be returned.
   *
   * @param supplier computation value supplier
   * @param <T>      computation type
   * @return either {@link Success}, or {@link Failure} by supplier result
   */
  static <T> Try<T> apply(final CheckedSupplier<? extends T> supplier) {
    try {
      return fromValue(supplier.get());
    } catch (final Exception e) {
      return fromError(e);
    }
  }

  /**
   * Flats nested {@link Try} of {@link Try} into flatten one.
   *
   * @param nestedTry nested try to flatten
   * @param <U>       computation type
   * @return flatten Try
   */
  static <U> Try<U> flatten(final Try<? extends Try<U>> nestedTry) {
    if (nestedTry.isFailure()) {
      return fromError(nestedTry.getError());
    }
    return nestedTry.getValue();
  }

  /**
   * @return true if the Try is a Success, or false in case of Failure.
   */
  boolean isSuccess();

  /**
   * @return true if the Try is a Failure, or false in case of Success.
   */
  boolean isFailure();

  /**
   * Maps the value of T to the value of type U.
   *
   * @param function a function to apply to the value of T.
   * @param <U>      the type of the result.
   * @return result of mapped value in a Try.
   */
  <U> Try<U> map(Function<? super T, ? extends U> function);

  /**
   * Maps the value of T to a new Try of U.
   *
   * @param function a function to apply to the value of T.
   * @param <U>      the type of the result.
   * @return a new Try of the mapped T value.
   */
  <U> Try<U> flatMap(Function<? super T, ? extends Try<U>> function);

  /**
   * Recovers a {@link Failure} Try into a {@link Success} pne
   *
   * @param recover a function to apply the exception
   * @return a new Success
   */
  Try<T> recover(Function<Throwable, ? extends T> recover);

  /**
   * Recovers a {@link Failure} Try into a new supplied Try
   *
   * @param recover a function to apply the exception
   * @return a new Try
   */
  Try<T> recoverWith(Function<Throwable, ? extends Try<T>> recover);

  /**
   * Applies mapper function in case of Success, or recover function in case of Failure.
   * Transforms current Try to a new one, depends on Try status.
   *
   * @param mapper  a function to apply for the Success value
   * @param recover a function to apply for the Failure exception
   * @param <U>     computation type
   * @return a new Try from applied functions
   */
  <U> Try<U> transform(Function<? super T, ? extends Try<U>> mapper,
                       Function<Throwable, ? extends Try<U>> recover);

  /**
   * Feeds the value to a {@link java.util.function.Consumer} if {@link Try} is
   * a {@link Success}. If {@link Try} is a {@link Failure} it takes no action.
   *
   * @param consumer for the value of T
   */
  void forEach(java.util.function.Consumer<? super T> consumer);

  /**
   * Applies recover function in case of Failure or mapper function in case of Success.
   * If mapper is applied and throws an exception, then recover is applied with this exception.
   *
   * @param mapper  a function to apply for the Success value
   * @param recover a function to apply for the Failure exception
   * @param <U>     computation type
   * @return the result of applied functions
   */
  <U> Try<U> fold(Function<? super T, ? extends U> mapper,
                  Function<Throwable, ? extends U> recover);

  /**
   * Ensures that the (successful) result of the current Try satisfies the given predicate,
   * or fails with the given value.
   *
   * @param predicate the predicate for the result
   * @return new future with same value if predicate returns true, else new future with a failure
   */
  Try<T> ensure(final Predicate<? super T> predicate);

  /**
   * @return the value if computation succeed, or throws the exception of the error.
   * @throws Throwable computation error
   */
  T get() throws Throwable;

  /**
   * @return the computed value, else null.
   */
  T getValue();

  /**
   * @return the error if computation failed, else null.
   */
  Throwable getError();

  /**
   * @param defaultValue the default value to return if Try is Failure.
   * @return the value if Success, else defaultValue.
   */
  T getOrElse(T defaultValue);

  /**
   * @param defaultTry the default try to return if Try is Failure.
   * @return the try if Success, else defaultTry.
   */
  Try<T> orElse(Try<T> defaultTry);

  /**
   * @return {@link Optional} of the current Try
   */
  Optional<T> toOptional();

  final class Success<T> implements Try<T> {

    private final T value;

    private Success(final T value) {
      this.value = value;
    }

    public static <T> Success<T> of(final T value) {
      return new Success<>(value);
    }

    @Override
    public boolean isSuccess() {
      return true;
    }

    @Override
    public boolean isFailure() {
      return false;
    }

    @Override
    public T getValue() {
      return value;
    }

    @Override
    public Throwable getError() {
      return null;
    }

    @Override
    public T getOrElse(final T defaultValue) {
      return value;
    }

    @Override
    public Try<T> orElse(final Try<T> defaultTry) {
      return this;
    }

    @Override
    public Optional<T> toOptional() {
      return Optional.ofNullable(value);
    }

    @Override
    public T get() throws Throwable {
      return value;
    }

    @Override
    public <U> Try<U> map(final Function<? super T, ? extends U> func) {
      return apply(() -> func.apply(value));
    }

    @Override
    public <U> Try<U> fold(final Function<? super T, ? extends U> mapper,
                           final Function<Throwable, ? extends U> recover) {
      return Try.<U>apply(() -> mapper.apply(value)).recover(recover::apply);
    }

    @Override
    public Try<T> ensure(final Predicate<? super T> predicate) {
      if (predicate.test(value)) {
        return fromValue(value);
      }

      return fromError(new NoSuchElementException("predicate is not satisfied"));
    }

    @Override
    public <U> Try<U> flatMap(final Function<? super T, ? extends Try<U>> func) {
      return flatten(apply(() -> func.apply(getValue())));
    }

    @Override
    public Try<T> recover(final Function<Throwable, ? extends T> function) {
      return this;
    }

    @Override
    public Try<T> recoverWith(final Function<Throwable, ? extends Try<T>> function) {
      return this;
    }

    @Override
    public <U> Try<U> transform(final Function<? super T, ? extends Try<U>> mapper,
                                final Function<Throwable, ? extends Try<U>> recover) {
      return mapper.apply(value);
    }

    @Override
    public void forEach(final Consumer<? super T> consumer) {
      consumer.accept(value);
    }

    @Override
    public String toString() {
      return "Success(" + value + ")";
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Success<?> success = (Success<?>) o;
      return Objects.equals(value, success.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  final class Failure<T> implements Try<T> {

    private final Throwable error;

    private Failure(final Throwable error) {
      this.error = error;
    }

    public static <T> Failure<T> of(final Throwable error) {
      return new Failure<>(error);
    }

    @Override
    public boolean isSuccess() {
      return false;
    }

    @Override
    public boolean isFailure() {
      return true;
    }

    @Override
    public T getValue() {
      return null;
    }

    @Override
    public Throwable getError() {
      return error;
    }

    @Override
    public T getOrElse(final T defaultValue) {
      return defaultValue;
    }

    @Override
    public Try<T> orElse(final Try<T> defaultTry) {
      return defaultTry;
    }

    @Override
    public Optional<T> toOptional() {
      return Optional.empty();
    }

    @Override
    public T get() throws Throwable {
      throw error;
    }

    @Override
    public <U> Try<U> map(final Function<? super T, ? extends U> function) {
      return fromError(error);
    }

    @Override
    public <U> Try<U> flatMap(final Function<? super T, ? extends Try<U>> function) {
      return fromError(error);
    }

    @Override
    public Try<T> recover(final Function<Throwable, ? extends T> function) {
      return apply(() -> function.apply(error));
    }

    @Override
    public Try<T> recoverWith(final Function<Throwable, ? extends Try<T>> function) {
      return flatten(apply(() -> function.apply(error)));
    }

    @Override
    public <U> Try<U> transform(final Function<? super T, ? extends Try<U>> mapper,
                                final Function<Throwable, ? extends Try<U>> recover) {
      return recover.apply(error);
    }

    @Override
    public <U> Try<U> fold(final Function<? super T, ? extends U> mapper,
                           final Function<Throwable, ? extends U> recover) {
      return apply(() -> recover.apply(error));
    }

    @Override
    public Try<T> ensure(final Predicate<? super T> predicate) {
      return this;
    }

    @Override
    public void forEach(final Consumer<? super T> consumer) {

    }

    @Override
    public String toString() {
      return "Failure(" + error.toString() + ")";
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Failure<?> failure = (Failure<?>) o;
      return Objects.equals(error, failure.error);
    }

    @Override
    public int hashCode() {
      return Objects.hash(error);
    }
  }
}
