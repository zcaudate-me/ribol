# ribol

`ribol` is a conditional restart library for clojure inspired by `errorkit`

### Installation:

In project.clj, add to dependencies:

     [im.chit/ribol "0.3.3"]

### Documentation

See main site at:

http://z.caudate.me/ribol/

To generate this document for offline use: 

 1. Clone this repository

   > git clone https://github.com/zcaudate/ribol.git

 2. Install [lein-midje-doc](http://z.caudate.me/lein-midje-doc). 

 3. Run in project folder

   > lein midje-doc

The output will be generated in `<root>/index.html`


## Changes:

#### v0.3.3
- Allow `catch` clauses in `manage`, `anticipate`, `raise-on` and `raise-on-all`
- Supports more bindings for issues (hashmap, symbol)
- Checkers support sets and underscores

#### v0.3.2
- supports an additional :origin key for keeping the original stacktrace information intact in `raise-on` and `raise-on-all`

#### v0.3.1
- supports `finally` clauses in `manage`, `anticipate`, `raise-on` and `raise-on-all`


## License

Copyright © 2013 Chris Zheng

Distributed under the MIT License
