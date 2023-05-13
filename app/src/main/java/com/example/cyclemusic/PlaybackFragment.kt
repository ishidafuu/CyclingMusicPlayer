import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.cyclemusic.R
import java.io.File
import java.io.FilenameFilter
import java.util.ArrayList
import androidx.fragment.app.activityViewModels

class PlaybackFragment : Fragment() , OnFolderSelectedListener{

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mp3ListView: ListView
    private lateinit var mp3Files: Array<File>
    private lateinit var fileList: MutableList<String>
    private val viewModel: SharedViewModel by activityViewModels()
    private var folderPath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.playback_fragment, container, false)
        super.onViewCreated(view, savedInstanceState)

        viewModel.selectedFolderPath.observe(viewLifecycleOwner, { folderPath ->
            setFolderPath(folderPath)
        })

        mp3ListView = view.findViewById(R.id.mp3ListView)

        mediaPlayer = MediaPlayer()
        fileList = ArrayList()

        populateMp3FilesAndFileList()

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            fileList
        )

        mp3ListView.adapter = adapter

        mp3ListView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                    mediaPlayer.reset()
                }

                val selectedFile = mp3Files[position]
                mediaPlayer.setDataSource(selectedFile.path)
                mediaPlayer.prepare()
                mediaPlayer.start()

                Toast.makeText(
                    requireContext(),
                    "Now playing: ${selectedFile.name}",
                    Toast.LENGTH_SHORT
                ).show()
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

    fun setFolderPath(folderPath: String) {
        this.folderPath = folderPath
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

    override fun onFolderSelected(folderPath: String) {
        setFolderPath(folderPath)
    }

    
    override fun onPause() {
        super.onPause()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        updateFileList()
//        if (!mediaPlayer.isPlaying) {
//            mediaPlayer.start()
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
    }
}
