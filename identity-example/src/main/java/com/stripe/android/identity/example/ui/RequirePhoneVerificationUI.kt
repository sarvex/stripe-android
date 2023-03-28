package com.stripe.android.identity.example.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stripe.android.identity.example.R

@Composable
internal fun RequirePhoneVerificationUI(
    scrollState: ScrollState,
    submissionState: IdentitySubmissionState,
    onSubmissionStateChanged: (IdentitySubmissionState) -> Unit
) {
    var requirePhoneVerification by remember {
        mutableStateOf(false)
    }
    var providedPhoneNumber: String? by remember {
        mutableStateOf(null)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = requirePhoneVerification, onCheckedChange = {
            requirePhoneVerification = it
            onSubmissionStateChanged(
                submissionState.copy(
                    requirePhoneVerification = requirePhoneVerification
                )
            )
            if (!requirePhoneVerification) {
                providedPhoneNumber = null
                onSubmissionStateChanged(
                    submissionState.copy(
                        providedPhoneNumber = null
                    )
                )
            }
        })
        StyledClickableText(
            text = AnnotatedString(stringResource(id = R.string.require_phone_number)),
            onClick = {
                requirePhoneVerification = !requirePhoneVerification
                onSubmissionStateChanged(
                    submissionState.copy(
                        requirePhoneVerification = requirePhoneVerification
                    )
                )
                if (!requirePhoneVerification) {
                    providedPhoneNumber = null
                    onSubmissionStateChanged(
                        submissionState.copy(
                            providedPhoneNumber = null
                        )
                    )
                }
            }
        )
    }
    if (requirePhoneVerification) {
        OutlinedTextField(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                .fillMaxWidth(),
            value = providedPhoneNumber.orEmpty(),
            onValueChange = {
                providedPhoneNumber = it
                onSubmissionStateChanged(
                    submissionState.copy(
                        providedPhoneNumber = it
                    )
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            label = { Text(stringResource(id = R.string.provided_phone_number)) }
        )
    }
    LaunchedEffect(requirePhoneVerification) {
        scrollState.scrollTo(scrollState.maxValue)
    }
}
