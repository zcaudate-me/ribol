# ribol

`ribol` is a conditional restart library for clojure inspired by `errorkit`, having a more readable syntax, and designed with the base `clojure.lang.ExceptionInfo` type in mind.

### Installation:

In project.clj, add to dependencies:

     [im.chit/ribol "0.1.5"]

### Rational:
In the author's experience, there are two forms of 'exceptions' that a programmer will encounter:

 1. Programming Mistakes - These are a result of logic and reasoning errors and should not occur in normal operation and should be eliminated. The best strategy for dealing with this is for the program to write unit tests and fail early with a clear message to the programmer what the error is.
    - Null pointers
    - Wrong inputs to functions

 2. Exceptions due to Circumstances - These are circumstancial and should be considered part of the normal operation of the program.
    - A database connection going down
    - A file not found
    - User input not valid

The common method of `try` and `catch` is not really needed when dealing with the first type of exceptions and a little too weak when dealing with the second. There are numerous resources that explain why this is the case but the net effect is that in order to mitigate these type 2 exceptions, there requires alot of defensive programming that makes for spegetti code.

There are two other conditional restart libraries for clojure - `errorkit` and `swell` the reason for writing `ribol` was to have an updated `errorkit` to work with `ex-info` in `clojure.core`, along with more understandable syntax. `swell` was written specifically to work with the `slingshot` try+/catch+ packages and I thought that the two together carried too much baggage. `ribol` is quite lightweight and has no dependencies.

## Usage


## License

Copyright © 2013 Chris Zheng

Distributed under the MIT License
