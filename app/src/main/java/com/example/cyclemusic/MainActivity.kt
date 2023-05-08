package com.example.cyclemusic

import MainPagerAdapter
import OnFolderSelectedListener
import PlaybackFragment
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.cyclemusic.databinding.ActivityMainBinding
private val requestCodePermissionAudio = 1
private val requestCodePermissionStorage = 2

private const val TAG = "MainActivity"

// MainActivityクラスはAppCompatActivityを継承します
@UnstableApi
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : AppCompatActivity(), OnFolderSelectedListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ストレージへのアクセス許可をリクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                requestCodePermissionAudio
            )
        }


        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            requestCodePermissionStorage
        )

        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        setupViewPager()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               
            } else {
                // Permission denied
                if (requestCode == requestCodePermissionAudio) {
                    Log.w(TAG, "Audio Permission denied")
                }
                if (requestCode == requestCodePermissionStorage) {
                    Log.w(TAG, "Storage Permission denied")
                }
            }
        
    }

    override fun onFolderSelected(folderPath: String) {
        val playbackFragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem) as PlaybackFragment
        playbackFragment.setFolderPath(folderPath)
    }

    private fun setupViewPager() {
        val adapter = MainPagerAdapter(supportFragmentManager, lifecycle)
        viewPager.adapter = adapter

        // Add the ViewPager to the TabLayout
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "再生"
                1 -> tab.text = "フォルダ選択"
            }
        }.attach()
    }
}
