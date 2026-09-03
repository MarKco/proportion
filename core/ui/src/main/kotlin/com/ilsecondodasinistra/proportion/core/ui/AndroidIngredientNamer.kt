package com.ilsecondodasinistra.proportion.core.ui

import android.content.Context
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Resolves built-in ingredient names from `strings.xml` via a dynamic lookup rather than a
 * hand-written `when`: with 400-600 entries, a `when` block would blow past detekt's `LongMethod`
 * threshold (80 lines). [AndroidIngredientNamerTest] plus a resource-consistency test guard the
 * one risk this trades in — a seed key with no matching string resource — at build time.
 */
class AndroidIngredientNamer @Inject constructor(
    @ApplicationContext private val context: Context,
) : BuiltInIngredientNamer {

    override fun name(key: String): String {
        val resId = context.resources.getIdentifier("ingredient_$key", "string", context.packageName)
        check(resId != 0) { "no ingredient_$key string resource" }
        return context.getString(resId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IngredientNamerModule {

    /** The domain asks for names through [BuiltInIngredientNamer]; only this layer knows resources. */
    @Binds
    abstract fun ingredientNamer(impl: AndroidIngredientNamer): BuiltInIngredientNamer
}
