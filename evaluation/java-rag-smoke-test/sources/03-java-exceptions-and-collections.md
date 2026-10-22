# Java exceptions and collections

## Exceptions

An exception represents an abnormal condition. Checked exceptions must be caught or declared by the method with `throws`. Runtime exceptions are unchecked and do not have that compile-time requirement. `throw` raises a particular exception object; `throws` in a method declaration lists exceptions that may propagate to the caller.

A `try` block contains code that may fail. A `catch` block handles a matching exception. A `finally` block runs when control leaves the associated try/catch flow, including when an exception is thrown, and is commonly used for cleanup. A `finally` block may not complete normally if the process terminates abruptly.

## Collections

An array has a fixed length after creation and can hold elements of one declared component type. `ArrayList` is a resizable list implementation from the Collections Framework; it grows as elements are added and supports indexed access.

A `List` preserves element order and permits duplicates. A `Set` models unique elements and does not permit duplicates according to its equality rules. A `Map` associates keys with values; keys are unique within the map.

In a `HashMap`, inserting a value with a key that already exists replaces the value associated with that key. The map does not retain two separate entries with equal keys. Correct implementations of `equals` and `hashCode` are important for hash-based collections: objects considered equal must have equal hash codes.

## Iteration

An `Iterator` traverses a collection one element at a time using `hasNext()` and `next()`. In Java 5, the enhanced `for` loop can iterate over arrays and objects that implement `Iterable`. It avoids explicit index management for those traversals.
