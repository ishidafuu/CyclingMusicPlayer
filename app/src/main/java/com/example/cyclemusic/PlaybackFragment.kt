import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cyclemusic.R
import java.io.File

class PlaybackFragment : Fragment() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mp3ListView: ListView
    private lateinit var mp3Files: Array<File>
    private lateinit var fileList: MutableList<String>
    private lateinit var playPauseButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private val viewModel: SharedViewModel by activityViewModels()
    private var folderPath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.playback_fragment, container, false)

        viewModel.selectedFolderPath.observe(viewLifecycleOwner, { folderPath ->
            setFolderPath(folderPath)
        })

        mp3ListView = view.findViewById(R.id.mp3ListView)
        playPauseButton = view.findViewById(R.id.playPauseButton)

        mediaPlayer = MediaPlayer()
        fileList = ArrayList()

        populateMp3FilesAndFileList()

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            fileList
        )

        mp3ListView.adapter = adapter

        mp3ListView.setOnItemClickListener { parent, view, position, id ->
            onSongSelected(position)
        }

        playPauseButton.setOnClickListener {
            togglePlaybackState()
        }

        sharedPreferences = requireContext().getSharedPreferences("CycleMusicPrefs", Context.MODE_PRIVATE)
        val lastFolderPath = sharedPreferences.getString("LastFolderPath", null)
        if (lastFolderPath != null) {
            setFolderPath(lastFolderPath)
        }
        
        return view
    }

    private fun populateMp3FilesAndFileList() {
        if (folderPath != null) {
            mp3Files = fetchAllMp3Files(File(folderPath))
        } else {
            mp3Files = fetchAllMp3Files(Environment.getExternalStorageDirectory())
        }

        fileList.clear()
        for (file in mp3Files) {
            fileList.add(file.name)
        }
    }

    private fun fetchAllMp3Files(root: File): Array<File> {
        val mp3Files = ArrayList<File>()
        val files = root.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    mp3Files.addAll(fetchAllMp3Files(file))
                } else if (file.name.endsWith(".mp3", ignoreCase = true)) {
                    mp3Files.add(file)
                }
            }
        }
        return mp3Files.toTypedArray()
    }

    private fun playSelectedFile(selectedFile: File) {
        mediaPlayer.apply {
            stop()
            reset()
            setDataSource(selectedFile.path)
            prepare()
            start()
        }
        playPauseButton.text = getString(R.string.pause_text)
    }

    private fun togglePlaybackState() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            playPauseButton.text = getString(R.string.play_text)
        } else {
            mediaPlayer.start()
            playPauseButton.text = getString(R.string.pause_text)
        }
    }

    private fun setFolderPath(folderPath: String) {
        this.folderPath = folderPath
        sharedPreferences.edit().putString("LastFolderPath", folderPath).apply()
        if (isAdded) {
            populateMp3FilesAndFileList()
            (mp3ListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        }
    }
    fun updateFileList() {
        viewModel.selectedFolderPath.value?.let {
            setFolderPath(it)
        }
    }

    private fun onSongSelected(position: Int) {
        try {
            mediaPlayer.stop()
            mediaPlayer.reset()

            val selectedFile = mp3Files[position]
            mediaPlayer.setDataSource(selectedFile.path)
            mediaPlayer.prepare()
            mediaPlayer.start()
            playPauseButton.text = getString(R.string.pause_text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            playPauseButton.text = getString(R.string.play_text)
        }
    }

    override fun onResume() {
        super.onResume()
        updateFileList()
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            playPauseButton.text = getString(R.string.pause_text)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
    }
}
