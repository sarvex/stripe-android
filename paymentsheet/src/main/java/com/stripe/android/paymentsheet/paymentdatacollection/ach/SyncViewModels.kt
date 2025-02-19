package com.stripe.android.paymentsheet.paymentdatacollection.ach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.stripe.android.paymentsheet.ui.PrimaryButton
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel
import kotlinx.coroutines.flow.filterNot

@Composable
internal fun SyncViewModels(
    viewModel: USBankAccountFormViewModel,
    sheetViewModel: BaseSheetViewModel,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.result.collect { result ->
            sheetViewModel.handleConfirmUSBankAccount(result)
        }
    }

    LaunchedEffect(Unit) {
        sheetViewModel.primaryButtonState.collect { state ->
            // When the primary button state is StartProcessing or FinishProcessing
            // we should disable the inputs of this form. StartProcessing shows the loading
            // spinner, FinishProcessing shows the checkmark animation
            viewModel.setProcessing(
                state is PrimaryButton.State.StartProcessing ||
                    state is PrimaryButton.State.FinishProcessing
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requiredFields.collect { hasRequiredFields ->
            sheetViewModel.updateCustomPrimaryButtonUiState {
                it?.copy(enabled = hasRequiredFields)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveForFutureUse.filterNot {
            viewModel.currentScreenState.value is USBankAccountFormScreenState.BillingDetailsCollection
        }.collect { saved ->
            val mandateText = ACHText.getContinueMandateText(
                context = context,
                merchantName = viewModel.formattedMerchantName(),
                isSaveForFutureUseSelected = saved,
            )
            sheetViewModel.updateMandateText(
                context = context,
                screenState = viewModel.currentScreenState.value,
                mandateText = mandateText,
                merchantName = viewModel.formattedMerchantName(),
            )
        }
    }
}
