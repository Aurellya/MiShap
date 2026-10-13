# Why I built MiShap

<img src="./MiShap.png" width="150" alt="MiShap Icon">

## The question that started it

What happens when a customer is charged, but the "payment OK" reply never reaches the order service?

The order times out and cancels. The money is gone, and nobody is told to give it back.

Nothing in that story is a typo or a crashed server. Every service did exactly what its code says. The bug is in the **design**, in how the services talk to each other when something goes wrong. And you usually find it in production.

## Why this project

Before my Master's, I mostly built CRUD web applications. I was familiar with implementing features, connecting databases, and making applications work along their expected paths.

During my Master's in Computer Science (Software Technology) at the University of Twente I learned how to describe a system precisely, reason about its possible behavior, check its properties automatically, and generate code from a model.

I wanted to combine these ideas in one practical project that solve real problems. So MiShap does 3 things:

1. **Describe** a microservice workflow in a small DSL.
2. **Check** it by exploring every order of events, including lost messages, duplicated messages, and timeouts. If a rule breaks, it prints the shortest trace that breaks it.
3. **Generate** Spring Boot services from the same model, and test them against the bugs the checker found.

The goal is to catch design mistakes before they become expensive to fix.

The three demo specs. v1 is obviously broken. v2 *looks* fixed, but the checker finds a sneaky race where the refund overtakes the charge. v3 finally holds.


## How my courses fit in

MiShap is not a direct implementation of one course assignment. It combines ideas from several courses, each contributing a different part of the project.

| Course                                           | What I used it for in MiShap                                                                                            |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **Model-Driven Engineering**                     | Using textual models DSL and generating Spring Boot code from a model (model can be more than documentation)                                                  |
| **Software Evolution**                           | the ANTLR parser, a skill from building the Baby COBOL compiler/interpreter|
| **Service-Oriented Architecture**                |Spring Boot microservices that communicate through REST |
| **Modelling and Analysis of Concurrent Systems** | Treating the system as a transition system, interleaving of messages, state-space exploration, and safety properties |
| **Program Verification**                         | Thinking in invariants and preconditions, and what a check does and doesn't prove                                   |
| **System Validation**                            | Properties such as "never charged twice", and counterexamples that show how they fail                                |
| **System Testing and Risk Assessment**           | Testing failure scenarios such as duplicated messages and timeouts                                                   |
| **Software Management**                          | Developing the project incrementally, from the explorer to code generation and testing.                              |
| **Ontology-Driven Conceptual Modelling**         | Not used yet. Checking that service data models are consistent is a possible extension                               |


## Note

"No mishap found" doesn't mean "correct". It means nothing broke within the bounds and the failures I modeled. MiShap catches design bugs early, but it's not a proof.

## Where to look

- [README](../README.md): how to build and run it
- [Design note](design-note.md): semantics, assumptions, and limits
- [Sequence diagrams](sequence.md): the bugs as pictures