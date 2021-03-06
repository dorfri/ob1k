package com.outbrain.ob1k.concurrent;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.outbrain.ob1k.concurrent.combiners.BiFunction;
import com.outbrain.ob1k.concurrent.combiners.TriFunction;
import com.outbrain.ob1k.concurrent.eager.EagerComposableFuture;
import com.outbrain.ob1k.concurrent.handlers.*;
import org.junit.Assert;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import rx.Observable;

import static com.outbrain.ob1k.concurrent.ComposableFutures.*;
import static com.outbrain.ob1k.concurrent.ComposableFutures.toObservable;

/**
 * User: aronen
 * Date: 7/2/13
 * Time: 10:20 AM
 */
public class ComposableFutureTest {

    public static final int ITERATIONS = 100000;

    @Test
    public void testForeach() throws ExecutionException, InterruptedException {
        final List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            numbers.add(i);
        }

        final List<Integer> empty = new ArrayList<>();
        final ComposableFuture<List<Integer>> first3Even = foreach(numbers, empty, new ForeachHandler<Integer, List<Integer>>() {
            @Override
            public ComposableFuture<List<Integer>> handle(final Integer element, final List<Integer> aggregateResult) {
                if (aggregateResult.size() < 3 && element % 2 == 0) {
                    aggregateResult.add(element);
                }

                return fromValue(aggregateResult);
            }
        });

        final List<Integer> result = first3Even.get();
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get(0), new Integer(2));
        Assert.assertEquals(result.get(1), new Integer(4));
        Assert.assertEquals(result.get(2), new Integer(6));
    }

    @Test
    public void testRepeat() throws Exception {
        final ComposableFuture<Integer> future = repeat(10, 0, new FutureSuccessHandler<Integer, Integer>() {
            @Override
            public ComposableFuture<Integer> handle(final Integer result) {
                return fromValue(result + 1);
            }
        });
        Assert.assertEquals(10, (int) future.get());
    }

    @Test
    public void testRecursive() throws Exception {
        final AtomicInteger atomicInteger = new AtomicInteger();
        final ComposableFuture<Integer> future = recursive(new Supplier<ComposableFuture<Integer>>() {
            @Override
            public ComposableFuture<Integer> get() {
                return fromValue(atomicInteger.incrementAndGet());
            }
        }, input -> input >= 10);
        Assert.assertEquals(10, (int) future.get());
    }

    @Test
    public void testBatch() throws Exception {
        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        ComposableFuture<List<String>> res = batch(nums, 2, new FutureSuccessHandler<Integer, String>() {
            @Override
            public ComposableFuture<String> handle(final Integer result) {
                return schedule(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "num:" + result;
                    }
                }, 1, TimeUnit.SECONDS);
            }
        });

        List<String> results = res.get();
        Assert.assertEquals(results.size(), nums.size());

    }

    @Test
    public void testBatchUnordered() throws Exception {
        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        ComposableFuture<List<String>> res = batchUnordered(nums, 2, new FutureSuccessHandler<Integer, String>() {
            @Override
            public ComposableFuture<String> handle(final Integer result) {
                return schedule(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "num:" + result;
                    }
                }, 1, TimeUnit.SECONDS);
            }
        });

        List<String> results = res.get();
        Assert.assertEquals(results.size(), nums.size());

    }

    @Test
    public void testBatchToStream() throws Exception {
        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Observable<List<String>> stream = batchToStream(nums, 2, new FutureSuccessHandler<Integer, String>() {
            @Override
            public ComposableFuture<String> handle(final Integer result) {
                return schedule(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "num:" + result;
                    }
                }, 1, TimeUnit.SECONDS);
            }
        });

        Iterable<List<String>> iterator = stream.toBlocking().toIterable();
        int totalElements = 0;
        for(List<String> batch : iterator) {
            int batchSize = batch.size();
            totalElements += batchSize;
            Assert.assertEquals(batchSize, 2);
        }
        Assert.assertEquals(totalElements, nums.size());
    }

    @Test
    @Ignore("performance test")
    public void testThreadPool() {
        testWithRegularThreadPool(true);
        //    testWithRegularThreadPool(false);
    }

    @Test
    @Ignore("performance test")
    public void testSingleThreadBenchmark() {
        final long t1 = System.currentTimeMillis();
        long sum = 0;

        for (long i = 0; i < ITERATIONS; i++) {
            final long phase1 = computeHash(i);
            final long phase2 = computeHash(phase1);
            final long phase3 = computeHash(phase2);
            sum += phase3;
        }

        final long t2 = System.currentTimeMillis();
        System.out.println("total time: " + (t2 - t1) + " for sum: " + sum);
    }

    private void testWithRegularThreadPool(final boolean delegate) {
        final List<ComposableFuture<Long>> futures = new ArrayList<>();

        final long t1 = System.currentTimeMillis();

        for (int i = 0; i < ITERATIONS; i++) {
            final long seed = i;
            final ComposableFuture<Long> f1 = submit(delegate, new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return computeHash(seed);
                }
            });

            final ComposableFuture<Long> f2 = f1.continueOnSuccess(new SuccessHandler<Long, Long>() {
                @Override
                public Long handle(final Long seed) {
                    return computeHash(seed);
                }
            });

            final ComposableFuture<Long> f3 = f2.continueOnSuccess(new SuccessHandler<Long, Long>() {
                @Override
                public Long handle(final Long seed) {
                    return computeHash(seed);
                }
            });

            futures.add(f3);

        }

        final ComposableFuture<List<Long>> all = all(futures);
        try {
            final List<Long> res = all.get();
            final long t2 = System.currentTimeMillis();
            long sum = 0;
            for (final long num : res) {
                sum += num;
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static long computeHash(final long seed) {
        long value = seed;
        for (int i = 0; i < 10000; i++) {
            value ^= value << 13;
            value ^= value >>> 17;
            value ^= value << 5;
        }

        return value;
    }

    @Test
    public void testContinuations() {
        final ComposableFuture<String> res =
            schedule(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return "lala";
                }
            }, 100, TimeUnit.MILLISECONDS).continueWith(new FutureResultHandler<String, String>() {
                @Override
                public ComposableFuture<String> handle(final Try<String> result) {
                    return fromError(new RuntimeException("bhaaaaa"));
                }
            }).continueOnSuccess(new SuccessHandler<String, String>() {
                @Override
                public String handle(final String result) {
                    return "second lala";
                }
            }).continueOnError(new ErrorHandler<String>() {
                @Override
                public String handle(final Throwable error) {
                    return "third lala";
                }
            }).continueOnError(new ErrorHandler<String>() {
                @Override
                public String handle(final Throwable error) {
                    return "baaaaddddd";
                }
            }).continueOnSuccess(new SuccessHandler<String, String>() {
                @Override
                public String handle(final String result) throws ExecutionException {
                    throw new ExecutionException(new RuntimeException("booo"));
                }
            });

        try {
            final String result = res.get();
            Assert.fail("got result instead of an exception");
        } catch (InterruptedException | ExecutionException e) {
            final String exTypeName = e.getCause().getClass().getName();
            Assert.assertEquals(exTypeName, RuntimeException.class.getName());
        }
    }

    private static final class Person {
        public final int age;
        public final String name;
        public final double weight;


        private Person(final int age, final String name, final double weight) {
            this.age = age;
            this.name = name;
            this.weight = weight;
        }
    }

    @Test
    public void testComposingFutureTypes() {
        final String name = "haim";
        final int age = 23;
        final double weight = 70.3;

        final ComposableFuture<String> futureName = fromValue(name);
        final ComposableFuture<Integer> futureAge = fromValue(age);
        final ComposableFuture<Double> futureWeight = fromValue(weight);

//      final ComposableFuture<Double> weight = fromError(new RuntimeException("Illegal Weight error!"));

        final ComposableFuture<Person> person = combine(futureName, futureAge, futureWeight, new TriFunction<String, Integer, Double, Person>() {
            @Override
            public Person apply(final String name, final Integer age, final Double weight) {
                return new Person(age, name, weight);
            }
        });

        try {
            final Person result = person.get();
            Assert.assertEquals(result.age, age);
            Assert.assertEquals(result.name, name);
            Assert.assertEquals(result.weight, weight, 0);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail(e.getMessage());
        }

        final ComposableFuture<String> first = fromValue("1");
        final ComposableFuture<Integer> second = fromValue(2);
        final ComposableFuture<Object> badRes = combine(first, second, new BiFunction<String, Integer, Object>() {
            @Override
            public Object apply(final String left, final Integer right) throws ExecutionException {
                throw new ExecutionException(new RuntimeException("not the same..."));
            }
        });

        try {
            badRes.get();
            Assert.fail("should get an error");
        } catch (final InterruptedException e) {
            Assert.fail(e.getMessage());
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause().getMessage().contains("not the same..."));
        }

    }

    @Test
    public void testSlowFuture() {
        final ComposableFuture<String> f1 = schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "slow";
            }
        }, 1, TimeUnit.SECONDS);

        final ComposableFuture<String> f2 = fromValue("fast1");
        final ComposableFuture<String> f3 = fromValue("fast2");

        final ComposableFuture<List<String>> res = all(Arrays.asList(f1, f2, f3));
        final long t1 = System.currentTimeMillis();
        try {
            final List<String> results = res.get();
            final long t2 = System.currentTimeMillis();
            Assert.assertTrue("time is: " + (t2 - t1), (t2 - t1) > 900); // not
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail(e.getMessage());
        }

        final ComposableFuture<String> f4 = schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "slow";
            }
        }, 1, TimeUnit.SECONDS);
        final ComposableFuture<String> f5 = fromError(new RuntimeException("oops"));
        final ComposableFuture<List<String>> res2 = all(true, Arrays.asList(f4, f5));
        final long t3 = System.currentTimeMillis();
        try {
            final List<String> results = res2.get();
            Assert.fail("should get error.");
        } catch (InterruptedException | ExecutionException e) {
            final long t4 = System.currentTimeMillis();
            Assert.assertTrue((t4 - t3) < 100);
        }

        final ComposableFuture<String> f6 = schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "slow";
            }
        }, 1, TimeUnit.SECONDS);
        final ComposableFuture<String> f7 = fromError(new RuntimeException("oops"));
        final ComposableFuture<List<String>> res3 = all(true, Arrays.asList(f6, f7));
        final long t5 = System.currentTimeMillis();
        try {
            final List<String> results = res3.get();
            Assert.fail("should get error.");
        } catch (InterruptedException | ExecutionException e) {
            final long t6 = System.currentTimeMillis();
            System.out.println("time took to fail: " + (t6 - t5));
            Assert.assertTrue((t6 - t5) < 100);
        }

    }

    @Test
    public void testFuturesToStream() throws InterruptedException {
        final ComposableFuture<Long> first = schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        }, 1, TimeUnit.SECONDS);

        final ComposableFuture<Long> second = schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        }, 2, TimeUnit.SECONDS);

        final ComposableFuture<Long> third = schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        }, 3, TimeUnit.SECONDS);

        final Iterable<Long> events = toHotObservable(Arrays.asList(first, second, third), true).toBlocking().toIterable();
        long prevEvent = 0;
        int counter = 0;
        for (final Long event : events) {
            counter++;
            Assert.assertTrue("event should have bigger timestamp than the previous one", event > prevEvent);
            prevEvent = event;
        }

        Assert.assertEquals("should receive 3 events", counter, 3);

    }

    @Test
    public void testFutureProviderToStream() {
        final Observable<Long> stream = toObservable(new FutureProvider<Long>() {
            private volatile int index = 3;
            private volatile ComposableFuture<Long> currentRes;

            @Override
            public boolean moveNext() {
                if (index > 0) {
                    index--;
                    currentRes = schedule(new Callable<Long>() {
                        @Override
                        public Long call() throws Exception {
                            return System.currentTimeMillis();
                        }
                    }, 100, TimeUnit.MILLISECONDS);

                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public ComposableFuture<Long> current() {
                return currentRes;
            }
        });

        long current = System.currentTimeMillis();
        final Iterable<Long> events = stream.toBlocking().toIterable();
        int counter = 0;
        for (final Long event : events) {
            Assert.assertTrue(event > current);
            current = event;
            counter++;
        }

        Assert.assertTrue(counter == 3);

    }

    @Test
    public void testFirstNoTimeout() throws Exception {
        final PassThroughCount passThroughCount = new PassThroughCount(3);
        try {
            final Map<String, ComposableFuture<String>> elements = createElementsMap(passThroughCount);

            final Map<String, String> res = first(elements, 3).get();
            Assert.assertEquals(3,res.size());
            Assert.assertEquals("one", res.get("one"));
            Assert.assertEquals("two", res.get("two"));
            Assert.assertEquals("three", res.get("three"));
        } finally {
            passThroughCount.releaseAllWaiters(); // release last two guys
        }
    }

    @Test
    public void testFirstWithTimeout() throws Exception {
        final PassThroughCount passThroughCount = new PassThroughCount(2);
        try {
            final Map<String, ComposableFuture<String>> elements = createElementsMap(passThroughCount);
            passThroughCount.waitForPassers();  // we do not want that the first two elements will not finish due to scheduling issues
            final Map<String, String> res = first(elements, 3, 10, TimeUnit.MILLISECONDS).get();

            Assert.assertEquals(2, res.size());
            Assert.assertEquals("one", res.get("one"));
            Assert.assertEquals("two", res.get("two"));
        } finally {
            passThroughCount.releaseAllWaiters(); // release last two guys
        }
    }

    @Test
    public void testAllFailOnError() throws Exception {
        final PassThroughCount passThroughCount = new PassThroughCount(5);
        final Map<String, ComposableFuture<String>> elements = createElementsMap(passThroughCount);

        try {
            all(true, elements).get();
            Assert.fail("should get an exception");
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause().getMessage().contains("bad element"));
        } finally {
            passThroughCount.releaseAllWaiters(); // release last two guys
        }

    }

    @Test
    public void testAllFailFast() throws Exception {
        final Map<String, ComposableFuture<String>> elements = new HashMap<>();

        elements.put("one", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Thread.sleep(100);
                return "one";
            }
        }));

        elements.put("two", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                throw new RuntimeException("error...");
            }
        }));

        final long t1 = System.currentTimeMillis();
        try {
            all(true, elements).get();
            Assert.fail("should fail");
        } catch (final ExecutionException e) {
            final long t2 = System.currentTimeMillis();
            Assert.assertTrue("should fail fast", (t2 - t1) < 50);
        }
    }
    class PassThroughCount {
        final CountDownLatch waitersLatch;
        final CountDownLatch passersLatch;
        final int numToPass;

        public PassThroughCount(int numToPass) {
            waitersLatch = new CountDownLatch(1);
            passersLatch = new CountDownLatch(numToPass);
            this.numToPass = numToPass;
        }

        public void awaitOrPass(long myOrder) throws InterruptedException {
            if (myOrder  > numToPass)  waitersLatch.await() ;
            else passersLatch.countDown();
        }
        public void releaseAllWaiters() {
            waitersLatch.countDown();
        }
        public void waitForPassers() throws InterruptedException {
            passersLatch.await();
        }
    }

    private Map<String, ComposableFuture<String>> createElementsMap(final PassThroughCount passThroughCount) {
        final Map<String, ComposableFuture<String>> elements = new HashMap<>();

        elements.put("one", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                passThroughCount.awaitOrPass(1);
                return "one";
            }
        }));
        elements.put("two", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                passThroughCount.awaitOrPass(2);
                return "two";
            }
        }));
        elements.put("three", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                passThroughCount.awaitOrPass(3);
                return "three";
            }
        }));
        elements.put("four", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                passThroughCount.awaitOrPass(4);
                throw new RuntimeException("bad element");
            }
        }));
        elements.put("five", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                passThroughCount.awaitOrPass(5);
                return "five";
            }
        }));
        return elements;
    }

    @Test
    public void testWithTimeout() throws Exception {
        final String RES_STR= "result";
        final EagerComposableFuture<String> value = new EagerComposableFuture<>();

        ComposableFuture<String> effectiveValue = value.withTimeout(100, TimeUnit.MILLISECONDS);
        Thread.sleep(50);
        value.set(RES_STR);
        Assert.assertEquals(RES_STR, value.get());
        Assert.assertEquals(RES_STR,effectiveValue.get());

    }

    @Test(expected=ExecutionException.class)
    public void testWithTimeoutExpired() throws Exception {
        final String RES_STR= "result";
        final EagerComposableFuture<String> value = new EagerComposableFuture<>();

        ComposableFuture<String> effectiveValue = value.withTimeout(50, TimeUnit.MILLISECONDS);
        Thread.sleep(100);
        value.set(RES_STR);
        Assert.assertEquals(RES_STR , value.get());
        effectiveValue.get(); // this should throw an exception
    }

}
