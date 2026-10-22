# Java RAG smoke-test questions and answer key

Upload the five files under `sources/`, **not this answer key**. Select the same collection for every question and leave optional metadata filters blank. The expected answers are deliberately concise; equivalent grounded wording is acceptable.

| # | Question | Expected answer | Related source |
|---:|---|---|---|
| 1 | What executes Java bytecode? | The Java Virtual Machine (JVM) loads and executes Java bytecode. | `01-java-platform.md` |
| 2 | What is the difference between a JRE and a JDK? | A JRE provides the JVM and runtime libraries; a JDK adds development tools such as the compiler. | `01-java-platform.md` |
| 3 | What is Java bytecode? | Bytecode is the compiled instruction format stored in class files and executed by a compatible JVM. | `01-java-platform.md` |
| 4 | Why can Java class files run on different operating systems? | Compatible JVMs on those systems can execute the same Java bytecode. | `01-java-platform.md` |
| 5 | Does “write once, run anywhere” guarantee that every Java program is portable? | No. Native dependencies and environment-specific behavior can still limit portability. | `01-java-platform.md` |
| 6 | Give two examples of Java primitive types. | `int` and `boolean` are primitive types; `double` is another example. | `01-java-platform.md` |
| 7 | What does a Java reference variable hold? | It refers to an object or has the value `null`. | `01-java-platform.md` |
| 8 | What does garbage collection do? | It can reclaim heap objects that are no longer reachable by the application. | `01-java-platform.md` |
| 9 | Are Java String objects mutable? | No. A String’s character sequence cannot be changed after the String is created; operations produce another String. | `01-java-platform.md` |
| 10 | What is the purpose of a Java package declaration? | It identifies the package of a source file, grouping related types and providing a namespace. | `01-java-platform.md` |
| 11 | What is an object in Java? | An object is an instance of a class. | `02-java-object-oriented-programming.md` |
| 12 | What does a constructor do? | It initializes a newly created object; its name matches the class and it has no return type. | `02-java-object-oriented-programming.md` |
| 13 | What is a common encapsulation pattern for object state? | Make fields private and expose controlled operations through methods. | `02-java-object-oriented-programming.md` |
| 14 | What does package-private access mean? | With no access modifier, a member is accessible within the same package. | `02-java-object-oriented-programming.md` |
| 15 | How many direct superclasses can a Java class extend? | One; Java classes have single class inheritance. | `02-java-object-oriented-programming.md` |
| 16 | Can a Java class implement multiple interfaces? | Yes. A class can implement more than one interface. | `02-java-object-oriented-programming.md` |
| 17 | How does runtime polymorphism choose an overridden instance method? | It selects the implementation based on the object’s actual class. | `02-java-object-oriented-programming.md` |
| 18 | What is method overloading? | Declaring methods with the same name but different parameter lists; the compiler selects the applicable overload. | `02-java-object-oriented-programming.md` |
| 19 | What is method overriding? | A subclass provides a compatible implementation of an inherited instance method. | `02-java-object-oriented-programming.md` |
| 20 | What is one difference between an abstract class and an interface? | An abstract class can contain both implemented and abstract methods; an interface specifies a contract for implementing classes. | `02-java-object-oriented-programming.md` |
| 21 | What does `static` mean for a field? | The field belongs to the class rather than to each individual instance. | `02-java-object-oriented-programming.md` |
| 22 | Can a final object reference point to another object later? | No, but the object currently referenced may still be mutable. | `02-java-object-oriented-programming.md` |
| 23 | What must a method do with a checked exception? | It must catch the exception or declare it with `throws`. | `03-java-exceptions-and-collections.md` |
| 24 | What is the difference between `throw` and `throws`? | `throw` raises an exception object; `throws` declares exceptions that may propagate from a method. | `03-java-exceptions-and-collections.md` |
| 25 | What is a `finally` block commonly used for? | Cleanup when control leaves a try/catch flow, including when an exception is thrown. | `03-java-exceptions-and-collections.md` |
| 26 | How does an array differ from an ArrayList? | An array has a fixed length; an ArrayList is resizable and supports indexed access. | `03-java-exceptions-and-collections.md` |
| 27 | Which collection interface models ordered elements and permits duplicates? | `List`. | `03-java-exceptions-and-collections.md` |
| 28 | Which collection interface models unique elements? | `Set`. | `03-java-exceptions-and-collections.md` |
| 29 | What happens when a value is inserted into a HashMap using an existing equal key? | The value associated with that key is replaced; the map does not keep a second entry for an equal key. | `03-java-exceptions-and-collections.md` |
| 30 | What rule should `equals` and `hashCode` obey for hash-based collections? | If two objects are equal, they must have equal hash codes. | `03-java-exceptions-and-collections.md` |
| 31 | What methods does an Iterator use to traverse a collection? | `hasNext()` checks for another element and `next()` returns it. | `03-java-exceptions-and-collections.md` |
| 32 | What do Java generics provide? | Compile-time type checking for values used with generic types and fewer casts when retrieving values. | `04-java5-language-features.md` |
| 33 | What is type erasure? | Java’s implementation of generics removes most generic type-parameter information during compilation. | `04-java5-language-features.md` |
| 34 | Can `List<int>` be used as a Java generic type argument? | No. Generic type arguments must be reference types; use a wrapper such as `Integer`. | `04-java5-language-features.md` |
| 35 | Why is an enum more type-safe than a set of integer constants? | Its named values belong to a distinct enum type with a fixed set of instances. | `04-java5-language-features.md` |
| 36 | What does `@Override` do? | It marks a method intended to override an inherited method, allowing the compiler to detect some mistakes. | `04-java5-language-features.md` |
| 37 | What is autoboxing? | Automatic conversion between a primitive and its corresponding wrapper when the context requires it. | `04-java5-language-features.md` |
| 38 | What happens when Java unboxes a null Integer? | It throws `NullPointerException`. | `04-java5-language-features.md` |
| 39 | What can Java 5’s enhanced for loop iterate over? | Arrays and objects that implement `Iterable`. | `04-java5-language-features.md` |
| 40 | Where must a varargs parameter appear in a method declaration? | It must be the final parameter; inside the method it is treated as an array. | `04-java5-language-features.md` |
| 41 | What does a static import allow? | It lets source code refer to accessible static members without qualifying them with the declaring type. | `04-java5-language-features.md` |
| 42 | What is `printf` used for in Java? | Formatted output using a format string and conversions such as `%s` and `%d`. | `04-java5-language-features.md` |
| 43 | Which Java package introduced higher-level concurrency utilities in Java 5? | `java.util.concurrent`. | `05-java5-concurrency.md` |
| 44 | What does ExecutorService add to task execution? | It supports submitting tasks and managing the executor’s lifecycle. | `05-java5-concurrency.md` |
| 45 | What does a Future represent? | A result that may become available later; it can be checked, cancelled, or retrieved with `get()`. | `05-java5-concurrency.md` |
| 46 | What was the default maximum heap size in the Java 5 runtime? | Not stated in the indexed source documents; EKA should abstain. | None — unanswerable |
| 47 | Which garbage collector was enabled by default in every Java 5 vendor distribution? | Not stated in the indexed source documents; EKA should abstain. | None — unanswerable |
| 48 | What exact throughput percentage did Java 5’s ConcurrentHashMap guarantee? | No such performance guarantee is stated in the indexed source documents; EKA should abstain. | None — unanswerable |
| 49 | Which company-specific license governed every Java 5 distribution? | Not stated in the indexed source documents; EKA should abstain. | None — unanswerable |
| 50 | What was the precise startup time of Java 5 on a 2 GHz processor? | Not stated in the indexed source documents; EKA should abstain. | None — unanswerable |
