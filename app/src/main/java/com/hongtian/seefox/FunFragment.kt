package com.hongtian.seefox

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import kotlin.random.Random
import java.io.File

class FunFragment : Fragment() {

    private val urls = mutableListOf<String>()
    private val localImages = mutableListOf<String>()
    private val albumFolders = mutableListOf<Triple<String, Int, String>>() // 文件夹路径, 图片数量, 第一张图片路径
    private var currentLocalPath = ""
    private var showAlbumMode = false // false=图片网格, true=文件夹列表
    private lateinit var recycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabOnline: TextView
    private lateinit var tabLocal: TextView
    private lateinit var btnAddPath: TextView
    private lateinit var btnToggleView: TextView
    private lateinit var adapter: WallpaperAdapter
    private var seedBase = 0
    private var currentTab = 0 // 0=网络图, 1=本地图
    private var isListView = false // 本地图是否列表视图
    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    // 记录每个标签的滚动状态
    private var onlineScrollState: android.os.Parcelable? = null
    private var localScrollState: android.os.Parcelable? = null
    // 记录本地图的浏览状态
    private var localShowAlbumMode = true
    private var localCurrentPath = ""
    private var localImagesCache = mutableListOf<String>()
    // 加密文件夹: 路径 -> 密码
    private val lockedFolders = mutableMapOf<String, String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_fun, c, false)

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (!isAdded || currentTab != 0) return
            // 判断是否滑到底部
            val layoutManager = rv.layoutManager as? GridLayoutManager ?: return
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            val total = layoutManager.itemCount
            if (lastVisible >= total - 3) {
                scrollRunnable?.let { handler.removeCallbacks(it) }
                scrollRunnable = Runnable { loadMore() }
                handler.postDelayed(scrollRunnable!!, 300)
            }
        }
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        swipeRefresh = v.findViewById(R.id.swipeRefresh)
        recycler = v.findViewById(R.id.wallpaperRecycler)
        tabOnline = v.findViewById(R.id.tabOnline)
        tabLocal = v.findViewById(R.id.tabLocal)
        btnAddPath = v.findViewById(R.id.btnAddPath)
        btnToggleView = v.findViewById(R.id.btnToggleView)
        btnAddPath.visibility = View.GONE
        btnToggleView.visibility = View.GONE

        // 读取加密文件夹
        val prefs = requireContext().getSharedPreferences("seefox_prefs", android.content.Context.MODE_PRIVATE)
        val savedLocked = prefs.getString("locked_folders_map", "") ?: ""
        if (savedLocked.isNotEmpty()) {
            savedLocked.split("|").forEach { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) {
                    lockedFolders[parts[0]] = parts[1]
                }
            }
        }

        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = WallpaperAdapter(emptyList(), { url ->
            if (showAlbumMode) {
                // 文件夹模式，点击进入文件夹
                if (lockedFolders.containsKey(url)) {
                    // 加密文件夹，先验证密码
                    showPasswordDialog(url)
                } else {
                    enterFolder(url)
                }
            } else {
                // 图片模式，点击预览大图
                showFullImage(url)
            }
        }, onLongClick = { url ->
            // 长按文件夹：加密/解密
            showFolderLockDialog(url)
        })
        recycler.adapter = adapter

        tabOnline.setOnClickListener { switchTab(0) }
        tabLocal.setOnClickListener { switchTab(1) }
        btnAddPath.setOnClickListener { showPathInputDialog() }
        btnToggleView.setOnClickListener {
            if (currentTab != 1) return@setOnClickListener
            isListView = !isListView
            btnToggleView.text = if (isListView) "▦" else "☰"
            // 列表视图用1列，网格视图用2列
            (recycler.layoutManager as GridLayoutManager).spanCount = if (isListView) 1 else 2
            adapter.setListViewMode(isListView)
        }

        swipeRefresh.setOnRefreshListener {
            refreshContent()
            swipeRefresh.isRefreshing = false
        }

        recycler.isNestedScrollingEnabled = true
        recycler.post {
            loadMore()
        }
    }

    override fun onResume() {
        super.onResume()
        recycler.clearOnScrollListeners()
        recycler.addOnScrollListener(scrollListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 清除所有延迟任务，防止Fragment销毁后还回调
        scrollRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
    }

    private fun switchTab(tab: Int) {
        if (tab == currentTab) return
        // 保存当前标签的滚动状态
        if (currentTab == 0) {
            onlineScrollState = recycler.layoutManager?.onSaveInstanceState()
        } else {
            localScrollState = recycler.layoutManager?.onSaveInstanceState()
            localShowAlbumMode = showAlbumMode
            localCurrentPath = currentLocalPath
            localImagesCache = localImages.toMutableList()
        }

        currentTab = tab
        if (tab == 0) {
            tabOnline.setBackgroundResource(R.drawable.bg_fun_tab_selected)
            tabOnline.setTextColor(0xFFFFFFFF.toInt())
            tabLocal.setBackgroundResource(android.R.color.transparent)
            tabLocal.setTextColor(0xFFAAAAAA.toInt())
            btnAddPath.visibility = View.GONE
            btnToggleView.visibility = View.GONE
            // 恢复网络图缓存数据和滚动位置
            showAlbumMode = false
            (recycler.layoutManager as GridLayoutManager).spanCount = 2
            if (urls.isNotEmpty()) {
                adapter.updateData(urls, isAlbum = false)
                recycler.layoutManager?.onRestoreInstanceState(onlineScrollState)
            } else {
                loadMore()
            }
            // 确保滚动监听注册着
            recycler.clearOnScrollListeners()
            recycler.addOnScrollListener(scrollListener)
        } else {
            tabLocal.setBackgroundResource(R.drawable.bg_fun_tab_selected)
            tabLocal.setTextColor(0xFFFFFFFF.toInt())
            tabOnline.setBackgroundResource(android.R.color.transparent)
            tabOnline.setTextColor(0xFFAAAAAA.toInt())
            btnAddPath.visibility = View.VISIBLE
            btnToggleView.visibility = View.VISIBLE
            // 本地图：恢复之前的浏览状态
            showAlbumMode = localShowAlbumMode
            currentLocalPath = localCurrentPath
            if (showAlbumMode) {
                // 文件夹列表
                if (albumFolders.isNotEmpty()) {
                    val paths = albumFolders.map { it.first }
                    val counts = albumFolders.map { it.second }
                    adapter.updateData(paths, isAlbum = true, counts = counts)
                    recycler.layoutManager?.onRestoreInstanceState(localScrollState)
                } else {
                    loadLocalImages()
                }
            } else {
                // 图片模式（之前正在浏览某个文件夹）
                if (localImagesCache.isNotEmpty()) {
                    adapter.updateData(localImagesCache, isAlbum = false)
                    recycler.layoutManager?.onRestoreInstanceState(localScrollState)
                } else {
                    showAlbumMode = true
                    loadLocalImages()
                }
            }
        }
    }

    private fun refreshContent() {
        when (currentTab) {
            0 -> {
                seedBase = Random.nextInt(10000)
                urls.clear()
                adapter.updateData(urls, isAlbum = false)
                loadMore()
            }
            1 -> {
                // 下拉刷新：清空缓存，重新扫描
                albumFolders.clear()
                localImages.clear()
                showAlbumMode = true
                loadLocalImages()
            }
        }
    }

    private fun loadLocalImages() {
        // 检查权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (requireActivity().checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requireActivity().requestPermissions(
                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                    1001
                )
                return
            }
        } else {
            if (requireActivity().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requireActivity().requestPermissions(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    1001
                )
                return
            }
        }

        if (showAlbumMode) {
            // 显示文件夹列表
            loadAlbumFolders()
        } else {
            // 显示当前文件夹的图片
            loadImagesFromCurrentFolder()
        }
    }

    private fun loadAlbumFolders() {
        albumFolders.clear()
        try {
            val rootDir = Environment.getExternalStorageDirectory()
            scanFolders(rootDir, 0)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        val paths = albumFolders.map { it.first }
        val counts = albumFolders.map { it.second }
        val thumbs = albumFolders.map { it.third }
        adapter.updateData(paths, isAlbum = true, counts = counts)
        adapter.setAlbumThumbs(thumbs)
        adapter.setLockedSet(lockedFolders.keys)
        Toast.makeText(requireContext(), "共 ${albumFolders.size} 个相册", Toast.LENGTH_SHORT).show()
    }

    private fun scanFolders(dir: File, depth: Int) {
        if (depth > 3) return // 最多扫描3层
        if (!dir.exists() || !dir.isDirectory) return

        var imageCount = 0
        var firstImage = ""
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanFolders(file, depth + 1)
            } else {
                val name = file.name.lowercase()
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")
                ) {
                    imageCount++
                    if (firstImage.isEmpty()) firstImage = file.absolutePath
                }
            }
        }

        if (imageCount > 0) {
            albumFolders.add(Triple(dir.absolutePath, imageCount, firstImage))
        }
    }

    private fun loadImagesFromCurrentFolder() {
        localImages.clear()
        try {
            if (currentLocalPath.isEmpty()) {
                currentLocalPath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "seefox_wallpaper"
                ).absolutePath
            }
            val dir = File(currentLocalPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    val name = file.name.lowercase()
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                        name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp") ||
                        name.endsWith(".heic") || name.endsWith(".heif")
                    ) {
                        localImages.add(file.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "读取本地图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        adapter.updateData(localImages, isAlbum = false)
        Toast.makeText(requireContext(), "找到 ${localImages.size} 张图片", Toast.LENGTH_SHORT).show()
    }

    private fun showPathDialog() {
        val currentPath = if (currentLocalPath.isEmpty()) "未选择路径" else currentLocalPath
        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle("当前本地图片路径")
            .setMessage("$currentPath\n\n共 ${localImages.size} 张图片")
            .setPositiveButton("选择路径") { _, _ ->
                showPathInputDialog()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun onBackPressed(): Boolean {
        return if (currentTab == 1 && !showAlbumMode) {
            // 在图片模式，返回文件夹列表（用缓存，不重新扫描）
            showAlbumMode = true
            localShowAlbumMode = true
            if (albumFolders.isNotEmpty()) {
                val paths = albumFolders.map { it.first }
                val counts = albumFolders.map { it.second }
                adapter.updateData(paths, isAlbum = true, counts = counts)
            } else {
                loadLocalImages()
            }
            true
        } else {
            false
        }
    }

    private fun enterFolder(url: String) {
        currentLocalPath = url
        showAlbumMode = false
        localShowAlbumMode = false
        localCurrentPath = url
        loadLocalImages()
    }

    private fun showPasswordDialog(folderPath: String) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "输入密码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFF999999.toInt())
        }
        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle("文件夹已加密")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val pwd = input.text.toString()
                if (pwd == lockedFolders[folderPath]) {
                    enterFolder(folderPath)
                } else {
                    Toast.makeText(requireContext(), "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFolderLockDialog(folderPath: String) {
        val isLocked = lockedFolders.containsKey(folderPath)
        val items = arrayOf(if (isLocked) "解密此文件夹" else "加密此文件夹")
        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle(File(folderPath).name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        if (isLocked) {
                            // 解密：先验证密码
                            showUnlockPasswordDialog(folderPath)
                        } else {
                            // 加密：设置新密码
                            showSetPasswordDialog(folderPath)
                        }
                    }
                }
            }
            .show()
    }

    private fun showUnlockPasswordDialog(folderPath: String) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "输入密码解密"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFF999999.toInt())
        }
        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle("解密文件夹")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val pwd = input.text.toString()
                if (pwd == lockedFolders[folderPath]) {
                    lockedFolders.remove(folderPath)
                    saveLockedFolders()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "已解密", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSetPasswordDialog(folderPath: String) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "设置4位数字密码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFF999999.toInt())
        }
        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle("加密此文件夹")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val pwd = input.text.toString()
                if (pwd.length == 4) {
                    lockedFolders[folderPath] = pwd
                    saveLockedFolders()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "加密成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "密码必须4位数字", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveLockedFolders() {
        val sb = StringBuilder()
        lockedFolders.forEach { (path, pwd) ->
            if (sb.isNotEmpty()) sb.append("|")
            sb.append(path).append("=").append(pwd)
        }
        requireContext().getSharedPreferences("seefox_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("locked_folders_map", sb.toString()).apply()
    }

    private fun showPathInputDialog() {
        // 读取保存的自定义路径
        val prefs = requireContext().getSharedPreferences("seefox_prefs", android.content.Context.MODE_PRIVATE)
        val savedPaths = prefs.getStringSet("custom_paths", setOf())?.toMutableSet() ?: mutableSetOf()

        val defaultPaths = arrayOf(
            "Pictures/seefox_wallpaper (默认壁纸)",
            "Pictures (相册)",
            "DCIM/Camera (相机照片)",
            "Download (下载)",
            "Pictures/Screenshots (截图)"
        )

        // 合并默认路径和保存的路径
        val allPaths = mutableListOf<String>()
        allPaths.addAll(defaultPaths)
        allPaths.addAll(savedPaths)
        allPaths.add("➕ 添加新文件夹")

        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.item_dialog_folder,
            R.id.text1,
            allPaths
        )

        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle("选择图片文件夹")
            .setAdapter(adapter) { dialog, which ->
                when {
                    which < 5 -> {
                        // 默认路径
                        val pathMap = mapOf(
                            0 to "Pictures/seefox_wallpaper",
                            1 to "Pictures",
                            2 to "DCIM/Camera",
                            3 to "Download",
                            4 to "Pictures/Screenshots"
                        )
                        val subPath = pathMap[which] ?: ""
                        currentLocalPath = Environment.getExternalStorageDirectory().absolutePath + "/" + subPath
                        loadLocalImages()
                    }
                    which < allPaths.size - 1 -> {
                        // 自定义路径
                        currentLocalPath = savedPaths.elementAt(which - 5)
                        loadLocalImages()
                    }
                    else -> {
                        // 添加新文件夹
                        showManualPathDialog()
                    }
                }
            }
            .show()
    }

    private fun showManualPathDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "输入图片文件夹完整路径"
            setText(currentLocalPath)
        }
        AlertDialog.Builder(requireContext(), R.style.Dialog_TransparentRounded)
            .setTitle("手动输入路径")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) {
                    currentLocalPath = path
                    // 保存到 SharedPreferences
                    val prefs = requireContext().getSharedPreferences("seefox_prefs", android.content.Context.MODE_PRIVATE)
                    val savedPaths = prefs.getStringSet("custom_paths", setOf())?.toMutableSet() ?: mutableSetOf()
                    savedPaths.add(path)
                    prefs.edit().putStringSet("custom_paths", savedPaths).apply()
                    loadLocalImages()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadMore() {
        if (!isAdded || currentTab != 0) return
        val startPos = urls.size
        val newUrls = mutableListOf<String>()
        for (i in 0 until 10) {
            val seed = "wp${seedBase}_${startPos + i}_${Random.nextInt(100000)}"
            newUrls.add("https://picsum.photos/seed/$seed/400/300")
        }
        urls.addAll(newUrls)
        adapter.updateData(urls, isAlbum = false)
    }

    private fun showFullImage(url: String) {
        if (!isAdded) return
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_full_image, null)
        val viewPager = dialogView.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val loading = dialogView.findViewById<android.widget.ProgressBar>(R.id.loading)
        val btnDownload = dialogView.findViewById<TextView>(R.id.btnDownload)
        val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)
        val btnPrev = dialogView.findViewById<TextView>(R.id.btnPrev)
        val btnNext = dialogView.findViewById<TextView>(R.id.btnNext)

        val currentList = if (currentTab == 0) urls else localImages
        val startIndex = currentList.indexOf(url).coerceAtLeast(0)

        val pagerAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val iv = ZoomableImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF000000.toInt())
                }
                return object : RecyclerView.ViewHolder(iv) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val iv = holder.itemView as ImageView
                val imgPath = currentList[position]
                if (currentTab == 0) {
                    val bigUrl = imgPath.replace("/400/300", "/1080/1920")
                    Glide.with(ctx).load(bigUrl).into(iv)
                } else {
                    Glide.with(ctx).load(android.net.Uri.fromFile(File(imgPath))).into(iv)
                }
            }
            override fun getItemCount() = currentList.size
        }
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(startIndex, false)

        loading.postDelayed({ loading.visibility = View.GONE }, 1500)

        btnPrev.setOnClickListener {
            viewPager.currentItem = viewPager.currentItem - 1
        }
        btnNext.setOnClickListener {
            viewPager.currentItem = viewPager.currentItem + 1
        }

        val dialog = android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar)
        dialog.setContentView(dialogView)
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )

        btnDownload.setOnClickListener {
            if (currentTab == 0) {
                downloadImage(currentList[viewPager.currentItem])
            } else {
                Toast.makeText(ctx, "已是本地图片", Toast.LENGTH_SHORT).show()
            }
        }
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun downloadImage(url: String) {
        try {
            val ctx = requireContext()
            val bigUrl = url.replace("/400/300", "/1080/1920")
            val request = DownloadManager.Request(Uri.parse(bigUrl))
                .setTitle("壁纸_${System.currentTimeMillis()}.jpg")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    "seefox_wallpaper/wallpaper_${System.currentTimeMillis()}.jpg"
                )
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(ctx, "开始下载，保存到 Pictures/seefox_wallpaper/", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    class WallpaperAdapter(
        private var items: List<String>,
        private val onClick: (String) -> Unit,
        private val onLongClick: ((String) -> Unit)? = null,
        private var isAlbumMode: Boolean = false,
        private var albumCounts: List<Int> = emptyList(),
        private var albumThumbs: List<String> = emptyList(),
        private var lockedSet: Set<String> = emptySet(),
        private var listViewMode: Boolean = false
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_IMAGE = 1
            private const val TYPE_ALBUM = 2
            private const val TYPE_LIST = 3
        }

        fun updateData(newItems: List<String>, isAlbum: Boolean = false, counts: List<Int> = emptyList()) {
            items = newItems
            isAlbumMode = isAlbum
            albumCounts = counts
            notifyDataSetChanged()
        }

        fun setAlbumThumbs(thumbs: List<String>) {
            albumThumbs = thumbs
            notifyDataSetChanged()
        }

        fun setLockedSet(locked: Set<String>) {
            lockedSet = locked
            notifyDataSetChanged()
        }

        fun setListViewMode(isList: Boolean) {
            listViewMode = isList
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when {
                isAlbumMode -> TYPE_ALBUM
                listViewMode -> TYPE_LIST
                else -> TYPE_IMAGE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_ALBUM -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_path_chooser, parent, false)
                    object : RecyclerView.ViewHolder(view) {}
                }
                TYPE_LIST -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_local_list, parent, false)
                    object : RecyclerView.ViewHolder(view) {}
                }
                else -> {
                    val screenW = parent.context.resources.displayMetrics.widthPixels
                    val cardW = screenW / 2 - 20
                    val iv = ImageView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(cardW, cardW * 3 / 4)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setPadding(4, 4, 4, 4)
                    }
                    object : RecyclerView.ViewHolder(iv) {}
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val url = items[position]
            when (getItemViewType(position)) {
                TYPE_ALBUM -> {
                    val view = holder.itemView
                    val textView = view.findViewById<TextView>(R.id.pathText)
                    val countView = view.findViewById<TextView>(R.id.pathCount)
                    val thumbView = view.findViewById<ImageView>(R.id.folderThumb)
                    val lockView = view.findViewById<TextView>(R.id.lockIcon)
                    val folderName = File(url).name
                    val count = albumCounts.getOrElse(position) { 0 }
                    textView.text = folderName
                    countView.text = "$count 张"
                    // 显示/隐藏锁图标
                    lockView.visibility = if (lockedSet.contains(url)) View.VISIBLE else View.GONE
                    val thumbPath = albumThumbs.getOrElse(position) { "" }
                    if (thumbPath.isNotEmpty()) {
                        Glide.with(thumbView.context)
                            .load(android.net.Uri.fromFile(File(thumbPath)))
                            .centerCrop()
                            .into(thumbView)
                    }
                    view.setOnClickListener { onClick(url) }
                    view.setOnLongClickListener {
                        onLongClick?.invoke(url)
                        true
                    }
                }
                TYPE_LIST -> {
                    val view = holder.itemView
                    val thumb = view.findViewById<ImageView>(R.id.thumbImage)
                    val tvName = view.findViewById<TextView>(R.id.tvName)
                    val tvInfo = view.findViewById<TextView>(R.id.tvInfo)
                    val file = File(url)
                    tvName.text = file.name
                    val sizeKB = file.length() / 1024
                    val sizeStr = if (sizeKB > 1024) "${sizeKB / 1024}MB" else "${sizeKB}KB"
                    tvInfo.text = sizeStr
                    Glide.with(thumb.context)
                        .load(android.net.Uri.fromFile(file))
                        .centerCrop()
                        .into(thumb)
                    view.setOnClickListener { onClick(url) }
                }
                else -> {
                    val iv = holder.itemView as ImageView
                    if (url.startsWith("/")) {
                        Glide.with(iv.context)
                            .load(android.net.Uri.fromFile(File(url)))
                            .into(iv)
                    } else {
                        Glide.with(iv.context).load(url).into(iv)
                    }
                    iv.setOnClickListener { onClick(url) }
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
