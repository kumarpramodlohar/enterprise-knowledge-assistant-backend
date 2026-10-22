# Java 5 language and library features

Java 5, also called Java 1.5 or J2SE 5.0, introduced several language features that reduce boilerplate and improve type safety.

## Generics

Generics let classes and methods declare type parameters, such as `List<String>`. They provide compile-time type checking for values used with generic types and reduce the need for casts when retrieving values. Java implements generics primarily through type erasure: most generic type-parameter information is removed during compilation, with some signatures retained in class metadata. Java generic type arguments must be reference types, so primitive values use wrapper classes such as `Integer` instead of `int`.

## Enums and annotations

An enum declares a fixed set of named instances, such as `enum Day { MONDAY, TUESDAY }`. Enums are type-safe and can also declare fields, constructors, and methods. Unlike integer constants, enum values belong to a distinct type.

Annotations attach structured metadata to declarations. For example, `@Override` marks a method intended to override an inherited method, allowing the compiler to detect some mistakes. An annotation does not by itself change a method's runtime behavior; tools or frameworks may inspect annotations.

## Other language features

Autoboxing converts between a primitive and its corresponding wrapper when the context requires it, such as `int` and `Integer`. Unboxing converts a wrapper back to a primitive. Unboxing a `null` wrapper throws `NullPointerException`.

The enhanced `for` loop iterates over arrays and `Iterable` values without explicitly managing an index or iterator. It is useful when each element is processed in sequence.

Varargs allow a method to accept a variable number of arguments using a final parameter written with `...`, for example `String... names`. Inside the method, that parameter is treated as an array. A varargs parameter must be the final parameter in the method declaration.

Static import lets source code refer to accessible static members without qualifying them by their declaring type. It is declared with `import static` and does not copy the imported member.

The `java.util.Formatter` API and formatted output methods such as `printf` provide format-string-based output, including conversions such as `%s` for text and `%d` for integral values.
