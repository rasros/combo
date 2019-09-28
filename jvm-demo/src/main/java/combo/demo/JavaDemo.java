package combo.demo;

import combo.bandit.univariate.NormalPosterior;
import combo.bandit.univariate.ThompsonSampling;
import combo.model.Model;
import combo.model.ModelBandit;
import combo.model.ModelSolver;

import java.util.List;

public class JavaDemo {
    public static void main(String[] args) {

        // Define simple model with some variables, one explicit constraint, and one child model
        var model = new Model.Builder("Root")
                .add().bool("boolean variable")
                .add().flag("wrapper around constant", 10)
                .add().nominal("only one of", 1, 2, 3, 4, 5)
                .add().optionalMultiple("any number of", "a", "b", "c")
                .add().atMost(1, "boolean variable", "any number of")
                .addModel(new Model.Builder("Child model")
                        .add().bool("Child model variable")
                        .build())
                .build();

        // Solver can generate random assignments and permutations with the asSequence method
        var solver = ModelSolver.localSearch(model);

        // Assignment is used like so:
        var randomAssignment = solver.witnessOrThrow();
        System.out.println(randomAssignment);
        System.out.println(randomAssignment.getBoolean("boolean variable"));
        // Can also get from child models if unambiguous
        System.out.println(randomAssignment.getBoolean("Child model variable"));
        // Otherwise can do more qualified get like so
        System.out.println(randomAssignment.subAssignment("Child model").getBoolean("Child model variable"));
        // Get variables of other types
        System.out.println("Optional multiple: " + randomAssignment.<List<String>>get("any number of"));
        System.out.println("Mandatory nominal: " + randomAssignment.getInt("only one of"));
        System.out.println();

        // This bandit is the most basic, it just generates a bunch of solutions and does a multi-armed bandit
        // competition between them
        var bandit = ModelBandit.listBandit(model, new ThompsonSampling<>(NormalPosterior.INSTANCE));

        // The bandit is used like so:
        var chosenAssignment = bandit.chooseOrThrow();
        bandit.update(chosenAssignment, -2.0f);

        // We can also generate an assignment with given context
        var contextAssignment = bandit.chooseOrThrow(model.get("only one of", 5));
        System.out.println(contextAssignment);
        bandit.update(contextAssignment, 1.0f);
        // This will always be 5
        System.out.println("Should be 5: " + contextAssignment.getInt("only one of"));
    }
}
