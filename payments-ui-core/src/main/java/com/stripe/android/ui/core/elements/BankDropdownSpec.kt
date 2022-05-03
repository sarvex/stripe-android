package com.stripe.android.ui.core.elements

import androidx.annotation.RestrictTo
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize

@Parcelize
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
data class BankDropdownSpec(
    override val api_path: IdentifierSpec,
    @StringRes val label: Int,
    val bankType: SupportedBankType
) : FormItemSpec(), RequiredItemSpec {
    fun transform(bankRepository: BankRepository, initialValue: String?) = createSectionElement(
        SimpleDropdownElement(
            this.api_path,
            DropdownFieldController(
                SimpleDropdownConfig(
                    label,
                    bankRepository.get(this.bankType)
                ),
                initialValue = initialValue
            )
        )
    )
}
