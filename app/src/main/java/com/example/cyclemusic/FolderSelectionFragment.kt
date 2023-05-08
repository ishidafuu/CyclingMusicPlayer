import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.cyclemusic.R
import java.io.File
import java.io.FileFilter

class FolderSelectionFragment : Fragment() {

    private lateinit var backButton: Button
    private lateinit var currentDir: File
    private lateinit var folderListView: ListView
    private lateinit var folderList: MutableList<String>
    private lateinit var folders: MutableList<File>

    private var listener: OnFolderSelectedListener? = null
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.folder_selection_fragment, container, false)
        
        backButton = view.findViewById(R.id.backButton)
        folderListView = view.findViewById(R.id.folderListView)
        folderList = ArrayList()
        folders = ArrayList()

        currentDir = Environment.getExternalStorageDirectory()
        populateFolderList(currentDir)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            folderList
        )

        folderListView.adapter = adapter

//        folderListView.onItemClickListener =
//            AdapterView.OnItemClickListener { _, _, position, _ ->
//                val selectedFolder = folders[position]
//                if (selectedFolder.isDirectory) {
//                    folderList.clear()
//                    folders.clear()
//                    populateFolderList(selectedFolder)
//                    adapter.notifyDataSetChanged()
//                    currentDir = selectedFolder
//                    val mp3Files = folders.filter { it.name.endsWith(".mp3", ignoreCase = true) }.toTypedArray()
//                    updateAdapter(mp3Files)
//
//                } else if (selectedFolder.name.endsWith(".mp3", ignoreCase = true)) {
////                    listener?.onFolderSelected(currentDir.absolutePath)
//                }
//            }

        folderListView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val selectedFolder = folders[position]
                if (selectedFolder.isDirectory) {
                    folderList.clear()
                    folders.clear()
                    currentDir = selectedFolder
                    populateFolderList(selectedFolder)

                    adapter.notifyDataSetChanged()
                    
//                    val mp3Files = folders.filter { it.name.endsWith(".mp3", ignoreCase = true) }.toTypedArray()
//                    updateAdapter(mp3Files)

//                    listener?.onFolderSelected(currentDir.absolutePath)
                    
                } else if (selectedFolder.name.endsWith(".mp3", ignoreCase = true)) {
//                    listener?.onFolderSelected(mp3Files)
                }
            }
        

        backButton.setOnClickListener {
            if (currentDir.path != Environment.getExternalStorageDirectory().path) {
                currentDir = currentDir.parentFile as File
                folderList.clear()
                folders.clear()
                populateFolderList(currentDir)
                adapter.notifyDataSetChanged()
//                listener?.onFolderSelected(currentDir.absolutePath)
            }
        }

        return view
    }

    private fun updateAdapter(mp3Files: Array<File>) {
        val mp3FileNames = mp3Files.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mp3FileNames)
        folderListView.adapter = adapter

        folderListView.setOnItemClickListener { _, _, position, _ ->
            val selectedFile = mp3Files[position]
            // ここで selectedFile を再生する処理を行います
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFolderSelectedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFolderSelectedListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
    
    private fun populateFolderList(root: File) {
        val files = root.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory || file.name.endsWith(".mp3", ignoreCase = true)) {
                    folderList.add(file.name)
                    folders.add(file)
                }
            }
        }
    }
}
