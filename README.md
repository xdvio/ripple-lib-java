The Ripple Java Library
===============

[![Build Status](https://travis-ci.org/sublimator/ripple-lib-java.png?branch=master)](https://travis-ci.org/sublimator/ripple-lib-java) [![CircleCI](https://circleci.com/gh/sublimator/ripple-lib-java.svg?style=svg)](https://circleci.com/gh/sublimator/ripple-lib-java)
Java version of ripple-lib (alpha work in progress)

Currently looking for java/android developers to help evolve this library/api.

Please open an issue with any questions/suggestions.

The goal for this is to be an implementation of ripple-types, binary
serialization, with a websocket library agnostic implementation of a client,
which will track changes to accounts balances/offers/trusts, that can be used as
the basis for various clients/wallets.

### Current status
  
  - Binary serialization/parsing/shamap
  - Crude implementation of a high level client
    - Single threaded
    - High level helper classes
      - AccountTxPager (wraps account_tx)
      - PaymentFlow (wraps path_find)
    - Automatic transaction resubmission
      - Resubmits transactions in manner resilient to poor network conditions
  - Api client choice of WebSocket transport
  - Test suite for core types
  - Signing / Verification
  - KeyPair creation

### Docs

  - [ShaMap](ripple-core/src/main/java/com/ripple/core/types/shamap/README.md)
  - [Serialized Types](ripple-core/README.md)
  - [Transaction Manager](ripple-client/src/main/java/com/ripple/client/transactions/README.md)

### TODO
  - More helper classes
  - General cleanup/stabilisation of code / api surface
  - Documentation
  - Complete test coverage

### Examples

  - See in ripple-examples/ folder
