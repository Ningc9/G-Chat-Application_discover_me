package com.syedsaifhossain.g_chatapplication

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.syedsaifhossain.g_chatapplication.adapter.NewChatAdapter
import com.syedsaifhossain.g_chatapplication.databinding.FragmentNewChatsBinding
import com.syedsaifhossain.g_chatapplication.models.NewChatItem

class NewChatsFragment : Fragment() {

    private lateinit var binding: FragmentNewChatsBinding
    private lateinit var chatList: ArrayList<NewChatItem>
    private lateinit var adapter: NewChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentNewChatsBinding.inflate(inflater, container, false)
        // 示例数据，实际应从Firebase加载好友列表
        chatList = ArrayList<NewChatItem>().apply {
            add(NewChatItem("uid_1", "Friend 1", R.drawable.cityimg))
            add(NewChatItem("uid_2", "Friend 2", R.drawable.cityimg))
        }
        adapter = NewChatAdapter(chatList)
        binding.friendsList.layoutManager = LinearLayoutManager(requireContext())
        binding.friendsList.adapter = adapter

        // 群聊创建按钮（右上角）点击事件
        binding.doneButton.setOnClickListener {
            val selectedUids = chatList.filter { it.isSelected }.map { it.uid }
            if (selectedUids.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Please select at least one friend", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showGroupNameDialog { groupName ->
                createGroupInFirebase(groupName, selectedUids)
            }
        }

        binding.selectGroupText.setOnClickListener{
            findNavController().navigate(R.id.action_newChatsFragment_to_selectGroupFragment)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 加载真实好友列表
        loadFriends()
        // 其它初始化逻辑可保留
    }

    private fun loadFriends() {
        val currentUserUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val usersRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
        usersRef.child(currentUserUid).child("friends").get().addOnSuccessListener { snapshot ->
            val friendUids = snapshot.children.mapNotNull { it.key }
            android.util.Log.d("NewChatsFragment", "friendUids: $friendUids") // 调试日志
            if (friendUids.isEmpty()) {
                chatList.clear()
                adapter.notifyDataSetChanged()
                return@addOnSuccessListener
            }
            usersRef.get().addOnSuccessListener { usersSnap ->
                val newList = ArrayList<com.syedsaifhossain.g_chatapplication.models.NewChatItem>()
                for (uid in friendUids) {
                    val userSnap = usersSnap.child(uid)
                    android.util.Log.d("NewChatsFragment", "userSnap for $uid: ${userSnap.value}") // 调试日志
                    val name = userSnap.child("name").getValue(String::class.java) ?: "Unknown"
                    newList.add(com.syedsaifhossain.g_chatapplication.models.NewChatItem(uid, name, R.drawable.cityimg))
                }
                chatList.clear()
                chatList.addAll(newList)
                adapter.notifyDataSetChanged()
            }
        }
    }

    // 弹窗输入群名
    private fun showGroupNameDialog(onGroupNameEntered: (String) -> Unit) {
        val editText = android.widget.EditText(requireContext())
        editText.hint = "Enter group name"
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Group Name")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val groupName = editText.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    onGroupNameEntered(groupName)
                } else {
                    android.widget.Toast.makeText(requireContext(), "Group name cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 创建群组并跳转
    private fun createGroupInFirebase(groupName: String, memberUids: List<String>) {
        val currentUserUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val groupRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("groups").push()
        val groupId = groupRef.key ?: return
        val members = memberUids.associateWith { true }.toMutableMap()
        members[currentUserUid] = true // 把自己也加进去
        val groupData = mapOf(
            "name" to groupName,
            "owner" to currentUserUid,
            "members" to members,
            "createdAt" to System.currentTimeMillis()
        )
        groupRef.setValue(groupData)
            .addOnSuccessListener {
                // 跳转到群聊页面，需在 nav_graph.xml 配置 groupId 传递
                val bundle = android.os.Bundle().apply { putString("groupId", groupId) }
                findNavController().navigate(R.id.groupChatFragment, bundle)
            }
            .addOnFailureListener {
                android.widget.Toast.makeText(requireContext(), "Failed to create group", android.widget.Toast.LENGTH_SHORT).show()
            }
    }
}