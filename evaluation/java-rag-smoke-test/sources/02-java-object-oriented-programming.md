# Java object-oriented programming

## Classes, objects, and constructors

A class defines a type and can declare fields, constructors, and methods. An object is an instance of a class. A constructor initializes a newly created object; its name matches the class name and it has no return type. If a class declares no constructor, Java provides a default no-argument constructor.

## Encapsulation and access

Encapsulation keeps an object's state and implementation behind a defined interface. A common pattern is to make fields `private` and expose controlled operations through methods.

Java access levels are `public`, `protected`, package-private (no modifier), and `private`. `private` members are accessible only within their declaring class. Package-private members are accessible within the same package. `protected` members are accessible within the same package and to subclasses under Java's protected-access rules. `public` members are broadly accessible wherever the containing type is accessible.

## Inheritance and polymorphism

A class can extend one superclass with `extends`, inheriting accessible behavior and potentially overriding methods. Java classes have single class inheritance: a class has one direct superclass. A class can implement multiple interfaces.

Polymorphism lets code use a superclass or interface reference to refer to an object of a concrete subtype. For an overridden instance method, the implementation selected at runtime is based on the object's actual class.

Method overloading means declaring methods with the same name but different parameter lists. The compiler selects an overload using the method arguments and static types. Method overriding means a subclass provides a compatible implementation of an inherited instance method; runtime dispatch selects the override for the object's actual class.

## Abstract classes and interfaces

An abstract class cannot be instantiated directly and may contain both implemented methods and abstract methods. A concrete subclass must implement inherited abstract methods unless it too is abstract.

An interface specifies a contract that implementing classes agree to provide. A class uses `implements` to implement an interface and may implement more than one. Interfaces allow unrelated classes to support the same API without sharing a superclass.

## Static and final

A `static` field or method belongs to the class rather than to an individual instance. A `final` variable can be assigned only once; a `final` method cannot be overridden; and a `final` class cannot be subclassed. A final reference cannot point to a different object after assignment, although the referenced object's state may still be mutable.
