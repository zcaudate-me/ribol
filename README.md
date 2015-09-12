# ribol

`ribol` is a conditional restart library for clojure and clojurescript inspired by `errorkit`

[![Build Status](https://travis-ci.org/zcaudate/ribol.png?branch=master)](https://travis-ci.org/zcaudate/ribol)

## DEPRECATION NOTICE

[ribol](https://github.com/zcaudate/ribol) has been merged into [hara](https://github.com/zcaudate/hara). Please see [updated docs](http://docs.caudate.me/hara/hara-event.html) for the most recent version.

### Installation:

In project.clj, add to dependencies:

     [im.chit/ribol "0.4.1"]

### Documentation

See main site at:

http://docs.caudate.me/ribol/

To generate this document for offline use: 

  1. Clone this repository

   > git clone https://github.com/zcaudate/ribol.git

  2. Install [lein-midje-doc](http://docs.caudate.me/lein-midje-doc). 
  
  3. Create `docs` folder
      > mkdir docs

  4. Run in project folder
  
      > lein midje-doc

The output will be generated in `docs/index.html`


## Changes:

#### v0.4.1
- Bugfix

#### v0.4.0
- Clojurescript support!
- TODO: Docs for clojurescript

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
