package com.stripe.android.financialconnections.features.networkingsavetolinkverification

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.ViewModelContext
import com.stripe.android.core.Logger
import com.stripe.android.financialconnections.analytics.FinancialConnectionsAnalyticsTracker
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.Error
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.PaneLoaded
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError.Error.ConfirmVerificationSessionError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError.Error.StartVerificationSessionError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationSuccess
import com.stripe.android.financialconnections.domain.ConfirmVerification
import com.stripe.android.financialconnections.domain.ConfirmVerification.OTPError
import com.stripe.android.financialconnections.domain.GetCachedAccounts
import com.stripe.android.financialconnections.domain.GetCachedConsumerSession
import com.stripe.android.financialconnections.domain.GoNext
import com.stripe.android.financialconnections.domain.MarkLinkVerified
import com.stripe.android.financialconnections.domain.SaveAccountToLink
import com.stripe.android.financialconnections.domain.StartVerification
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest.Pane
import com.stripe.android.financialconnections.repository.SaveToLinkWithStripeSucceededRepository
import com.stripe.android.financialconnections.ui.FinancialConnectionsSheetNativeActivity
import com.stripe.android.uicore.elements.IdentifierSpec
import com.stripe.android.uicore.elements.OTPController
import com.stripe.android.uicore.elements.OTPElement
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class NetworkingSaveToLinkVerificationViewModel @Inject constructor(
    initialState: NetworkingSaveToLinkVerificationState,
    private val eventTracker: FinancialConnectionsAnalyticsTracker,
    private val getCachedConsumerSession: GetCachedConsumerSession,
    private val saveToLinkWithStripeSucceeded: SaveToLinkWithStripeSucceededRepository,
    private val startVerification: StartVerification,
    private val confirmVerification: ConfirmVerification,
    private val markLinkVerified: MarkLinkVerified,
    private val getCachedAccounts: GetCachedAccounts,
    private val saveAccountToLink: SaveAccountToLink,
    private val goNext: GoNext,
    private val logger: Logger
) : MavericksViewModel<NetworkingSaveToLinkVerificationState>(initialState) {

    init {
        logErrors()
        suspend {
            val consumerSession = requireNotNull(getCachedConsumerSession())
            runCatching {
                startVerification.sms(consumerSession.clientSecret)
            }.onFailure {
                eventTracker.track(VerificationError(PANE, StartVerificationSessionError))
            }.getOrThrow()
            eventTracker.track(PaneLoaded(PANE))
            NetworkingSaveToLinkVerificationState.Payload(
                email = consumerSession.emailAddress,
                phoneNumber = consumerSession.redactedPhoneNumber,
                consumerSessionClientSecret = consumerSession.clientSecret,
                otpElement = OTPElement(
                    IdentifierSpec.Generic("otp"),
                    OTPController()
                )
            )
        }.execute { copy(payload = it) }
    }

    private fun logErrors() {
        onAsync(
            NetworkingSaveToLinkVerificationState::payload,
            onSuccess = {
                viewModelScope.launch {
                    it.otpElement.otpCompleteFlow.collectLatest { onOTPEntered(it) }
                }
            },
            onFail = { error ->
                logger.error("Error fetching payload", error)
                eventTracker.track(Error(PANE, error))
            },
        )
        onAsync(
            NetworkingSaveToLinkVerificationState::confirmVerification,
            onSuccess = {
                saveToLinkWithStripeSucceeded.set(true)
                goNext(Pane.SUCCESS)
            },
            onFail = { error ->
                logger.error("Error confirming verification", error)
                eventTracker.track(Error(PANE, error))
                if (error !is OTPError) {
                    saveToLinkWithStripeSucceeded.set(false)
                    goNext(Pane.SUCCESS)
                }
            },
        )
    }

    private fun onOTPEntered(otp: String) = suspend {
        val payload = requireNotNull(awaitState().payload())

        runCatching {
            confirmVerification.sms(
                consumerSessionClientSecret = payload.consumerSessionClientSecret,
                verificationCode = otp
            )
            saveAccountToLink.existing(
                consumerSessionClientSecret = payload.consumerSessionClientSecret,
                selectedAccounts = getCachedAccounts().map { it.id },
            )
        }
            .onSuccess { eventTracker.track(VerificationSuccess(PANE)) }
            .onFailure {
                eventTracker.track(VerificationError(PANE, ConfirmVerificationSessionError))
            }.getOrThrow()

        // Mark link verified (ignore its result).
        kotlin.runCatching { markLinkVerified() }
        Unit
    }.execute { copy(confirmVerification = it) }

    fun onSkipClick() {
        goNext(Pane.SUCCESS)
    }

    companion object :
        MavericksViewModelFactory<NetworkingSaveToLinkVerificationViewModel, NetworkingSaveToLinkVerificationState> {

        internal val PANE = Pane.NETWORKING_SAVE_TO_LINK_VERIFICATION

        override fun create(
            viewModelContext: ViewModelContext,
            state: NetworkingSaveToLinkVerificationState
        ): NetworkingSaveToLinkVerificationViewModel {
            return viewModelContext.activity<FinancialConnectionsSheetNativeActivity>()
                .viewModel
                .activityRetainedComponent
                .networkingSaveToLinkVerificationSubcomponent
                .initialState(state)
                .build()
                .viewModel
        }
    }
}

internal data class NetworkingSaveToLinkVerificationState(
    val payload: Async<Payload> = Uninitialized,
    val confirmVerification: Async<Unit> = Uninitialized
) : MavericksState {

    data class Payload(
        val email: String,
        val phoneNumber: String,
        val otpElement: OTPElement,
        val consumerSessionClientSecret: String
    )
}
