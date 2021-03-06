package io.numbersprotocol.starlingcapture.feature.proof

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.ImageLoader
import coil.api.load
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.android.material.transition.MaterialContainerTransform
import com.stfalcon.imageviewer.StfalconImageViewer
import io.numbersprotocol.starlingcapture.BuildConfig
import io.numbersprotocol.starlingcapture.R
import io.numbersprotocol.starlingcapture.data.information.InformationRepository
import io.numbersprotocol.starlingcapture.data.proof.Proof
import io.numbersprotocol.starlingcapture.data.proof.ProofRepository
import io.numbersprotocol.starlingcapture.data.serialization.SaveProofRelatedDataWorker
import io.numbersprotocol.starlingcapture.databinding.FragmentProofBinding
import io.numbersprotocol.starlingcapture.di.CoilImageLoader
import io.numbersprotocol.starlingcapture.publisher.PublishersDialog
import io.numbersprotocol.starlingcapture.util.enableCardPreview
import io.numbersprotocol.starlingcapture.util.observeEvent
import io.numbersprotocol.starlingcapture.util.snack
import kotlinx.android.synthetic.main.fragment_proof.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.qualifier.named

class ProofFragment(
    private val proofRepository: ProofRepository,
    private val informationRepository: InformationRepository,
    private val publishersDialog: PublishersDialog
) : Fragment() {

    private val proofViewModel: ProofViewModel by viewModel()
    private val imageLoader: ImageLoader by inject(named(CoilImageLoader.LargeTransitionThumb))
    private val args: ProofFragmentArgs by navArgs()
    private lateinit var proof: Proof
    private lateinit var binding: FragmentProofBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform()
        postponeEnterTransition()
        proof = args.proof
        initializeViewModel()
    }

    private fun initializeViewModel() {
        proofViewModel.proof.value = proof
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentProofBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = proofViewModel
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeSharedElements()
        setOptionsMenuListener()
        bindViewLifecycle()
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun initializeSharedElements() {
        scrollView.transitionName = "$proof"
        thumbImageView.load(
            proofRepository.getRawFile(proof),
            imageLoader = imageLoader,
            builder = {
                listener(
                    onError = { _, _ -> startPostponedEnterTransition() },
                    onSuccess = { _, _ -> startPostponedEnterTransition() }
                )
            }
        )
    }

    private fun setOptionsMenuListener() {
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.publish -> {
                    publishersDialog.show(requireActivity(), setOf(proof), viewLifecycleOwner)
                }
                R.id.saveAs -> dispatchPickFolderIntent()
                R.id.delete -> showConfirmDialog { deleteProof() }
            }
            true
        }
    }

    private fun bindViewLifecycle() {
        bindInformationProviderViewPager()
        bindSignatureViewPager()
        proofViewModel.viewMediaEvent.observeEvent(viewLifecycleOwner) {
            if (proofViewModel.isVideo.value != true) showImageViewer()
            else dispatchViewVideoIntent()
        }
        proofViewModel.editCaptionEvent.observeEvent(viewLifecycleOwner) {
            showCaptionEditingDialog(it)
        }
    }

    private fun showImageViewer() {
        StfalconImageViewer.Builder(requireContext(), listOf(proof)) { view, proof ->
            view.load(proofRepository.getRawFile(proof))
        }.show()
    }

    private fun dispatchViewVideoIntent() {
        val videoFile = proofRepository.getRawFile(proof)
        val videoUri = FileProvider.getUriForFile(
            requireContext(),
            "${BuildConfig.APPLICATION_ID}.provider",
            videoFile
        )
        val viewVideoIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(videoUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (viewVideoIntent.resolveActivity(requireContext().packageManager) == null) {
            snack("No provider can handle ${Intent.ACTION_VIEW} with video/* MIME type")
            return
        }
        startActivity(viewVideoIntent)
    }

    private fun dispatchPickFolderIntent() {
        val pickFolderIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (pickFolderIntent.resolveActivity(requireContext().packageManager) == null) {
            snack("No provider can handle ${Intent.ACTION_OPEN_DOCUMENT_TREE}.")
            return
        }
        startActivityForResult(pickFolderIntent, REQUEST_PICK_FOLDER)
    }

    private fun showCaptionEditingDialog(prefill: String) {
        MaterialDialog(requireContext()).show {
            lifecycleOwner(viewLifecycleOwner)
            input(allowEmpty = true, prefill = prefill) { _, text ->
                proofViewModel.saveCaption(text.toString())
            }
            title(R.string.edit_caption)
            positiveButton(android.R.string.ok)
        }
    }

    private fun showConfirmDialog(onPositive: () -> Unit) {
        MaterialDialog(requireContext()).show {
            lifecycleOwner(viewLifecycleOwner)
            title(R.string.are_you_sure)
            message(R.string.message_are_you_sure)
            positiveButton(android.R.string.ok) { onPositive() }
            negativeButton(android.R.string.cancel)
        }
    }

    private fun deleteProof() = lifecycleScope.launch {
        proofRepository.remove(proof)
        findNavController().navigateUp()
    }

    private fun bindInformationProviderViewPager() {
        val informationProviderAdapter =
            InformationProviderAdapter(informationRepository, viewLifecycleOwner, proof)

        binding.informationProviderViewPager.adapter = informationProviderAdapter
        binding.informationProviderViewPager.enableCardPreview()

        proofViewModel.informationProviders.observe(viewLifecycleOwner) {
            informationProviderAdapter.submitList(it)
        }
    }

    private fun bindSignatureViewPager() {
        val signatureAdapter = SignatureAdapter()
        binding.signatureViewPager.adapter = signatureAdapter
        binding.signatureViewPager.enableCardPreview()
        proofViewModel.signatures.observe(viewLifecycleOwner) { signatureAdapter.submitList(it) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (resultCode != RESULT_CANCELED) snack("Bad result code from request ($requestCode): $resultCode")
            return
        }
        when (requestCode) {
            REQUEST_PICK_FOLDER -> SaveProofRelatedDataWorker.saveProofAs(
                requireContext(), proof, data!!.data!!
            )
            else -> snack("Unknown request code ($requestCode) from activity result.")
        }
    }

    companion object {
        const val REQUEST_PICK_FOLDER = 1
    }
}