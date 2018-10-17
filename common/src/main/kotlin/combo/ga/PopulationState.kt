package combo.ga

import combo.math.DescriptiveStatistic
import combo.sat.MutableLabeling

class PopulationState(
        val population: Array<MutableLabeling>,
        val scores: DoubleArray,
        val age: IntArray,
        var oldest: Int,
        var youngest: Int,
        var scoreStatistic: DescriptiveStatistic)
