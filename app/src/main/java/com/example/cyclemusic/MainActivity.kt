package com.example.cyclemusic

import MainPagerAdapter
import OnBackPressed
import OnFolderSelectedListener
import PlaybackFragment
import SharedViewModel
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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.activity.viewModels
import androidx.navigation.fragment.NavHostFragment

private val requestCodePermissionAudio = 1
private val requestCodePermissionStorage = 2

private const val TAG = "MainActivity"

@UnstableApi
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : AppCompatActivity(), OnFolderSelectedListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private val viewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            // Permission granted
        } else {
            // Permission denied
            if (requestCode == requestCodePermissionAudio) {
                Log.w(TAG, getString(R.string.audio_permission_denied))
            }
            if (requestCode == requestCodePermissionStorage) {
                Log.w(TAG, getString(R.string.storage_permission_denied))
            }
        }
    }

    override fun onFolderSelected(folderPath: String) {
        viewModel.selectedFolderPath.value = folderPath
    }

    private fun setupViewPager() {
        val adapter = MainPagerAdapter(supportFragmentManager, lifecycle)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_playback)
                1 -> getString(R.string.tab_folder_selection)
                else -> null
            }
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    val playbackFragment = supportFragmentManager.findFragmentByTag("f$position") as PlaybackFragment
                    playbackFragment.updateFileList()
                }
            }
        })
    }

    override fun onBackPressed() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val navHostFragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
        if ((navHostFragment?.childFragmentManager?.fragments?.get(0) as? OnBackPressed)?.onBackPressed() != true) {
            super.onBackPressed()
        }
    }
}
