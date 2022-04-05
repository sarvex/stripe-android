package com.stripe.android.ui.core.elements

import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize

/**
 * This is an element that will make elements (as specified by identifier) hidden
 * when save for future use is unchecked
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Parcelize
class SaveForFutureUseSpec : FormItemSpec(), RequiredItemSpec {
    override val identifier = IdentifierSpec.SaveForFutureUse

    fun transform(initialValue: Boolean, merchantName: String): FormElement =
        SaveForFutureUseElement(
            this.identifier,
            SaveForFutureUseController(initialValue),
            merchantName
        )
}
