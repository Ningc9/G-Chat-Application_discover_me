package com.syedsaifhossain.g_chatapplication

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.syedsaifhossain.g_chatapplication.adapter.ChatMessageAdapter
import com.syedsaifhossain.g_chatapplication.databinding.FragmentChatScreenBinding
import com.syedsaifhossain.g_chatapplication.models.ChatModel
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.google.GoogleEmojiProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.os.Environment
// Added Imports
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide // Keep Glide import for later use in RecyclerView
import com.syedsaifhossain.g_chatapplication.adapter.ChatAdapter
import com.syedsaifhossain.g_chatapplication.models.Chats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.TimerTask

// Ensure the class declaration includes the interface implementation from your original
class ChatScreenFragment : Fragment(){

    private var _binding: FragmentChatScreenBinding? = null
    private val binding get() = _binding!!

    // Original Variables
    private lateinit var chatMessageAdapter: ChatMessageAdapter
    private val chatList = mutableListOf<ChatModel>()
    private lateinit var currentUserId: String
    private lateinit var emojiPopup: EmojiPopup
    // Variables from original code for image/camera handling (kept)
    private var selectedImageUri: Uri? = null
    private var currentPhotoPath: String? = null
    private var selectedCameraPhotoUri: Uri? = null
    // Variables from original code for audio (kept)
    private var audioFile: File? = null

    // --- ADDED Member Variables for Arguments (Nullable) ---
    private var otherUserId: String? = null
    private var otherUserName: String? = null
    private var otherUserAvatarUrl: String? = null // Still needed for RecyclerView later
    private var myAvatarUrl: String? = null // Still needed for RecyclerView later
    // --- END Member Variables ---
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String = ""
    private var isRecording = false
    private var recordStartTime = 0L
    private var recordingTimer: Timer? = null
    private var elapsedTime: Long = 0

    private val messages = mutableListOf<Chats>()
    private lateinit var chatAdapter: ChatAdapter


    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(requireContext(), "Audio permission is required", Toast.LENGTH_SHORT).show()
        }
    }


    // Original ActivityResultLaunchers (kept)
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadImageToFirebase(uri)
            }
        }
    }
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                val photoFile = File(path)
                val photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    photoFile
                )
                selectedCameraPhotoUri = photoUri
                binding.chatMessageInput.setText("📷 Photo taken. Press Enter to send.")
            }
        }
    }
    // --- END ActivityResultLaunchers ---

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Receive Arguments (Revised) ---
        arguments?.let { bundle ->
            otherUserId = bundle.getString("otherUserId")
            otherUserName = bundle.getString("otherUserName")
            otherUserAvatarUrl = bundle.getString("otherUserAvatarUrl")
            myAvatarUrl = bundle.getString("myAvatarUrl")
            Log.d("ChatScreenFragment", "Arguments received: otherUserId=$otherUserId, otherUserName=$otherUserName, otherUserAvatarUrl=$otherUserAvatarUrl, myAvatarUrl=$myAvatarUrl")
        }

        // Check if essential arguments were received
        if (otherUserId == null || otherUserName == null) {
            Log.e("ChatScreenFragment", "Essential arguments (otherUserId or otherUserName) are missing!")
            Toast.makeText(requireContext(), "Error loading chat information.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack() // Go back if essential info is missing
            return // Stop further execution
        }
        // --- END Receive Arguments ---

        // Initialize EmojiManager (Original)
        EmojiManager.install(GoogleEmojiProvider())

        // Get current user ID (Original)
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        if (currentUserId == "anonymous") {
            Log.e("ChatScreenFragment", "Current user is anonymous!")
            // Consider stronger error handling
        }

        // --- MODIFIED: Setup RecyclerView Adapter to pass avatar URLs ---
        chatMessageAdapter = ChatMessageAdapter(chatList, currentUserId, myAvatarUrl, otherUserAvatarUrl)
        // --- END MODIFICATION ---

        binding.chatScreenRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatMessageAdapter
        }

        // Setup Emoji Popup (Original)
        emojiPopup = EmojiPopup.Builder.fromRootView(binding.root)
            .setOnEmojiPopupShownListener { /* Original listener if any */ }
            .setOnEmojiPopupDismissListener { /* Original listener if any */ }
            .setOnEmojiClickListener { _, emoji ->
                sendMessageToFirebase(emoji.unicode)
                emojiPopup.dismiss()
            }
            .build(binding.chatMessageInput)

        binding.imoButton.setOnClickListener {
            if (emojiPopup.isShowing) emojiPopup.dismiss()
            else emojiPopup.toggle()
        }

        // Start listening for messages (Original)
        listenForMessages()
        // Setup message input listener (Original)
        handleMessageSendOnEnter()

        // Setup back button listener (Original)
        binding.chatMessageBackImg.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.videoIcon.setOnClickListener {
            findNavController().navigate(R.id.action_chatScreenFragment_to_videoCallFragment)
        }

        binding.callIcon.setOnClickListener {
            findNavController().navigate(R.id.action_chatScreenFragment_to_voiceCallFragment)
        }

        // 绑定下方相机按钮点击事件
        binding.cameraIcon.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.chatAddButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(
                requireContext(),
                view,
                Gravity.NO_GRAVITY,
                0,
                R.style.PopupMenuStyle
            )

            popupMenu.inflate(R.menu.add_options_menu)

            // Force icons to show using reflection
            try {
                val fields = popupMenu.javaClass.declaredFields
                for (field in fields) {
                    if (field.name == "mPopup") {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popupMenu)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace() // Or log it using Log.e(...)
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.galleryId -> {
                        // Example: Handle permission properly using new APIs if targeting Android 13+
                        checkAndRequestPermission(Manifest.permission.READ_MEDIA_IMAGES)
                        true
                    }
                    R.id.documentId -> {
                        // Handle Document action
                        true
                    }
                    R.id.contactId -> {
                        // Handle Contact action
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }

        requestPermissionsIfNeeded()

        requestPermissionsIfNeeded()

        binding.chatMicButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkAudioPermission()) {
                        startRecording()
                    } else {
                        requestPermissionsIfNeeded()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }

        binding.chatSendButton.setOnClickListener {
            val message = binding.chatMessageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendTextMessage(message)
                binding.chatMessageInput.setText("")
            } else if (outputFile.isNotEmpty() && File(outputFile).exists()) {
                sendVoiceMessageWithCoroutine(outputFile)
                outputFile = ""
            } else {
                Toast.makeText(requireContext(), "Type a message or record voice first", Toast.LENGTH_SHORT).show()
            }
        }



        binding.tvToolbarUserName.text = otherUserName

        binding.moreIcon.setOnClickListener {
            findNavController().navigate(R.id.action_chatScreenFragment_to_chatScreenPageMoreOptionFragment)
        }

    } // End of onViewCreated


    private fun sendTextMessage(messageText: String) {
        val message = ChatModel(
            senderId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            message = messageText,
            timestamp = System.currentTimeMillis()
        )
        chatList.add(message)
        chatMessageAdapter.notifyItemInserted(chatList.size - 1)
        // 保存到Firebase
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val chatNodeId = getChatNodeId(senderId, otherUserId!!)
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatNodeId)
        val messageId = chatRef.push().key ?: return
        chatRef.child(messageId).setValue(message)
    }

    private fun sendVoiceMessage(audioUrl: String, duration: Int) {
        val message = ChatModel(
            senderId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            message = audioUrl, // 语音URL
            timestamp = System.currentTimeMillis(),
            type = "voice",     // 指定为语音消息
            duration = duration  // 录音时长
        )
        chatList.add(message)
        chatMessageAdapter.notifyItemInserted(chatList.size - 1)
        // 保存到Firebase
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val chatNodeId = getChatNodeId(senderId, otherUserId!!)
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatNodeId)
        val messageId = chatRef.push().key ?: return
        chatRef.child(messageId).setValue(message)
    }



    // --- NEW METHOD: Create a unique chat node ID ---
    private fun getChatNodeId(userId1: String, userId2: String): String {
        // Sort the user IDs alphabetically to ensure the same chat ID
        // regardless of who started the chat
        val userIds = sortedSetOf(userId1, userId2)
        return userIds.joinToString("_")
    }
    // --- END NEW METHOD ---

    // --- MODIFIED: Handle message sending with integrated audio handling ---
    private fun handleMessageSendOnEnter() {
        binding.chatMessageInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                //Voice messagge
                if (outputFile.isNotEmpty() && File(outputFile).exists()) {
                    sendVoiceMessageWithCoroutine(outputFile)
                    // Optional: reset outputFile after sending
                    outputFile = ""
                } else {
                    Toast.makeText(requireContext(), "No voice message recorded", Toast.LENGTH_SHORT).show()
                }
                // Handle photo/image sending
                if (selectedCameraPhotoUri != null) {
                    uploadImageToFirebase(selectedCameraPhotoUri!!)
                    selectedCameraPhotoUri = null
                    binding.chatMessageInput.setText("")
                } else if (selectedImageUri != null) {
                    uploadImageToFirebase(selectedImageUri!!)
                    selectedImageUri = null
                    binding.chatMessageInput.setText("")
                } else {
                    // Handle text message sending
                    val message = binding.chatMessageInput.text.toString().trim()
                    if (message.isNotEmpty()) {
                        sendMessageToFirebase(message)
                        binding.chatMessageInput.setText("")
                    }
                }
                true
            } else {
                false
            }
        }
    }
    // --- END MODIFICATION ---

    // --- MODIFIED: Send message to specific chat node ---
    private fun sendMessageToFirebase(messageText: String) {
        if (otherUserId == null) {
            Log.e("ChatScreenFragment", "Cannot send message, otherUserId is null.")
            Toast.makeText(requireContext(), "Error: Chat partner info missing.", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取当前登录用户的 UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e("ChatScreenFragment", "User not logged in")
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        val senderId = currentUser.uid

        // Create a specific chat node ID based on the two users involved
        val chatNodeId = getChatNodeId(senderId, otherUserId!!)
        Log.d("ChatScreenFragment", "Sending message to chat node: $chatNodeId")

        // Reference to the specific chat node
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatNodeId)

        val messageId = chatRef.push().key ?: return
        Log.d("ChatScreenFragment", "Generated message ID: $messageId")

        val message = ChatModel(
            senderId = senderId,  // 使用当前登录用户的 UID
            message = messageText,
            timestamp = System.currentTimeMillis()
        )
        Log.d("ChatScreenFragment", "Created message object: $message")

        chatRef.child(messageId).setValue(message)
            .addOnSuccessListener { 
                Log.d("ChatScreenFragment", "Message sent successfully to path: chats/$chatNodeId/$messageId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatScreenFragment", "Failed to send message to path: chats/$chatNodeId/$messageId", e)
                Toast.makeText(requireContext(), "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // --- END MODIFICATION ---

    // --- MODIFIED: Listen for messages from specific chat node ---
    private fun listenForMessages() {
        if (otherUserId == null) {
            Log.e("ChatScreenFragment", "Cannot listen for messages, otherUserId is null.")
            Toast.makeText(requireContext(), "Failed to load messages", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取当前登录用户的 UID
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e("ChatScreenFragment", "User not logged in")
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        val senderId = currentUser.uid

        // Create a specific chat node ID based on the two users involved
        val chatNodeId = getChatNodeId(senderId, otherUserId!!)
        Log.d("ChatScreenFragment", "Listening for messages in chat node: $chatNodeId")

        // Reference to the specific chat node
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatNodeId)

        chatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("ChatScreenFragment", "Received data snapshot: ${snapshot.exists()}")
                chatList.clear()
                for (child in snapshot.children) {
                    val message = child.getValue(ChatModel::class.java)
                    Log.d("ChatScreenFragment", "Message from Firebase: $message")
                    message?.let { chatList.add(it) }
                }
                Log.d("ChatScreenFragment", "Total messages in chatList: ${chatList.size}")
                chatList.sortBy { it.timestamp }
                chatMessageAdapter.notifyDataSetChanged()
                if (chatList.isNotEmpty()) {
                    binding.chatScreenRecyclerView.scrollToPosition(chatList.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load messages", Toast.LENGTH_SHORT).show()
                Log.e("ChatScreenFragment", "Failed to load messages", error.toException())
            }
        })
    }
    // --- END MODIFICATION ---


    private fun checkAndRequestPermission(permission: String) {
        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> openGallery()
            else -> requestPermissionLauncher.launch(permission)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    // --- MODIFIED: Upload image to Firebase with specific chat node ---
    private fun uploadImageToFirebase(imageUri: Uri) {
        try {
            if (imageUri == Uri.EMPTY) { Toast.makeText(requireContext(), "Invalid image selected", Toast.LENGTH_SHORT).show(); return }
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            if (inputStream == null) { Toast.makeText(requireContext(), "Cannot access image", Toast.LENGTH_SHORT).show(); return }
            inputStream.close()

            val compressedImageUri = compressImage(imageUri) ?: run { Toast.makeText(requireContext(), "Failed to compress", Toast.LENGTH_SHORT).show(); return }

            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("chat_images/${UUID.randomUUID()}")
            _binding?.progressBar?.visibility = View.VISIBLE
            val mimeType = requireContext().contentResolver.getType(compressedImageUri) ?: "image/jpeg"
            val extension = mimeType.substringAfterLast('/') ?: "jpg"
            val finalImageRef = imageRef.child("image.$extension")
            val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(mimeType).build()

            finalImageRef.putFile(compressedImageUri, metadata)
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    _binding?.progressBar?.progress = progress
                }
                .addOnSuccessListener {
                    finalImageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        if (otherUserId == null) { Log.e("ChatScreenFragment", "Cannot send image message, otherUserId is null."); _binding?.progressBar?.visibility = View.GONE; return@addOnSuccessListener }

                        // Create a specific chat node ID based on the two users involved
                        val chatNodeId = getChatNodeId(currentUserId, otherUserId!!)

                        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatNodeId)
                        val messageId = chatRef.push().key ?: return@addOnSuccessListener
                        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
                        val message = ChatModel(
                            senderId = senderId,
                            message = "📷 Image",
                            imageUrl = downloadUrl.toString(),
                            type = "image",
                            timestamp = System.currentTimeMillis()
                        )
                        chatRef.child(messageId).setValue(message)
                            .addOnSuccessListener { Toast.makeText(requireContext(), "Image sent", Toast.LENGTH_SHORT).show(); Log.d("ChatScreenFragment", "Image message sent") }
                            .addOnFailureListener { e -> Toast.makeText(requireContext(), "Failed to send image message: ${e.message}", Toast.LENGTH_SHORT).show(); Log.e("ChatScreenFragment", "Failed to send image message", e) }
                        _binding?.progressBar?.visibility = View.GONE
                    }.addOnFailureListener { e -> Toast.makeText(requireContext(), "Failed to get image URL: ${e.message}", Toast.LENGTH_SHORT).show(); _binding?.progressBar?.visibility = View.GONE; Log.e("ChatScreenFragment", "Failed to get download URL", e) }
                }
                .addOnFailureListener { e ->
                    val errorMessage = "Failed to upload: ${e.message}" // Simplified error
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                    _binding?.progressBar?.visibility = View.GONE
                    Log.e("ChatScreenFragment", "Failed to upload image", e)
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
            _binding?.progressBar?.visibility = View.GONE
            Log.e("ChatScreenFragment", "Exception during image upload", e)
        }
    }
    // --- END MODIFICATION ---

    private fun compressImage(imageUri: Uri): Uri? {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap == null) return null

            val maxDimension = 1024
            val width = bitmap.width; val height = bitmap.height
            var newWidth = width; var newHeight = height
            if (width > height && width > maxDimension) { newWidth = maxDimension; newHeight = (height.toFloat() * maxDimension / width).toInt() }
            else if (height > maxDimension) { newHeight = maxDimension; newWidth = (width.toFloat() * maxDimension / height).toInt() }

            val compressedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            bitmap.recycle()

            val tempFile = File.createTempFile("compressed_", ".jpg", requireContext().cacheDir)
            val outputStream = java.io.FileOutputStream(tempFile)
            compressedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush(); outputStream.close()
            compressedBitmap.recycle()
            return Uri.fromFile(tempFile)
        } catch (e: Exception) { Log.e("ChatScreenFragment", "Error compressing image", e); return null }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (requireActivity().packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                val photoFile: File? = try { createImageFile() } catch (ex: java.io.IOException) { Log.e("ChatScreenFragment", "Error creating image file", ex); null }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", it)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    cameraLauncher.launch(intent)
                } ?: run { Toast.makeText(requireContext(), "Error preparing camera", Toast.LENGTH_SHORT).show() }
            } else { Toast.makeText(requireContext(), "No camera app found", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { Toast.makeText(requireContext(), "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show(); Log.e("ChatScreenFragment", "Error opening camera", e) }
    }

    @Throws(java.io.IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        storageDir?.mkdirs() // Ensure directory exists
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply { currentPhotoPath = absolutePath }
    }


    private fun requestPermissionsIfNeeded() {
        val neededPermissions = mutableListOf<String>()
        if (!checkAudioPermission()) neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        if (neededPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun startRecording() {
        try {
            Log.d("VoiceDebug", "准备开始录音，准备文件路径...")
            // Prepare recording file
            val dirPath = "${requireContext().externalCacheDir?.absolutePath}/voiceMessages"
            val dir = File(dirPath)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "voice_${System.currentTimeMillis()}.3gp"
            outputFile = "$dirPath/$fileName"
            Log.d("VoiceDebug", "录音文件路径: $outputFile")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile)
                prepare()
                start()
            }

            isRecording = true
            recordStartTime = System.currentTimeMillis()
            Log.d("VoiceDebug", "录音已开始，时间戳: $recordStartTime")

            // Show the recording indicator (text and mic icon)
            binding.recordingIndicatorLayout.visibility = View.VISIBLE
            binding.recordingStatusText.visibility = View.VISIBLE
            binding.recordingMicIcon.visibility = View.VISIBLE

            // Start a timer to update the recording time
            startTimer()

            Toast.makeText(requireContext(), "Recording Started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("VoiceDebug", "录音启动失败: ${e.message}")
            Toast.makeText(requireContext(), "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTimer() {
        recordingTimer = Timer()
        recordingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                elapsedTime = System.currentTimeMillis() - recordStartTime
                val seconds = (elapsedTime / 1000).toInt()
                // Update the UI with the elapsed time
                activity?.runOnUiThread {
                    binding.recordingStatusText.text = "Recording... $seconds sec"
                }
            }
        }, 0, 1000) // Update every second
    }

    private fun stopRecording() {
        try {
            val elapsed = System.currentTimeMillis() - recordStartTime
            Log.d("VoiceDebug", "准备停止录音，录音时长: ${elapsed}ms")
            if (elapsed < 1000) { // If recording was too short
                Toast.makeText(requireContext(), "Recording too short", Toast.LENGTH_SHORT).show()
                try {
                    Log.d("VoiceDebug", "调用 release() 释放 MediaRecorder（录音过短）")
                    mediaRecorder?.release()
                } catch (e: Exception) {
                    Log.e("VoiceDebug", "录音过短时 release() 异常: ${e.message}")
                }
                mediaRecorder = null
                isRecording = false
                // Hide the recording indicator
                binding.recordingIndicatorLayout.visibility = View.GONE
                Log.d("VoiceDebug", "录音过短，已取消")
                return
            }

            try {
                Log.d("VoiceDebug", "调用 stop() 停止 MediaRecorder")
                mediaRecorder?.stop()
                Log.d("VoiceDebug", "stop() 调用成功")
            } catch (e: Exception) {
                Log.e("VoiceDebug", "stop() 调用异常: ${e.message}")
            }
            try {
                Log.d("VoiceDebug", "调用 release() 释放 MediaRecorder")
                mediaRecorder?.release()
                Log.d("VoiceDebug", "release() 调用成功")
            } catch (e: Exception) {
                Log.e("VoiceDebug", "release() 调用异常: ${e.message}")
            }
            isRecording = false

            // Hide the recording indicator and stop timer
            binding.recordingIndicatorLayout.visibility = View.GONE
            recordingTimer?.cancel()

            // 打印录音文件大小
            val file = File(outputFile)
            Log.d("VoiceDebug", "录音结束，文件路径: $outputFile, 文件大小: ${file.length()} 字节")

            Toast.makeText(requireContext(), "Recording Stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("VoiceDebug", "录音停止失败: ${e.message}")
            Toast.makeText(requireContext(), "Failed to stop recording: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            mediaRecorder = null
        }
    }


    private fun sendVoiceMessageWithCoroutine(filePath: String) {
        val storageRef = FirebaseStorage.getInstance().reference
        val audioRef = storageRef.child("voiceMessages/${File(filePath).name}")
        val duration = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt()
        val file = File(filePath)
        Log.d("VoiceDebug", "准备上传录音文件: $filePath, 文件大小: ${file.length()} 字节, 时长: ${duration}s")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                audioRef.putFile(Uri.fromFile(File(filePath))).await()
                val downloadUrl = audioRef.downloadUrl.await()
                withContext(Dispatchers.Main) {
                    Log.d("VoiceDebug", "录音文件上传成功，downloadUrl: $downloadUrl")
                    sendVoiceMessage(downloadUrl.toString(), duration)
                    Toast.makeText(requireContext(), "Voice message sent!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("VoiceDebug", "录音文件上传失败: ${e.message}")
                    Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
        audioFile = null
        _binding = null
    }
}