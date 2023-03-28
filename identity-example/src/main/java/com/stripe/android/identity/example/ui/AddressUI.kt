package com.stripe.android.identity.example.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable

@Composable
internal fun AddressUI(
    scrollState: ScrollState,
    submissionState: IdentitySubmissionState,
    onSubmissionStateChanged: (IdentitySubmissionState) -> Unit
) {
    RequirePhoneVerificationUI(scrollState, submissionState, onSubmissionStateChanged)
}
