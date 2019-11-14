# COMBO
Combo is a library for Constrained Online Multi-variate Bandit Optimization (COMBO), otherwise known as combinatorial multi-armed bandits. It is used to optimize software with user data in a production environment. It supports multiple methods, such as generalized linear model bandits, random forest bandits, neural network bandits, and genetic algorithms. Using Combo, each user can recieve their own configuration with potentially thousands of variables in milliseconds. As the results of each users experience with their configuration is recorded the resulting configurations will be better and better. Depending on the method employed this can require some statistical modeling. Combo is written in Kotlin with Java/JavaScript interoperability in mind, thus it can be used from both Java and JavaScript.

Using it requires three steps: 

1. Create a [model](https://en.wikipedia.org/wiki/Feature_model).
2. Map the model to your actual software features.
3. Create a Combo optimizer.

## Feature model

A feature model is a tree that describes the variables in the optimization problem. Lets start of with a simple example, which is intended to be used to display a top-list of the most important media categories on a web site.

The optimal configuration will be automatically calculated based on how well each category performs in terms of eg. sales or click data.

```kotlin
fun main() {

    val myModel = Model.root {

        // Context variables
        int("DisplayWidth", 640, 1920)
        val customerType = nominal("CustomerType", "Child", "Company", "Person")

        // The category tree is encoded directly

        //Games can be absent
        val games = optionalMultiple("Games", "Shooter", "Platform", "Sports", "Action", "Adventure", "Strategy")

        // Movies is a sub-category with multiple sub-options
        val movies = model("Movies") {
            val horror = optionalMultiple("Horror", "Slasher", "Splatter", "Zombie")
            optionalMultiple("Action", "Thriller", "Martial arts", "Crime")
            optionalMultiple("Sci-fi", "Supernatural", "Super heroes", "Fantasy")

            // This adds a constraint that ensures that whenever CustomerType is Child then any of the 
            // horror categories are hidden.
            val child = customerType.option("Child")
            impose { child equivalent !horror }
        }

        // ... add more categories as needed

        // Add a hard constraint that the number of categories must be between 2 and 5.
        // This could have been a dynamic parameter with the number of movie genres instead.
        val categoryVariables = games.values + movies.scope.variables.asSequence()
                .flatMap { (it as Multiple<*>).values }.toList().toTypedArray()
        impose { atLeast(2, *categoryVariables) }
        impose { atMost(5, *categoryVariables) }
    }
}
```

## Optimizer

Creating an optimizer is simple, creating the _right_ optimizer can be a challenge. There are several hyper-parameters that can be tuned for better performance. The random forest bandit is recommended to start with because it is quite robust to bad tuning.

```kotlin
// Using the feature model "myModel" from above
// This optimizer will maximize binomial data (success/failures).
val optimizer = ModelBandit.randomForestBandit(myModel, ThompsonSampling(BinomialVariance))
```

Using the optimizer then is as simple as this:

```kotlin
val assignment = optimizer.chooseOrThrow()
// The values can be queried like so:
assignment.getBoolean("Horror")
// It can be used as an ordinary map from String to value as such (but then the structure is lost).
val map = assignment.toMap()
```

To get a "personalized" do this:

```kotlin
val assignment = optimizer.chooseOrThrow(myModel["DisplayWidth", 1920], myModel["CustomerType", "Child"])
```

## Support
Feel free to contact me, the author, at rasmus at cs.lth.se in case of trouble or need of help. I might require you to give an experience report though :)
