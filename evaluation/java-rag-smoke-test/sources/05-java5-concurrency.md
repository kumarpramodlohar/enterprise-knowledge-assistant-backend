# Java 5 concurrency utilities

Java 5 added the `java.util.concurrent` package, which provides higher-level concurrency utilities in addition to intrinsic synchronization.

## Executors and futures

An `Executor` separates task submission from the mechanism used to run tasks. `ExecutorService` extends this idea with lifecycle management and methods for submitting tasks. Applications should shut down an executor service when it is no longer needed so its worker resources can be released.

Submitting a `Callable` to an executor can return a `Future`. A `Future` represents a result that may become available later; callers can check whether the task is done, cancel it, or call `get()` to wait for and retrieve the result.

## Coordination and concurrent data

A `BlockingQueue` is a thread-safe queue that can block producers or consumers when capacity or available elements require waiting. It is useful for producer-consumer designs.

`CountDownLatch` lets one or more threads wait until a count reaches zero. Other threads call `countDown()` to decrement the count. A latch is generally one-shot; it cannot be reset for another cycle.

`AtomicInteger` supports atomic integer operations such as increment and compare-and-set without requiring callers to use a separate synchronized block for those operations.

`ConcurrentHashMap` is a thread-safe map designed for concurrent access. It supports concurrent retrieval and updates without requiring callers to synchronize every operation on the entire map.

## Locks

The `Lock` interface and implementations such as `ReentrantLock` provide explicit locking operations. A thread that acquires a lock should release it in a `finally` block so an exception does not leave the lock held. Explicit locks can support capabilities such as interruptible acquisition and timed acquisition that are not expressed by a basic `synchronized` block.

## Corpus limits

This smoke-test corpus does not specify Java 5 default heap sizes, default garbage collector selection, exact performance characteristics, or vendor-specific licensing terms. Do not infer those facts from these documents.
