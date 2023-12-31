package com.maxrave.simpmusic.ui.fragment.player

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.maxrave.simpmusic.R
import com.maxrave.simpmusic.adapter.queue.QueueAdapter
import com.maxrave.simpmusic.databinding.BottomSheetQueueTrackOptionBinding
import com.maxrave.simpmusic.databinding.QueueBottomSheetBinding
import com.maxrave.simpmusic.extension.setEnabledAll
import com.maxrave.simpmusic.service.test.source.MusicSource
import com.maxrave.simpmusic.service.test.source.StateSource
import com.maxrave.simpmusic.viewModel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QueueFragment: BottomSheetDialogFragment() {

    @Inject
    lateinit var musicSource: MusicSource

    private val viewModel by activityViewModels<SharedViewModel>()
    private var _binding: QueueBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var queueAdapter: QueueAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setWindowAnimations(R.style.DialogAnimation)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setOnShowListener {

            val bottomSheetDialog = it as BottomSheetDialog
            val parentLayout =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { bottom ->
                val behaviour = BottomSheetBehavior.from(bottom)
                behaviour.isDraggable = false
                setupFullHeight(bottom)
                behaviour.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QueueBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    @UnstableApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loadingQueue.visibility = View.VISIBLE
        binding.rvQueue.visibility = View.GONE
        binding.topAppBar.subtitle = viewModel.from.value
        binding.topAppBar.setNavigationOnClickListener {
            dismiss()
        }
        queueAdapter = QueueAdapter(arrayListOf(), requireContext(), -1)
        binding.rvQueue.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        if (musicSource.catalogMetadata.isNotEmpty()) {
            queueAdapter.updateList(musicSource.catalogMetadata)
        }

        lifecycleScope.launch {
            val job1 = launch {
                musicSource.stateFlow.collect{ state ->
                    when(state) {
                        StateSource.STATE_INITIALIZING -> {
                            binding.loadingQueue.visibility = View.VISIBLE
                            binding.rvQueue.visibility = View.VISIBLE
                        }
                        StateSource.STATE_ERROR -> {
                            binding.loadingQueue.visibility = View.GONE
                            binding.rvQueue.visibility = View.VISIBLE
                        }
                        StateSource.STATE_INITIALIZED -> {
                            binding.loadingQueue.visibility = View.GONE
                            binding.rvQueue.visibility = View.VISIBLE
                            queueAdapter.updateList(musicSource.catalogMetadata)
                        }
                        else -> {
                            binding.loadingQueue.visibility = View.VISIBLE
                            binding.rvQueue.visibility = View.VISIBLE
                        }
                    }
                }
            }
            val job2 = launch {
                updateNowPlaying()
            }
            val job3 = launch {
                musicSource.currentSongIndex.collect{ index ->
                    Log.d("QueueFragment", "onViewCreated: $index")
                    if (musicSource.stateFlow.value == StateSource.STATE_INITIALIZED || musicSource.stateFlow.value == StateSource.STATE_INITIALIZING){
                        binding.rvQueue.smoothScrollToPosition(index)
                        queueAdapter.setCurrentPlaying(index)
                    }
                }
            }
            val job4 = launch {
                musicSource.added.collect{ isAdded ->
                    Log.d("Check Added in Queue", "$isAdded")
                    if (isAdded){
                        Log.d("Check Queue", "${musicSource.catalogMetadata}")
                        queueAdapter.updateList(musicSource.catalogMetadata)
                        musicSource.changeAddedState()
                    }
                }
            }
            job1.join()
            job2.join()
            job3.join()
            job4.join()
        }

        binding.tvSongTitle.isSelected = true
        binding.tvSongArtist.isSelected = true

        queueAdapter.setOnClickListener(object : QueueAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                viewModel.playMediaItemInMediaSource(position)
                dismiss()
            }
        })
        queueAdapter.setOnOptionClickListener(object : QueueAdapter.OnOptionClickListener {
            override fun onOptionClick(position: Int) {
                val dialog = BottomSheetDialog(requireContext())
                val dialogView = BottomSheetQueueTrackOptionBinding.inflate(layoutInflater)
                with(dialogView) {
                    btMoveUp.setOnClickListener { musicSource.moveItemUp(position)
                        queueAdapter.updateList(musicSource.catalogMetadata)
                        dialog.dismiss() }
                    btMoveDown.setOnClickListener { musicSource.moveItemDown(position)
                        queueAdapter.updateList(musicSource.catalogMetadata)
                        dialog.dismiss() }
                    btDelete.setOnClickListener { musicSource.removeMediaItem(position)
                        queueAdapter.updateList(musicSource.catalogMetadata)
                        dialog.dismiss() }
                }
                if (musicSource.catalogMetadata.size > 1) {
                    when (position) {
                        0 -> {
                            setEnabledAll(dialogView.btMoveUp, false)
                            setEnabledAll(dialogView.btMoveDown, true)
                        }
                        musicSource.catalogMetadata.size - 1 -> {
                            setEnabledAll(dialogView.btMoveUp, true)
                            setEnabledAll(dialogView.btMoveDown, false)
                        }
                        else -> {
                            setEnabledAll(dialogView.btMoveUp, true)
                            setEnabledAll(dialogView.btMoveDown, true)
                        }
                    }
                }
                else {
                    setEnabledAll(dialogView.btMoveUp, false)
                    setEnabledAll(dialogView.btMoveDown, false)
                    setEnabledAll(dialogView.btDelete, false)
                }
                dialog.setCancelable(true)
                dialog.setContentView(dialogView.root)
                dialog.show()
            }
        })
    }
    private fun updateNowPlaying(){
        viewModel.nowPlayingMediaItem.observe(viewLifecycleOwner) {
            if (it != null){
                binding.ivThumbnail.load(it.mediaMetadata.artworkUri)
                binding.tvSongTitle.text = it.mediaMetadata.title
                binding.tvSongTitle.isSelected = true
                binding.tvSongArtist.text = it.mediaMetadata.artist
                binding.tvSongArtist.isSelected = true
            }
        }
    }
}