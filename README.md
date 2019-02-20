# COMBO
Combo is a library for Constrained Online Multi-variate Bandit Optimization (COMBO). It is used to optimize software with user data in a production environment. It supports multiple [online optimization](https://en.wikipedia.org/wiki/Online_optimization) methods, such as generalized linear bandits, decision tree bandits, and genetic algorithms. Using Combo, each user can recieve their own configuration with potentially thousands of variables in milliseconds. As the results of each users experience with their configuration is recorded the resulting configurations will be better and better. Depending on the method employed this can require some statistical modeling. Combo is written in Kotlin with Java/JavaScript interoperability in mind, thus it can be used from both Java and JavaScript.

Using it requires three steps: 

1. Create a [feature model](https://en.wikipedia.org/wiki/Feature_model).
2. Map the model to your actual software features.
3. Create a Combo optimizer.

## Feature model

A feature model is a tree that describes the variables in the optimization problem. Lets start of with a simple example, which is intended to be used to display a top-list of the most important categories on a web site.

```kotlin
import combo.model.Model

fun main() {
    val model = Model.builder()
            .optional(Model.builder("Movies")
                    .optional("Drama")
                    .optional("Sci-fi"))
                    .optional("Comedy"))
                    .optional("Horror"))
            .optional("Games")
            .build()
}
```

The flag function creates a feature with boolean on/off values. In this model the sub-features e.g. `Drama` and `Sci-fi` can only be true if their parent `Movies` is true. In logic terms, this is the relation: Drama => Movies AND Sci-fi => Movies. There will be no assignments where this constraint is not uphold. Combo supports some additional constraints (formally pseudo-boolean constraints). For example:

```kotlin
    val model = Model.builder()
            //...
            // This ensures that only one of moviesDrama and moviesSciFi will be true simultaneously
            // A top-k category list in this way is a simple matter of adding an atMost constraint with each leaf-node
            // in the feature model, with degree = k
            .constrained(atMost("Drama", "Sci-fi", "Comedy", "Horror", degree = k))
            .build()
```

Combo supports additional types of features. The previous features that were added with just a name were all instances of the Flag feature. The additional feature types are Alternative and Multiple. Alternative encodes an excludes constraint such that at most one of the given alternatives can be selected at once. Multiple is similar but without the exclusive constraint so that muliple options can be chosen at once. Alternative would be a better option to build the above model: 

```kotlin
    val model = Model.builder()
            .optional(alternative("Drama", "Sci-fi", "Comedy", "Horror"))
            .optional("Games")
            .build()
```

### Layout optimization

## Optimizer

Creating an optimizer is simple, creating the _right_ optimizer can be a challenge.

### Combinatorial bandit
```kotlin
// Using the feature model "model" from before
val optimizer = Optimizer.combinatorialBandit(model)
```

This will enumerate each possible solution and keep track of the performance of each possible solution individually. For a small model like the basic model above (with only 8 possible solutions) this might be good enough or even the best solution. However, this scales exponentially in the number of variables so for large models this approach is not feasible.

Note that the combinatorial bandit is implemented using Thompson sampling, thus you have to specify which posterior distribution the rewards are modeled with. By default this is the normal distribution. For binary rewards (success/failure) you can use:

```kotlin
val optimizer = Optimizer.combinatorialBandit(model, posterior = binomial())
```

### Genetic algorithm
### Decision tree bandit
### Generalized linear model bandit

## Why not A/B Testing?

The limitations of A/B testing as an optimization method is as follows.

1. Local optimization. Performing multiple iterations of A/B testing is equivalent to [hill climbing](https://en.wikipedia.org/wiki/Hill_climbing). Doing so might lead to a local optima. While multi-variate testing can alleviate this by also considering interactions between variables, doing so lead to the next problem.
2. Scale. Classical experiments takes a long time to complete. Also, multi-variate testing requires a lot of storage space, in the case of full factorial experiments.
3. Personalization. A/B testing does not directly deal with personalization, meaning that all users will get the same version of software. If the site is split into multiple segments then the experiment data cannot be shared between the different segments.

Obviously this does not mean that A/B testing is useless or should not be done. An implementaiton of an optimization system with Combo should definitely be A/B tested to verify that it works as intended.

# Support
Feel free to contact me, the author, at rasmus at cs.lth.se in case of trouble or need of help. I might require you to give an experience report though :)
