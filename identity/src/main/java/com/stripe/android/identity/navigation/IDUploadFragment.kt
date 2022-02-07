package com.stripe.android.identity.navigation

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.stripe.android.identity.viewmodel.IDUploadViewModel
import com.stripe.android.identity.R

class IDUploadFragment : Fragment() {

    companion object {
        fun newInstance() = IDUploadFragment()
    }

    private lateinit var viewModel: IDUploadViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.id_upload_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(IDUploadViewModel::class.java)
        // TODO: Use the ViewModel
    }

}