package com.stripe.android.financialconnections.features.networkinglinkverification

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.ViewModelContext
import com.stripe.android.core.Logger
import com.stripe.android.financialconnections.analytics.FinancialConnectionsAnalyticsTracker
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.Error
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError.Error.ConsumerNotFoundError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError.Error.LookupConsumerSession
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError.Error.NetworkedAccountsRetrieveMethodError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationError.Error.StartVerificationSessionError
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationSuccess
import com.stripe.android.financialconnections.analytics.FinancialConnectionsEvent.VerificationSuccessNoAccounts
import com.stripe.android.financialconnections.domain.ConfirmVerification
import com.stripe.android.financialconnections.domain.GetManifest
import com.stripe.android.financialconnections.domain.GoNext
import com.stripe.android.financialconnections.domain.LookupConsumerAndStartVerification
import com.stripe.android.financialconnections.domain.MarkLinkVerified
import com.stripe.android.financialconnections.domain.PollNetworkedAccounts
import com.stripe.android.financialconnections.features.networkinglinkverification.NetworkingLinkVerificationState.Payload
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest.Pane
import com.stripe.android.financialconnections.model.PartnerAccountsList
import com.stripe.android.financialconnections.ui.FinancialConnectionsSheetNativeActivity
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.VerificationType
import com.stripe.android.uicore.elements.IdentifierSpec
import com.stripe.android.uicore.elements.OTPController
import com.stripe.android.uicore.elements.OTPElement
import getRedactedPhoneNumber
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class NetworkingLinkVerificationViewModel @Inject constructor(
    initialState: NetworkingLinkVerificationState,
    private val getManifest: GetManifest,
    private val confirmVerification: ConfirmVerification,
    private val markLinkVerified: MarkLinkVerified,
    private val pollNetworkedAccounts: PollNetworkedAccounts,
    private val goNext: GoNext,
    private val analyticsTracker: FinancialConnectionsAnalyticsTracker,
    private val lookupConsumerAndStartVerification: LookupConsumerAndStartVerification,
    private val logger: Logger
) : MavericksViewModel<NetworkingLinkVerificationState>(initialState) {

    init {
        observeAsyncs()
        viewModelScope.launch {
            setState { copy(payload = Loading()) }
            runCatching { getManifest().also { requireNotNull(it.accountholderCustomerEmailAddress) } }
                .onSuccess { manifest ->
                    lookupConsumerAndStartVerification(
                        email = requireNotNull(manifest.accountholderCustomerEmailAddress),
                        businessName = manifest.businessName,
                        verificationType = VerificationType.SMS,
                        onConsumerNotFound = {
                            analyticsTracker.track(VerificationError(PANE, ConsumerNotFoundError))
                            goNext(Pane.INSTITUTION_PICKER)
                        },
                        onLookupError = { error ->
                            analyticsTracker.track(VerificationError(PANE, LookupConsumerSession))
                            setState { copy(payload = Fail(error)) }
                        },
                        onStartVerification = { /* no-op */ },
                        onVerificationStarted = { consumerSession ->
                            val payload = buildPayload(consumerSession)
                            setState { copy(payload = Success(payload)) }
                        },
                        onStartVerificationError = { error ->
                            analyticsTracker.track(
                                VerificationError(PANE, StartVerificationSessionError)
                            )
                            setState { copy(payload = Fail(error)) }
                        }
                    )
                }
                .onFailure { setState { copy(payload = Fail(it)) } }
        }
    }

    private fun buildPayload(consumerSession: ConsumerSession) = Payload(
        email = consumerSession.emailAddress,
        phoneNumber = consumerSession.getRedactedPhoneNumber(),
        consumerSessionClientSecret = consumerSession.clientSecret,
        otpElement = OTPElement(
            IdentifierSpec.Generic("otp"),
            OTPController()
        )
    )

    private fun observeAsyncs() {
        onAsync(
            NetworkingLinkVerificationState::payload,
            onSuccess = {
                viewModelScope.launch {
                    it.otpElement.otpCompleteFlow.collectLatest(::onOTPEntered)
                }
            },
            onFail = { error ->
                logger.error("Error starting verification", error)
                analyticsTracker.track(Error(PANE, error))
            },
        )
    }

    private fun onOTPEntered(otp: String) = suspend {
        val payload = requireNotNull(awaitState().payload())
        confirmVerification.sms(
            consumerSessionClientSecret = payload.consumerSessionClientSecret,
            verificationCode = otp
        )
        val updatedManifest = markLinkVerified()
        runCatching { pollNetworkedAccounts(payload.consumerSessionClientSecret) }
            .fold(
                onSuccess = { onNetworkedAccountsSuccess(it, updatedManifest) },
                onFailure = { onNetworkedAccountsFailed(it, updatedManifest) }
            )
    }.execute { copy(confirmVerification = it) }

    private suspend fun onNetworkedAccountsFailed(
        error: Throwable,
        updatedManifest: FinancialConnectionsSessionManifest
    ) {
        logger.error("Error fetching networked accounts", error)
        analyticsTracker.track(Error(PANE, error))
        analyticsTracker.track(VerificationError(PANE, NetworkedAccountsRetrieveMethodError))
        goNext(updatedManifest.nextPane)
    }

    private suspend fun onNetworkedAccountsSuccess(
        accounts: PartnerAccountsList,
        updatedManifest: FinancialConnectionsSessionManifest
    ) {
        if (accounts.data.isEmpty()) {
            // Networked user has no accounts
            analyticsTracker.track(VerificationSuccessNoAccounts(PANE))
            goNext(updatedManifest.nextPane)
        } else {
            // Networked user has linked accounts
            analyticsTracker.track(VerificationSuccess(PANE))
            goNext(Pane.LINK_ACCOUNT_PICKER)
        }
    }

    companion object :
        MavericksViewModelFactory<NetworkingLinkVerificationViewModel, NetworkingLinkVerificationState> {

        override fun create(
            viewModelContext: ViewModelContext,
            state: NetworkingLinkVerificationState
        ): NetworkingLinkVerificationViewModel {
            return viewModelContext.activity<FinancialConnectionsSheetNativeActivity>()
                .viewModel
                .activityRetainedComponent
                .networkingLinkVerificationSubcomponent
                .initialState(state)
                .build()
                .viewModel
        }

        internal val PANE = Pane.NETWORKING_LINK_VERIFICATION
    }
}

internal data class NetworkingLinkVerificationState(
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
