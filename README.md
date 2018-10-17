# COMBO

Combo is a library for Constrained Online Multi-variate Bandit Optimization (COMBO). It is used to optimize software with user data in a production environment. It supports multiple optimization methods, such as generalized linear bandits, decision tree bandits, and genetic algorithms. Combo is written in Kotlin with Java/JavaScript interoperability in mind, thus it can be used from both Java and JavaScript.

Using it requires three steps: 

1. Create a [feature model](https://en.wikipedia.org/wiki/Feature_model).
2. Map the model to your actual software features.
3. Create a Combo optimizer.

## Feature model

A feature model is a tree that describes the variables in the optimization problem. Lets start of with a simple example, which is intended to be used to display a top-5 list of the most important categories on a web site.

```kotlin
import combo.model.Model
import combo.model.atMost
import combo.model.flag

fun main(args: Array<String>) {
    val movies = flag("Movies")
    val moviesDrama = flag("Drama")
    val moviesSciFi = flag("Sci-fi")
    val games = flag("Games")

    val model = Model.builder("Top categories")
            .optional(Model.builder(movies)
                    .optional(moviesDrama)
                    .optional(moviesSciFi))
            .optional(games)
            .constrained(atMost(moviesDrama, moviesSciFi, degree = 1))
            .build()
}
```

## Optimizer

## Why not A/B Testing?

The limitations of A/B testing as an optimization method is as follows.

1. Local optimization. Performing multiple iterations of A/B testing is equivalent to [hill climbing](https://en.wikipedia.org/wiki/Hill_climbing). Doing so might lead to a local optima. While multi-variate testing can alleviate this by also considering interactions between variables, doing so lead to the next problem.
2. Scale. Classical experiments takes a long time to complete. Also, multi-variate testing requires a lot of storage space, in the case of full factorial experiments.
3. Personalization. A/B testing does not directly deal with personalization, meaning that all users will get the same version of software. If the site is split into multiple segments then the experiment data cannot be shared between the different segments.

Obviously this does not mean that A/B testing is useless or should not be done. An implementaiton of an optimization system with Combo should definitely be A/B tested to verify that it works as intended.
