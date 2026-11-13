package com.uzuu.signbridge.feature.devicecheck

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.uzuu.signbridge.MyApp
import com.uzuu.signbridge.R
import com.uzuu.signbridge.databinding.FragmentDeviceCheckBinding
import com.uzuu.signbridge.domain.model.ServiceContext
import kotlinx.coroutines.launch

class DeviceCheckFragment : Fragment(R.layout.fragment_device_check) {
    private var binding: FragmentDeviceCheckBinding? = null
    private val container get() = (requireActivity().application as MyApp).container
    private val viewModel: DeviceCheckViewModel by viewModels { container.deviceCheckFactory }
    private var chipSignature: Any? = null
    private var replySignature: Any? = null
    private var suggestionSignature: Any? = null
    private var rendering = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && binding != null) openCamera()
        else message(R.string.permission_camera_denied)
    }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message(if (granted) R.string.permission_mic_granted else R.string.permission_mic_denied)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentDeviceCheckBinding.bind(view)
        binding = ui
        listOf(ui.pageHeading, ui.chipsHeading, ui.devicesHeading, ui.outgoingHeading,
            ui.incomingHeading, ui.historyHeading).forEach { ViewCompat.setAccessibilityHeading(it, true) }
        chipSignature = null; replySignature = null; suggestionSignature = null
        ui.contextSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf("Quán ăn / cà phê", "Hiệu thuốc", "Hành chính", "Tự do"))
        ui.contextSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) {
                if (!rendering) viewModel.onEvent(DeviceCheckUiEvent.ContextSelected(ServiceContext.entries[position]))
            }
        }
        ui.addChip.setOnClickListener {
            viewModel.onEvent(DeviceCheckUiEvent.AddChip(ui.newChip.text?.toString().orEmpty()))
        }
        ui.deleteChip.setOnClickListener {
            viewModel.state.value.selectedIds.singleOrNull()?.let {
                viewModel.onEvent(DeviceCheckUiEvent.DeleteChip(it))
            }
        }
        ui.sendChips.setOnClickListener { viewModel.onEvent(DeviceCheckUiEvent.SendChips) }
        ui.healthButton.setOnClickListener { viewModel.onEvent(DeviceCheckUiEvent.CheckHealth) }
        ui.replayButton.setOnClickListener { viewModel.onEvent(DeviceCheckUiEvent.Replay) }
        ui.retryLoad.setOnClickListener { viewModel.onEvent(DeviceCheckUiEvent.RetryLoad) }
        ui.openCamera.setOnClickListener {
            if (hasPermission(Manifest.permission.CAMERA)) openCamera()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }
        bindCaptureButton(ui.signButton, true)
        bindCaptureButton(ui.micButton, false)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun openCamera() {
        val ui = binding ?: return
        ui.cameraPreview.isVisible = true
        container.cameraPreview.bind(viewLifecycleOwner, ui.cameraPreview.surfaceProvider,
            ui.cameraPreview.display?.rotation ?: android.view.Surface.ROTATION_0) { result ->
            if (binding == null) return@bind
            viewModel.onEvent(DeviceCheckUiEvent.CameraReady(result.isSuccess))
            viewModel.onEvent(DeviceCheckUiEvent.ShowMessage(
                result.exceptionOrNull()?.message ?: getString(R.string.camera_ready)))
        }
    }

    private fun beginCapture(sign: Boolean) {
        if (sign) {
            if (!viewModel.state.value.cameraReady) {
                message(R.string.camera_ready)
                if (hasPermission(Manifest.permission.CAMERA)) openCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
                return
            }
            viewModel.onEvent(DeviceCheckUiEvent.StartSign)
        } else {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            viewModel.onEvent(DeviceCheckUiEvent.StartRecording)
        }
    }

    private fun endCapture(sign: Boolean) {
        viewModel.onEvent(if (sign) DeviceCheckUiEvent.FinishSign else DeviceCheckUiEvent.FinishRecording)
    }

    @SuppressLint("ClickableViewAccessibility") // Explicit click/key toggle complements push-to-talk touch.
    private fun bindCaptureButton(button: MaterialButton, sign: Boolean) {
        var skipClick = false
        button.setOnClickListener {
            if (skipClick) { skipClick = false; return@setOnClickListener }
            val operation = viewModel.state.value.operation
            if (operation == if (sign) DeviceOperation.CAPTURING_SIGN else DeviceOperation.RECORDING) endCapture(sign)
            else beginCapture(sign)
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    beginCapture(sign)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    endCapture(sign)
                    skipClick = true
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    viewModel.onEvent(DeviceCheckUiEvent.Backgrounded)
                    true
                }
                else -> true
            }
        }
    }

    private fun render(state: DeviceCheckUiState) {
        val ui = binding ?: return
        rendering = true
        ui.contextSpinner.setSelection(state.context.ordinal, false)
        ui.contextSpinner.isEnabled = state.isIdle
        rendering = false
        ui.statusText.text = state.message
        ui.retryLoad.isVisible = state.session == null && state.isIdle
        ui.sendChips.isEnabled = state.isIdle && state.session != null && state.selectedIds.isNotEmpty()
        ui.addChip.isEnabled = state.isIdle && state.session != null
        ui.newChip.isEnabled = state.isIdle
        ui.deleteChip.isEnabled = state.isIdle && state.selectedIds.singleOrNull()?.let { id ->
            state.chips.any { it.id == id && it.isPersonal }
        } == true
        ui.healthButton.isEnabled = state.isIdle
        ui.openCamera.isEnabled = state.isIdle
        ui.replayButton.isEnabled = state.isIdle && state.outgoing.isNotBlank()
        ui.signButton.isEnabled = (state.isIdle && state.session != null) || state.operation == DeviceOperation.CAPTURING_SIGN
        ui.micButton.isEnabled = (state.isIdle && state.session != null) || state.operation == DeviceOperation.RECORDING
        ui.signButton.setText(if (state.operation == DeviceOperation.CAPTURING_SIGN) R.string.stop_sign else R.string.hold_sign)
        ui.micButton.setText(if (state.operation == DeviceOperation.RECORDING) R.string.stop_mic else R.string.hold_mic)

        val chipsKey = Triple(state.chips, state.selectedIds, state.isIdle)
        if (chipSignature != chipsKey) {
            chipSignature = chipsKey
            ui.chips.removeAllViews()
            state.chips.forEach { item ->
                ui.chips.addView(Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = item.label
                    isCheckable = true
                    isChecked = item.id in state.selectedIds
                    isEnabled = state.isIdle
                    setEnsureMinTouchTargetSize(true)
                    setOnClickListener { viewModel.onEvent(DeviceCheckUiEvent.ChipToggled(item.id)) }
                })
            }
        }
        ui.outgoingText.text = when {
            state.outgoing.isBlank() -> getString(R.string.outgoing_empty)
            state.isOffline -> getString(R.string.offline_format, state.outgoing)
            else -> state.outgoing
        }
        ui.signResult.isVisible = state.signResult != null
        state.signResult?.let { result ->
            ui.signResult.text = getString(R.string.sign_details, result.glosses.joinToString(" · "),
                (result.confidence * 100).toInt(), getString(if (result.needsConfirmation) R.string.confirm_sign else R.string.sign_confirmed))
        }
        val suggestions = state.signResult?.takeIf { it.needsConfirmation }?.suggestedChips.orEmpty()
        val suggestionsKey = suggestions to state.isIdle
        if (suggestionSignature != suggestionsKey) {
            suggestionSignature = suggestionsKey
            ui.suggestions.removeAllViews()
            suggestions.forEach { label ->
                ui.suggestions.addView(Chip(requireContext()).apply {
                    text = label
                    contentDescription = "Chọn hoặc lưu thẻ gợi ý: $label"
                    isEnabled = state.isIdle
                    setEnsureMinTouchTargetSize(true)
                    setOnClickListener {
                        val found = state.chips.find { it.label.equals(label, ignoreCase = true) }
                        viewModel.onEvent(if (found == null) DeviceCheckUiEvent.AddChip(label)
                            else DeviceCheckUiEvent.ChipToggled(found.id))
                    }
                })
            }
        }
        val incoming = state.incoming
        ui.summaryText.text = incoming?.let { summary ->
            SpannableString(summary.summary).apply {
                summary.highlights.forEach { highlight ->
                    var start = indexOf(highlight.text)
                    while (start >= 0 && highlight.text.isNotEmpty()) {
                        setSpan(StyleSpan(Typeface.BOLD), start, start + highlight.text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        start = indexOf(highlight.text, start + highlight.text.length)
                    }
                }
            }
        } ?: getString(R.string.incoming_empty)
        ui.transcriptText.text = incoming?.let { getString(R.string.transcript_format, it.transcript) }.orEmpty()
        val repliesKey = incoming?.quickReplies to state.isIdle
        if (replySignature != repliesKey) {
            replySignature = repliesKey
            ui.quickReplies.removeAllViews()
            incoming?.quickReplies?.forEach { reply ->
                ui.quickReplies.addView(MaterialButton(requireContext()).apply {
                    text = reply
                    isEnabled = state.isIdle
                    setOnClickListener { viewModel.onEvent(DeviceCheckUiEvent.ReplySelected(reply)) }
                })
            }
        }
        ui.historyText.text = state.history.take(20).joinToString("\n\n") {
            val direction = if (it.direction == com.uzuu.signbridge.domain.model.Direction.OUTGOING) "Gửi" else "Nhận"
            "$direction${if (it.isOffline) " · Offline" else ""}: ${it.processedText}"
        }.ifEmpty { getString(R.string.history_empty) }
    }

    private fun message(resource: Int) {
        viewModel.onEvent(DeviceCheckUiEvent.ShowMessage(getString(resource)))
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.state.value.session == null && viewModel.state.value.isIdle) {
            viewModel.onEvent(DeviceCheckUiEvent.RetryLoad)
        }
    }

    override fun onStop() {
        viewModel.onEvent(DeviceCheckUiEvent.Backgrounded)
        super.onStop()
    }

    override fun onDestroyView() {
        container.cameraPreview.unbind()
        viewModel.onEvent(DeviceCheckUiEvent.CameraReady(false))
        binding = null
        super.onDestroyView()
    }
}

