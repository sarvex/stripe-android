package com.stripe.android.paymentsheet.flowcontroller

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.stripe.android.CreateIntentCallback
import com.stripe.android.CreateIntentCallbackForServerSideConfirmation
import com.stripe.android.ExperimentalPaymentSheetDecouplingApi
import com.stripe.android.IntentConfirmationInterceptor
import com.stripe.android.paymentsheet.PaymentOptionCallback
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.PaymentSheetResultCallback

/**
 * Creates a [PaymentSheet.FlowController] that is remembered across compositions.
 *
 * This *must* be called unconditionally, as part of the initialization path.
 *
 * @param paymentOptionCallback Called when the customer's desired payment method changes.
 * @param paymentResultCallback Called when a [PaymentSheetResult] is available.
 */
@Composable
fun rememberPaymentSheetFlowController(
    paymentOptionCallback: PaymentOptionCallback,
    paymentResultCallback: PaymentSheetResultCallback,
): PaymentSheet.FlowController {
    val viewModelStoreOwner = requireNotNull(LocalViewModelStoreOwner.current) {
        "PaymentSheet.FlowController must be created with access to a ViewModelStoreOwner"
    }

    val activityResultRegistryOwner = requireNotNull(
        LocalActivityResultRegistryOwner.current
    ) {
        "PaymentSheet.FlowController must be created with access to a ActivityResultRegistryOwner"
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val activity = requireActivity(LocalContext.current) {
        "PaymentSheet.FlowController must be created in the context of an Activity"
    }

    return remember {
        FlowControllerFactory(
            viewModelStoreOwner = viewModelStoreOwner,
            lifecycleOwner = lifecycleOwner,
            activityResultRegistryOwner = activityResultRegistryOwner,
            statusBarColor = { activity.window?.statusBarColor },
            paymentOptionCallback = paymentOptionCallback,
            paymentResultCallback = paymentResultCallback,
        ).create()
    }
}

/**
 * Creates a [PaymentSheet.FlowController] that is remembered across compositions. Use this method
 * when you intend to create the [com.stripe.android.model.PaymentIntent] or
 * [com.stripe.android.model.SetupIntent] on your server.
 *
 * This *must* be called unconditionally, as part of the initialization path.
 *
 * @param createIntentCallback Called when the customer confirms the payment or setup.
 * @param paymentOptionCallback Called when the customer's desired payment method changes.
 * @param paymentResultCallback Called when a [PaymentSheetResult] is available.
 */
@ExperimentalPaymentSheetDecouplingApi
@Composable
fun rememberPaymentSheetFlowController(
    createIntentCallback: CreateIntentCallback,
    paymentOptionCallback: PaymentOptionCallback,
    paymentResultCallback: PaymentSheetResultCallback,
): PaymentSheet.FlowController {
    UpdateIntentConfirmationInterceptor(createIntentCallback)
    return rememberPaymentSheetFlowController(
        paymentOptionCallback = paymentOptionCallback,
        paymentResultCallback = paymentResultCallback,
    )
}

/**
 * Creates a [PaymentSheet.FlowController] that is remembered across compositions. Use this method
 * when you intend to create and confirm the [com.stripe.android.model.PaymentIntent] or
 * [com.stripe.android.model.SetupIntent] on your server.
 *
 * This *must* be called unconditionally, as part of the initialization path.
 *
 * @param createIntentCallback Called when the customer confirms the payment or setup.
 * @param paymentOptionCallback Called when the customer's desired payment method changes.
 * @param paymentResultCallback Called when a [PaymentSheetResult] is available.
 */
@ExperimentalPaymentSheetDecouplingApi
@Composable
fun rememberPaymentSheetFlowController(
    createIntentCallback: CreateIntentCallbackForServerSideConfirmation,
    paymentOptionCallback: PaymentOptionCallback,
    paymentResultCallback: PaymentSheetResultCallback,
): PaymentSheet.FlowController {
    UpdateIntentConfirmationInterceptor(createIntentCallback)
    return rememberPaymentSheetFlowController(
        paymentOptionCallback = paymentOptionCallback,
        paymentResultCallback = paymentResultCallback,
    )
}

private fun requireActivity(context: Context, errorMessage: () -> String): Activity {
    var currentContext = context
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    throw IllegalStateException(errorMessage())
}

@OptIn(ExperimentalPaymentSheetDecouplingApi::class)
@Composable
private fun UpdateIntentConfirmationInterceptor(
    createIntentCallback: CreateIntentCallback,
) {
    LaunchedEffect(createIntentCallback) {
        IntentConfirmationInterceptor.createIntentCallback = createIntentCallback
    }
}

@OptIn(ExperimentalPaymentSheetDecouplingApi::class)
@Composable
private fun UpdateIntentConfirmationInterceptor(
    createIntentCallbackForServerSideConfirmation: CreateIntentCallbackForServerSideConfirmation,
) {
    LaunchedEffect(createIntentCallbackForServerSideConfirmation) {
        IntentConfirmationInterceptor.createIntentCallback = createIntentCallbackForServerSideConfirmation
    }
}
