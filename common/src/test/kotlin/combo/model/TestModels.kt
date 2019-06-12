package combo.model

import combo.math.IntPermutation
import combo.sat.Problem
import combo.sat.constraints.Conjunction
import combo.sat.constraints.Relation
import combo.util.collectionOf
import kotlin.random.Random

object TestModels {


    val SAT_PROBLEMS: List<Problem> by lazy {
        arrayOf(MODEL1, MODEL2, MODEL3, MODEL4, MODEL5, MODEL6, MODEL7)
                .map { m -> m.problem }
    }
    val UNSAT_PROBLEMS: List<Problem> by lazy {
        arrayOf(UNSAT1, UNSAT2, UNSAT3, UNSAT4)
                .map { m -> m.problem }
    }
    val LARGE_SAT_PROBLEMS: List<Problem>  by lazy {
        arrayOf(LARGE1, LARGE2, LARGE3, LARGE4)
                .map { m -> m.problem }
    }
    val NUMERIC_PROBLEMS: List<Problem>  by lazy {
        arrayOf(NUMERIC1, NUMERIC2, NUMERIC3)
                .map { m -> m.problem }
    }
    val PB_PROBLEMS: List<Problem>  by lazy {
        arrayOf(CSP1).map { m -> m.problem }
    }
    val TINY_PROBLEMS: List<Problem> by lazy {
        listOf(MODEL2.problem, MODEL3.problem, MODEL5.problem)
    }

    val MODEL1 by lazy {
        Model.model("All Round Basic") {
            bool("f1")
            model("f2") {
                val m1 = multiple("m1", 4, 5, 6)
                constraint {
                    m1.option(4) implies m1.option(6)
                }
                model(optionalAlternative("alt1", "a", "c")) {
                    val f3 = bool("f3")
                    val alt2 = optionalAlternative("alt2", "a", "d")
                    constraint {
                        f3 implies alt2.option("d")
                    }
                }
            }
            flag("f4", true)
        }
    }

    val MODEL2 by lazy {
        Model.model("All Unit Features") {
            val f2 = bool("f2")
            constraint {
                this@model equivalent f2
            }
            model("f1") {
                optionalAlternative("alt1", "a", "c")
                optionalMultiple("or1", 4, 5, 6)
                constraint { !(f2 and "or1") }
                constraint { !(f2 and "alt1") }
            }
            constraint { "f2" implies "f1" }
            bool("f3")
            constraint { !(f2 and "f3") }
        }
    }

    val MODEL3 by lazy {
        Model.model("Unit Options in Select") {
            val alt1 = alternative("alt1", "a", "b", "c")
            val mult1 = multiple("mult1", "a", "b", "c")
            constraint { mult1.option("b") }
            constraint { alt1.option("c") }
        }
    }

    val MODEL4 by lazy {
        Model.model("All Standard Features") {
            bool()
            optionalAlternative("a1", 1, 2, 3)
            optionalMultiple("m1", values = *arrayOf(1, 2, 3))
            alternative("a2", values = *arrayOf(1, 2, 3))
            multiple("m2", values = *arrayOf(1, 2, 3))
        }
    }

    val MODEL5 by lazy {
        val subModel1 = Model.builder("r1") {
            model("sub1") {
                model("sub2") {
                    model("sub3") {
                        bool("sub4")
                    }
                }
            }
        }
        val subModel2 = Model.builder("r2") {
            bool("f1")
        }
        Model.model("Multiple Sub Models") {
            bool("b1")
            bool("b2")
            bool("b3")
            addModel(subModel1)
            addModel(subModel2)
            constraint { index["f1"] or index["sub4"] }
        }
    }

    val MODEL6 by lazy {
        Model.model("All Cardinality Options except NE") {
            val flags = Array(12) { bool("$it") }
            constraint { atMost(4, flags.sliceArray(0 until 10)) }
            constraint { atLeast(4, flags) }
            constraint { exactly(2, arrayOf(flags[0], flags[1], flags[2])) }
            constraint { excludes(flags[2], flags[3]) }
            constraint { cardinality(2, Relation.GT, flags) }
            constraint { cardinality(8, Relation.LT, flags) }
        }
    }

    val MODEL7 by lazy {
        Model.model("Diverging sub-models") {
            val top = index
            model("sub1") {
                bool("f1")
            }
            model("sub2") {
                model(top["f1"], "r") {
                    flag("f2", 1)
                }
            }
        }
    }

    val UNSAT1 by lazy {
        Model.model("Simple 2-Unsat") {
            val f1 = bool()
            val f2 = bool()
            constraint { f1 or f2 }
            constraint { f1 or !f2 }
            constraint { !f1 or f2 }
            constraint { !f1 or !f2 }
        }
    }

    val UNSAT2 by lazy {
        Model.model("Simple 3-Unsat") {
            val f1 = bool()
            val f2 = bool()
            val f3 = bool()
            constraint { f1 or f2 or f3 }
            constraint { f1 or f2 or !f3 }
            constraint { f1 or !f2 or f3 }
            constraint { f1 or !f2 or !f3 }
            constraint { !f1 or f2 or f3 }
            constraint { !f1 or f2 or !f3 }
            constraint { !f1 or !f2 or f3 }
            constraint { !f1 or !f2 or !f3 }
        }
    }

    val UNSAT3 by lazy {
        Model.model("Unsat by Cardinality") {
            val a = optionalAlternative(values = *(1..10).toList().toTypedArray())
            constraint { atLeast(2, arrayOf(a.option(1), a.option(2), a.option(3))) }
        }
    }

    val UNSAT4 by lazy {
        Model.model("Modified Simple 2-Unsat") {
            val a = bool()
            val b = bool()
            constraint { a xor b }
            model(b) {
                val c = bool()
                constraint { a equivalent c }
            }
            constraint { !b or a }
        }
    }
    val LARGE1 by lazy {
        Model.model("Just Alternatives") {
            optionalAlternative(values = *Array(80) { it })
            multiple(values = *Array(80) { it })
            alternative(values = *Array(80) { it })
            optionalMultiple(values = *Array(80) { it })
        }
    }

    val LARGE2 by lazy {
        Model.model("Hierarchical Category Model") {
            val top = this
            val topCategories = Array(5) { i ->
                model("Cat $i") {
                    val leafCategories = Array(10) { j ->
                        optionalMultiple("Cat $i $j", Array(10) { 1 + it })
                    }
                    constraint {
                        top reifiedEquivalent disjunction(*leafCategories)
                    }
                }
            }
            constraint { top reifiedEquivalent disjunction(*topCategories) }
            constraint {
                atMost(5, index.asSequence()
                        .filter { it is Multiple<*> }
                        .flatMap { (it as Multiple<*>).options().asSequence() }
                        .toList().toTypedArray())
            }
        }
    }

    val LARGE3 by lazy {
        Model.model("Flag chain") {
            var next = this
            for (k in 1..500)
                next = next.model("$k") {}
        }
    }

    val LARGE4 by lazy {
        Model.model("Random Disjunctions") {
            val flags = Array(500) { bool("$it") }
            val rng = Random(0)
            for (i in 1..1000) {
                constraint {
                    disjunction(*IntPermutation(flags.size, rng).asSequence()
                            .take(rng.nextInt(8) + 2)
                            .map { flags[it] }
                            .map { if (rng.nextBoolean()) it.not() else it }
                            .toList().toTypedArray())
                }
            }
        }
    }

    val NUMERIC1 by lazy {
        Model.model("Ints in hierarchy") {
            optionalInt("int1", -1, 1)
            int("int2", 0, 1)
            optionalInt("int3", 0, Int.MAX_VALUE)
            model {
                optionalInt("int4")
                bool()
                int("int5")
            }
        }
    }

    val NUMERIC2 by lazy {
        Model.model("Ints with constraints") {
            optionalInt()
            optionalInt()
            optionalInt()
            addConstraint(Conjunction(collectionOf(4, -42, 99)))
        }
    }

    val NUMERIC3 by lazy {
        Model.model("All kinds of numeric variables") {
            optionalInt("opt1", min = -100, max = 100)
            int("int1")
            optionalFloat("opt2", min = -0.1f, max = 1.0f)
            float(min = -0.1f, max = 1.0f)
            optionalBits(nbrBits = 10)
            bits(nbrBits = 100)
            model {
                optionalInt(min = -100, max = 100)
                int()
                optionalFloat(min = -0.1f, max = 1.0f)
                float(min = -0.1f, max = 1.0f)
                optionalBits(nbrBits = 10)
                bits(nbrBits = 100)
            }
        }
    }

    val CSP1 by lazy {
        Model.model("All kinds of PB constraints") {
            val f1 = bool()
            val f2 = bool()
            val f3 = bool()
            val f4 = bool()
            constraint {
                f1 reifiedImplies conjunction(f2, f3)
            }
            constraint {
                f4 reifiedImplies disjunction(f1, f2, f3)
            }
            constraint {
                cardinality(3, Relation.LT, arrayOf(!f1, f2, f3, !f4))
            }
            constraint {
                linear(2, Relation.GT, intArrayOf(1, 2, 3, 4), arrayOf(f1, f2, f3, f4))
            }
        }
    }
}