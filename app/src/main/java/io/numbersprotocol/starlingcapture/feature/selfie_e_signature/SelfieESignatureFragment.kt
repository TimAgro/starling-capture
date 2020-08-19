package io.numbersprotocol.starlingcapture.feature.selfie_e_signature

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.numbersprotocol.starlingcapture.databinding.FragmentSelfieESignatureBinding
import kotlinx.android.synthetic.main.fragment_selfie_e_signature.*
import org.koin.androidx.viewmodel.ext.android.viewModel

class SelfieESignatureFragment : Fragment() {

    private val selfieESignatureViewModel: SelfieESigantureViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        FragmentSelfieESignatureBinding.inflate(inflater, container, false).apply {
            viewModel = selfieESignatureViewModel
            lifecycleOwner = viewLifecycleOwner
            return root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }
}