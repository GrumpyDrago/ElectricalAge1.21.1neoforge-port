@file:Suppress("NAME_SHADOWING")
package mods.eln.misc

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceProcess
import net.minecraft.world.item.ItemStack
import mods.eln.craft.Smelting
import java.util.*
import kotlin.collections.ArrayList

class RecipesList {
    val recipes = ArrayList<Recipe>()
    val machines = ArrayList<ItemStack>()
    fun addRecipe(recipe: Recipe) {
        recipes.add(recipe)
        recipe.setMachineList(machines)
    }

    fun addMachine(machine: ItemStack) {
        machines.add(machine)
    }

    fun getRecipe(input: ItemStack?): Recipe? {
        for (r in recipes) {
            if (r.canBeCraftedBy(input)) return r
        }
        return null
    }

    /** Everything that the machines make this of, as many as it takes. */
    fun getInputs(recipe: Recipe): List<ItemStack> =
        OreDict.likelyInputs(recipe.input.item)
            .map { it.defaultInstance.copyWithCount(recipe.input.count) }
            .filter { getRecipe(it) === recipe }

    fun getRecipeFromOutput(output: ItemStack?): ArrayList<Recipe> {
        if (output == null) return ArrayList()
        val list = ArrayList<Recipe>()
        for (r in recipes) {
            for (stack in r.outputCopy) {
                if (stack != null) {
                    if (Utils.areSame(stack, output)) {
                        list.add(r)
                        break
                    }
                }
            }
        }
        return list
    }

    companion object {
        val listOfList = ArrayList<RecipesList>()
        @JvmStatic
        fun getGlobalRecipeWithOutput(output: ItemStack): ArrayList<Recipe> {
            var output = output
            output = output.copy()
            output.count = 1
            val list = ArrayList<Recipe>()
            for (recipesList in listOfList) {
                list.addAll(recipesList.getRecipeFromOutput(output))
            }
            for ((input, result) in Smelting.all()) {
                if (Utils.areSame(output, result)) {
                    val recipe = Recipe(input.copy(), output, ElectricalFurnaceProcess.energyNeededPerSmelt)
                    recipe.setMachineList(Eln.instance.furnaceList)
                    list.add(recipe)
                }
            }
            return list
        }

        @JvmStatic
        fun getGlobalRecipeWithInput(input: ItemStack): ArrayList<Recipe> {
            var input = input
            input = input.copy()
            input.count = 64
            val list = ArrayList<Recipe>()
            for (recipesList in listOfList) {
                val r = recipesList.getRecipe(input)
                if (r != null) list.add(r)
            }
            val smeltResult = Smelting.result(input)
            if (smeltResult != null) {
                val recipe = Recipe(input.copyWithCount(1), smeltResult, ElectricalFurnaceProcess.energyNeededPerSmelt)
                recipe.machineList.addAll(Eln.instance.furnaceList)
                list.add(recipe)
            }
            return list
        }
    }

    init {
        listOfList.add(this)
    }
}
