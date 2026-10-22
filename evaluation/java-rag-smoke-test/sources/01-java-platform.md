# Java platform and language basics

## JVM, JRE, and JDK

The Java Virtual Machine (JVM) executes Java bytecode. Java source code is compiled into class files containing bytecode, which the JVM loads and executes. A Java Runtime Environment (JRE) provides the JVM and runtime libraries needed to run Java applications. A Java Development Kit (JDK) includes development tools such as the compiler as well as the runtime needed to run applications.

Because compiled Java bytecode runs on a compatible JVM, the same class files can run on different operating systems that provide a suitable Java runtime. This is the basis of Java's "write once, run anywhere" portability goal; it does not guarantee that every native dependency or environment-specific behavior is portable.

## Memory and values

Java has primitive types, such as `int`, `boolean`, and `double`, and reference types, such as classes and arrays. A primitive variable holds a primitive value. A reference variable refers to an object or is `null`.

The JVM manages object memory. Garbage collection can reclaim heap objects that are no longer reachable by the application. Programmers do not explicitly free ordinary Java objects.

## Strings

`String` objects are immutable: after a String is created, its character sequence cannot be changed. Operations that appear to modify a String produce another String. Immutability makes String values safe to share and useful as map keys, provided the key is not replaced with another value.

## Packages and imports

A package groups related Java types and provides a namespace. The `package` declaration identifies the package of a source file. An `import` declaration lets source code refer to a type by its simple name instead of its fully qualified name. Importing a type does not copy or instantiate it.
