package com.stripe.android.ui.core.elements

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Header that displays promo information about Affirm
 */
@Serializable
@Parcelize
internal data class AffirmTextSpec(
    override val api_path: IdentifierSpec = IdentifierSpec.Generic("affirm_header")
) : FormItemSpec() {
    fun transform(): FormElement =
        AffirmHeaderElement(this.api_path)
}
